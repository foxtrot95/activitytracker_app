package com.example.activitytracker;

public class Config {
    public static final int NUMBER_OF_WINDOWS = 10;
    public static final long MAX_RECORDING_TIME = 1000L * 20;
    public static final long MAX_RECORDING_TIME_CALIBRATION = 1000L * 10;
    public static final int DELAY = 3000;
    public static final int FREQUENCY = 50; // ms
    public static final int WINDOW_SIZE = 1000;
    public static final int WINDOW_SIZE_RAW = 2000;

    public static final String KNN_MODEL = "knn.tflite";
    public static final String NEURAL_NETWORK_FEATURE_MODEL = "nn.tflite";
    public static final String NEURAL_NETWORK_RAW_MODEL = "nn_raw_model.tflite";
    public static final String TRANSFER_LEARNING_MODEL = "tl_model.tflite";

    public static final String KNN_CLASSIFIER_NAME = "KNN Classifier";
    public static final String NN_FEATURE_CLASSIFIER_NAME = "Neural Network Classifier";
    public static final String NN_RAW_CLASSIFIER_NAME = "Raw Neural Network Classifier";
    public static final String TRANSFER_LEARNING_CLASSIFIER_NAME = "Transfer Model Classifier";

    public static final int MINIMUM_EQUAL_ELEMENTS = 5;
    public static final int EPOCHS = 30;
    public static final int BOTTLENECK_SIZE = 64;
    public static final int STRIDE = 100;

    public static final String WEIGHTS_FILE_NAME = "weights.json";
    public static final String DATASET_FILE_NAME = "dataset.json";

    public static final String LOSSES_FILE_NAME = "losses.txt";
    public static final String[] ACTIVITIES = new String[]
            {"Walking", "Jogging", "Jumping", "Squatting", "Sitting", "Standing"};

}
