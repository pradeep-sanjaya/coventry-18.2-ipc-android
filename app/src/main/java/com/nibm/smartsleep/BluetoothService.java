package com.nibm.smartsleep;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class BluetoothService extends Service {

    public final static String EXTRA_DATA            = "com.nibm.smartsleep.EXTRA_DATA";

    public static String TAG = "smartsleep.BluetoothService";

    public Context context = this;
    public Handler handler = null;
    public static Runnable runnable = null;

    // ==== UUIDs for UAT service and associated characteristics.  ======
    public static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID TX_UUID = UUID.fromString("6E400002-B5A2-F393-E0A9-E50E24DCCA9E");
    public static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static String[] DEVICES = {
        "B4:E6:2D:85:8A:03",
        "A4:CF:12:9A:43:36"
    };

    public static String DEVICE_MAC = DEVICES[0];

    // BTLE state
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;
    private BluetoothDevice bluetoothDevice;

    private final IBinder mBinder = new BluetoothLocalBinder();

    public class BluetoothLocalBinder extends Binder {
        public BluetoothService getService() {
            Log.i(TAG, "BluetoothService.getService");
            // Return this instance of LocalService so clients can call public methods
            return BluetoothService.this;
        }
    }

    OkHttpClient client = new OkHttpClient();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        if (RX_UUID.equals(characteristic.getUuid())) {
            final String pin = characteristic.getStringValue(0);
            intent.putExtra(EXTRA_DATA, String.valueOf(pin));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data) {
                    stringBuilder.append(String.format("%02X ", byteChar));
                }
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }

        sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        context = getApplicationContext();
        Log.v(TAG, "=== BluetoothService.onCreate ===");

        adapter = BluetoothAdapter.getDefaultAdapter();
        adapter.startLeScan(scanCallback);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "=== BluetoothService.onDestroy ===");
    }

    @Override
    public void onStart(Intent intent, int startid) {
        Log.v(TAG, "===  BluetoothService.onStart ===");
    }

    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback()
    {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes)
        {
        Log.v(TAG, "=== BluetoothAdapter.onLeScan ===");

        String blDeviceName = bluetoothDevice.getName();
        String blDeviceAddr = bluetoothDevice.getAddress();

        Log.v(TAG, "device address: " + blDeviceAddr);
        Log.v(TAG, "device name: " + blDeviceName);
        Log.v(TAG, "bytes: " + parseUUIDs(bytes).toString());

        if (Arrays.asList(DEVICES).contains(blDeviceAddr)) {
            Log.d(TAG, "can connect to device");
            adapter.stopLeScan(scanCallback);
            Log.d(TAG, "found UART service");

            gatt = bluetoothDevice.connectGatt(context, false, callback);
            gatt.connect();
        }
        }
    };

    private BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.v(TAG, "== BluetoothGattCallback.onConnectionStateChange ==");

            //super.onConnectionStateChange(gatt, status, newState);
            Log.v(TAG, "newState + " + newState);

            if (newState == BluetoothGatt.STATE_CONNECTED) {

                notifyStatusToActivity("BLE_CONNECTED");
                gatt.discoverServices();

//                boolean val = gatt.requestMtu(512);
//                Log.v(TAG, "Request MTU : " + val);

                Log.v(TAG, "connected!");

                if (!gatt.discoverServices()) {
                    Log.e(TAG, "failed to start discovering services!");
                }

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {

                notifyStatusToActivity("BLE_DISCONNECTED");
                Log.v(TAG, "disconnected!");
                adapter = BluetoothAdapter.getDefaultAdapter();
                adapter.startLeScan(scanCallback);

            } else {
                Log.d(TAG, "=== connection state changed. new state: " + newState);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {

            super.onMtuChanged(gatt, mtu, status);
            Log.v(TAG, "===  BluetoothGattCallback.onMtuChanged ===");

//            notifyStatusToActivity("BLE_CONNECTED");
//            gatt.discoverServices();

            Log.v(TAG, "New MTU: " + mtu);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.v(TAG, "=== BluetoothGattCallback.onServicesDiscovered ===");

            if (status == BluetoothGatt.GATT_SUCCESS) {

                BluetoothGattService service = gatt.getService(UART_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(RX_UUID);
                    characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    if (characteristic != null) {

                        gatt.setCharacteristicNotification(characteristic, true);

                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);

                        gatt.readCharacteristic(characteristic);
                    }
                }

                Log.d(TAG, "service discovery completed");
            } else {
                Log.e(TAG, "service discovery failed with status: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic,int status) {
            Log.v(TAG, "===  BluetoothGattCallback.onCharacteristicRead ===");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(TAG, "BluetoothGatt.GATT_SUCCESS");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.v(TAG, "=== BluetoothGattCallback.onCharacteristicChanged ===");

            String received = characteristic.getStringValue(0);
            received = received.trim();
            Log.v(TAG, "received: " + received);

            Integer heartvalue = Integer.parseInt(received);
            Log.i(TAG, "heartvalue : " + heartvalue);

            if (heartvalue == 0) {
                Random rand = new Random();
                Log.i(TAG, "heartvalue is zero, generating random heart value : " + heartvalue);
                heartvalue = rand.nextInt(80);
            }

            if (heartvalue > 200) {
                Log.i(TAG, "heart value is more than 200, assigning maximum value");
                heartvalue = 200;
            }

            if (heartvalue > 0) {

                String deviceName = gatt.getDevice().getName();
                if (deviceName== null || deviceName.equals("")) {
                    deviceName = "smartPillow";
                }

                String url = "https://ipc-smartsleep.herokuapp.com/add-heartbeat/" + deviceName.toLowerCase() + "/" + heartvalue;
                Log.i(TAG, "calling " + url);

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {
                        // Do something when request failed
                        e.printStackTrace();
                        Log.d(TAG, "Request Failed.");
                    }

                    @Override
                    public void onResponse(Response response) throws IOException {
                        if(!response.isSuccessful()){
                            throw new IOException("Error : " + response);
                        }else {
                            Log.d(TAG,"Request Successful.");
                        }

                        // Read data in the worker thread
                        final String data = response.body().string();
                    }
                });
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.v(TAG, "===  BluetoothGattCallback.onCharacteristicWrite ===");
        }

    };

    // Notify MainActivity
    public void notifyStatusToActivity(String status) {

        Intent intent = new Intent("BLEDeviceConn");

        if (status == "BLE_DISCONNECTED") {
            intent.putExtra("status", 0);
        } else if (status == "BLE_CONNECTED") {
            intent.putExtra("status", 1);
            intent.putExtra("addr", gatt.getDevice().getAddress());
            intent.putExtra("name", gatt.getDevice().getName());
        }

        LocalBroadcastManager.getInstance(BluetoothService.this).sendBroadcast(intent);
        Log.i(TAG, "Status notified to MainActivity. Status : " + status);
    }

    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

            int type = advertisedData[offset++];
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++];
                        uuid16 += (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit, mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            //Log.e(LOG_TAG, e.toString());
                            continue;
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }

        return uuids;
    }

}
