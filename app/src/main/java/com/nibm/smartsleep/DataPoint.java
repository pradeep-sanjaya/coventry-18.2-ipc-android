package com.nibm.smartsleep;

public class DataPoint {
    private int heartbeat;
    private long time;

    public int getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(int heartbeat) {
        this.heartbeat = heartbeat;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @Override
    public String toString() {
        return "DataPoint{" +
                "heartbeat=" + heartbeat +
                ", time=" + time +
                '}';
    }
}