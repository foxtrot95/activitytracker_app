package com.example.activitytracker;

public class Prediction {
    private int prediction;
    private double confidence;

    public Prediction(int prediction, double confidence) {
        this.prediction = prediction;
        this.confidence = confidence;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getPrediction() {
        return prediction;
    }

    public String getFormattedConfidence() {
        if (confidence == 0) {
            return "-"; 
        }
        return String.format("%.2f%%", confidence);
    }
}
