package com.nibm.smartsleep;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.gson.Gson;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.PusherEvent;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "smartsleep.MainActivity";

    boolean mBound = false;

    private LineChart mChart;

    private Pusher pusher;

    private static final String PUSHER_APP_KEY = "87905df711b5e8a5256c";
    private static final String PUSHER_APP_CLUSTER = "ap2";
    private static final String CHANNEL_NAME = "heartbeat";
    private static final String EVENT_NAME = "new-heartbeat";

    private static final float GRAPH_MAX = 220.0f;
    private static final float HEART_ALERT = 170.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mChart = (LineChart) findViewById(R.id.chart);

        setupChart();
        setupAxes();
        setupData();
        setLegend();

        PusherOptions options = new PusherOptions();
        options.setCluster(PUSHER_APP_CLUSTER);
        Pusher pusher = new Pusher(PUSHER_APP_KEY, options);

        pusher.connect(new ConnectionEventListener() {
            @Override
            public void onConnectionStateChange(ConnectionStateChange change) {
                Log.v(TAG, "State changed from " + change.getPreviousState() +
                        " to " + change.getCurrentState());
            }

            @Override
            public void onError(String message, String code, Exception e) {
                Log.v(TAG, "There was a problem connecting! " +
                        "\ncode: " + code +
                        "\nmessage: " + message +
                        "\nException: " + e
                );
            }
        }, ConnectionState.ALL);

        Channel channel = pusher.subscribe(CHANNEL_NAME);

        channel.bind(EVENT_NAME, new SubscriptionEventListener() {
            @Override
            public void onEvent(PusherEvent event) {
                Log.v(TAG, "Received event with data: " + event.toString());
                Log.v(TAG, "data: " + event.getData());
                Gson gson = new Gson();
                Stat stat = gson.fromJson(event.getData(), Stat.class);
                Log.v(TAG, "stat: " + stat);

                addEntry(stat.getDataPoint());
            }
        });

        startService();
    }

    private void setupChart() {
        // disable description text
        mChart.getDescription().setEnabled(false);
        // enable touch gestures
        mChart.setTouchEnabled(true);
        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);
        // enable scaling
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);
        // set an alternative background color
        mChart.setBackgroundColor(Color.DKGRAY);
    }

    private void setupAxes() {
        XAxis xl = mChart.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(GRAPH_MAX);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        // Add a limit line
        LimitLine ll = new LimitLine(HEART_ALERT, "Heart Risk Limit");
        ll.setLineWidth(2f);
        ll.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        ll.setTextSize(10f);
        ll.setTextColor(Color.WHITE);
        // reset all limit lines to avoid overlapping lines
        leftAxis.removeAllLimitLines();
        leftAxis.addLimitLine(ll);
        // limit lines are drawn behind data (and not on top)
        leftAxis.setDrawLimitLinesBehindData(true);
    }

    private void setupData() {
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart.setData(data);
    }

    private void setLegend() {
        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.CIRCLE);
        l.setTextColor(Color.WHITE);
    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Heartbeat");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColors(ColorTemplate.VORDIPLOM_COLORS[0]);
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(10f);
        // To show values of each point
        set.setDrawValues(true);

        return set;
    }

    private void addEntry(DataPoint dataPoint) {
        LineData data = mChart.getData();

        if (data != null) {
            ILineDataSet set = data.getDataSetByIndex(0);

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), dataPoint.getHeartbeat()), 0);

            // let the chart know it's data has changed
            data.notifyDataChanged();
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(15);

            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // This registers mMessageReceiver to receive messages.
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("BLEDeviceConn"));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pusher.disconnect();
    }

    private void startService() {
        Log.i(TAG, "MainActivity.startService");
        Intent serviceIntent = new Intent(this, BluetoothService.class);
        bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void showToast(final String text) {
        Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
        toast.show();
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothService.BluetoothLocalBinder binder = (BluetoothService.BluetoothLocalBinder) service;
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            int deviceConnStatus = intent.getIntExtra("status",0);

            if (deviceConnStatus == 0) {
                showToast("Bluetooth disconnected");
            } else if (deviceConnStatus == 1) {
                showToast("Bluetooth connected");
            }
        }
    };
}
