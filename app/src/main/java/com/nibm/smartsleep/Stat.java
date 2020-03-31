package com.nibm.smartsleep;

public class Stat {
    private DataPoint dataPoint;

    @Override
    public String toString() {
        return "Stat{" +
                "dataPoint=" + dataPoint +
                '}';
    }

    public DataPoint getDataPoint() {
        return dataPoint;
    }

    public void setDataPoint(DataPoint dataPoint) {
        this.dataPoint = dataPoint;
    }
}

