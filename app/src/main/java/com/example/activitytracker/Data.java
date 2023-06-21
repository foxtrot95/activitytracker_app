package com.example.activitytracker;

import java.util.ArrayList;

public class Data {
    private int activity;
    ArrayList<float[]> accelerationData;
    ArrayList<Long> timeStamps;

    public Data(int activity, ArrayList<float[]> accelerationData, ArrayList<Long> timeStamps) {
        this.activity = activity;
        this.accelerationData = accelerationData;
        this.timeStamps = timeStamps;
    }
}
