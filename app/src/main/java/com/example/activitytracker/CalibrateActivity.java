package com.example.activitytracker;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.json.JSONException;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class CalibrateActivity extends AppCompatActivity implements SensorEventListener, AdapterView.OnItemSelectedListener {

    private Button startButton;
    private Button preprocessButton;
    private Button trainButton;
    private Button testButton;
    private TextView statusText;
    private TextView timerText;
    private Spinner spinnerActivities;

    // recordings
    private ArrayList<String> listItems = new ArrayList<>();
    ArrayAdapter<String> listAdapter;

    private ArrayList<Float> accelerationDataX;
    private ArrayList<Float> accelerationDataY;
    private ArrayList<Float> accelerationDataZ;

    private ArrayList<ArrayList<Float>> windowDataX;
    private ArrayList<ArrayList<Float>> windowDataY;
    private ArrayList<ArrayList<Float>> windowDataZ;

    private ArrayList<ArrayList<ArrayList<Float>>> activityDataX;
    private ArrayList<ArrayList<ArrayList<Float>>> activityDataY;
    private ArrayList<ArrayList<ArrayList<Float>>> activityDataZ;

    // features
    private ArrayList<float[]> features;
    private ArrayList<float[]> labels;

    private long windowTimestamp;

    // sensor
    private Sensor sensorAcceleration;
    private SensorManager sensorManager;

    // timer
    private Timer timer;
    private TimerTask timerTask;
    private Boolean startTracking;
    private long lastTimestamp;
    private long currentTimestamp;

    private final String[] models = new String[]
            {Config.NN_FEATURE_CLASSIFIER_NAME, Config.TRANSFER_LEARNING_CLASSIFIER_NAME};
    private int selectedActivity;
    private int selectedModel;

    MappedByteBuffer transferModel;
    MappedByteBuffer nnModel;

    private Interpreter trainInterpreter;
    private Interpreter testInterpreter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate);

        try {
            transferModel = loadModelFile(Config.TRANSFER_LEARNING_MODEL);
            nnModel = loadModelFile(Config.NEURAL_NETWORK_FEATURE_MODEL);
            trainInterpreter = new Interpreter(transferModel);
        } catch (Exception e) {
            e.printStackTrace();
        }

        startButton = findViewById(R.id.button_calibrate_start);
        preprocessButton = findViewById(R.id.button_calibrate_preprocess);
        trainButton = findViewById(R.id.button_calibrate_train);
        testButton = findViewById(R.id.button_calibrate_test);
        statusText = findViewById(R.id.text_calibrate_status);
        timerText = findViewById(R.id.text_calibrate_timer);
        ListView listRecordings = findViewById(R.id.list_calibrate_recordings);

        preprocessButton.setEnabled(false);
        trainButton.setEnabled(false);

        startTracking = false;
        timer = new Timer();

        selectedActivity = 0;
        selectedModel = 0;

        // sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // spinner
        spinnerActivities = findViewById(R.id.spinner_calibrate_activities);
        spinnerActivities.setOnItemSelectedListener(this);
        ArrayAdapter<String> adapterActivities = new ArrayAdapter<>(this,
                androidx.constraintlayout.widget.R.layout.support_simple_spinner_dropdown_item,
                Config.ACTIVITIES);
        spinnerActivities.setAdapter(adapterActivities);

        Spinner spinnerModel = findViewById(R.id.spinner_calibrate_model);
        spinnerModel.setOnItemSelectedListener(this);
        ArrayAdapter<String> adapterModel = new ArrayAdapter<>(this,
                androidx.constraintlayout.widget.R.layout.support_simple_spinner_dropdown_item,
                models);
        spinnerModel.setAdapter(adapterModel);

        // data
        accelerationDataX = new ArrayList<>();
        accelerationDataY = new ArrayList<>();
        accelerationDataZ = new ArrayList<>();

        windowDataX = new ArrayList<>();
        windowDataY = new ArrayList<>();
        windowDataZ = new ArrayList<>();

        activityDataX = new ArrayList<>();
        activityDataY = new ArrayList<>();
        activityDataZ = new ArrayList<>();

        features = new ArrayList<>();
        labels = new ArrayList<>();

        // list view
        listAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, listItems);
        listRecordings.setAdapter(listAdapter);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startTapped();
            }
        });

        preprocessButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { preprocess(); }
        });

        trainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { train(); }
        });

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { test(); }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(sensorAcceleration != null) {
            sensorManager.registerListener(this, sensorAcceleration,
                    SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(startTracking) {
            float[] currentValues = sensorEvent.values;

            accelerationDataX.add(currentValues[0]);
            accelerationDataY.add(currentValues[1]);
            accelerationDataZ.add(currentValues[2]);

            currentTimestamp = System.currentTimeMillis();

            if(currentTimestamp-windowTimestamp >= Config.STRIDE) {

                ArrayList<Float> tempX = new ArrayList<>(accelerationDataX);
                Collections.copy(tempX, accelerationDataX);
                ArrayList<Float> tempY = new ArrayList<>(accelerationDataY);
                Collections.copy(tempY, accelerationDataY);
                ArrayList<Float> tempZ = new ArrayList<>(accelerationDataZ);
                Collections.copy(tempZ, accelerationDataZ);

                windowDataX.add(tempX);
                windowDataY.add(tempY);
                windowDataZ.add(tempZ);

                accelerationDataX.clear();
                accelerationDataY.clear();
                accelerationDataZ.clear();

                windowTimestamp = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // do nothing
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if(adapterView.getId() == R.id.spinner_calibrate_activities) {
            selectedActivity = i;
        } else {
            selectedModel = i;
            if(Objects.equals(models[i], Config.NN_FEATURE_CLASSIFIER_NAME)) {
                testInterpreter = new Interpreter(nnModel);
            } else {
                testInterpreter = new Interpreter(transferModel);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        // do nothing
    }

    public void preprocess() {
        preprocessButton.setEnabled(false);
        preprocessData();
        activityDataX.clear();
        activityDataY.clear();
        activityDataZ.clear();
        listItems.clear();
        listAdapter.notifyDataSetChanged();
        trainButton.setEnabled(true);
        showMessage("Preprocessed data");
        statusText.setText(R.string.ready_to_train);
    }

    public void train() {
        trainButton.setEnabled(false);
        try {
            trainModel();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        features.clear();
        labels.clear();
        showMessage("Trained model");
        statusText.setText(R.string.ready_to_test);
    }

    public void test() {
        testButton.setEnabled(false);
        statusText.setText(R.string.testing);
        try {
            testModel();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        showMessage("Tested model");
        testButton.setEnabled(true);
    }

    public void preprocessData() {
        for(int activity_i = 0; activity_i < activityDataX.size(); activity_i++) {
            int end_window_i = activityDataX.get(activity_i).size() - Config.NUMBER_OF_WINDOWS;
            for(int start_window_i = 0; start_window_i < end_window_i; start_window_i++) {

                ArrayList<Float> accX = new ArrayList<>();
                ArrayList<Float> accY = new ArrayList<>();
                ArrayList<Float> accZ = new ArrayList<>();

                int end_window_j = start_window_i + Config.NUMBER_OF_WINDOWS;
                for(int window_j = start_window_i; window_j < end_window_j; window_j++){
                    accX.addAll(activityDataX.get(activity_i).get(window_j));
                    accY.addAll(activityDataY.get(activity_i).get(window_j));
                    accZ.addAll(activityDataZ.get(activity_i).get(window_j));
                }

                float[] feature = Helper.computeFeatures(accX, accY, accZ);
                features.add(feature);
                Integer label = Arrays.asList(Config.ACTIVITIES).indexOf(listItems.get(activity_i));
                labels.add(Helper.getCategoricalLabel(label));
            }
        }
        Log.v("LIST", features.size() + "");
        Log.v("LIST", Arrays.toString(features.get(1)) + "");
        Log.v("LIST", Arrays.toString(labels.get(1)) + "");
    }

    public void startTapped() {
        startButton.setEnabled(false);
        spinnerActivities.setEnabled(false);
        testButton.setEnabled(false);
        startTimer();
    }

    public void stopTimer() {
        timerTask.cancel();
        startTracking = false;
        startButton.setEnabled(true);
        testButton.setEnabled(true);

        spinnerActivities.setEnabled(true);
        timerText.setText(formatTime(0, 0));
        statusText.setText(R.string.ready_to_preprocess);
        listItems.add(Config.ACTIVITIES[selectedActivity]);
        listAdapter.notifyDataSetChanged();

        if(listItems.size() == 1)
            preprocessButton.setEnabled(true);

        ArrayList<ArrayList<Float>> tempX = new ArrayList<>(windowDataX);
        Collections.copy(tempX, windowDataX);
        ArrayList<ArrayList<Float>> tempY = new ArrayList<>(windowDataY);
        Collections.copy(tempY, windowDataY);
        ArrayList<ArrayList<Float>> tempZ = new ArrayList<>(windowDataZ);
        Collections.copy(tempZ, windowDataZ);

        activityDataX.add(tempX);
        activityDataY.add(tempY);
        activityDataZ.add(tempZ);

        windowDataX.clear();
        windowDataY.clear();
        windowDataZ.clear();

        accelerationDataX.clear();
        accelerationDataY.clear();
        accelerationDataZ.clear();
    }

    private void startTimer() {
        statusText.setText(R.string.waiting);

        timerTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(!startTracking) {
                            playAlertSound();
                            lastTimestamp = System.currentTimeMillis();
                            startTracking = true;
                            windowTimestamp = System.currentTimeMillis();
                        }

                        currentTimestamp = System.currentTimeMillis();

                        long timeDifference = currentTimestamp-lastTimestamp;
                        timerText.setText(getTimerText(timeDifference));
                        statusText.setText(R.string.recording);

                        if(timeDifference >= (Config.MAX_RECORDING_TIME_CALIBRATION)) {
                            playAlertSound();
                            stopTimer();
                        }
                    }
                });
            }
        };
        timer.scheduleAtFixedRate(timerTask, Config.DELAY, Config.FREQUENCY);
    }

    public void playAlertSound() {
        ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500);
    }

    private String getTimerText(Long time) {
        int seconds = (int) TimeUnit.MILLISECONDS.toSeconds(time) % 60;
        int minutes = (int) TimeUnit.MILLISECONDS.toMinutes(time);
        return formatTime(seconds, minutes);
    }

    @SuppressLint("DefaultLocale")
    private String formatTime(int seconds, int minutes) {
        return String.format("%02d", minutes) + ":" + String.format("%02d", seconds);
    }

    private void trainModel() throws IOException, JSONException {

        // shuffle index list
        ArrayList<Integer> shuffledIndexList = Helper.getShuffledIndexList(features.size());

        // train
        ArrayList<Float> losses = new ArrayList<>();
        for(int epoch = 0; epoch < Config.EPOCHS; epoch++) {
            for(int i = 0; i < shuffledIndexList.size(); i++) {
                Map<String, Object> inputs = new HashMap<>();
                inputs.put("features", features.get(shuffledIndexList.get(i)));

                float[][] bottleneck = new float[1][Config.BOTTLENECK_SIZE];

                Map<String, Object> bottleneck_output = new HashMap<>();
                bottleneck_output.put("bottleneck", bottleneck);

                trainInterpreter.runSignature(inputs, bottleneck_output, "load");

                Map<String, Object> transfer_model_inputs = new HashMap<>();
                transfer_model_inputs.put("bottleneck", bottleneck_output.get("bottleneck"));
                transfer_model_inputs.put("label", labels.get(shuffledIndexList.get(i)));

                float[] loss = new float[1];

                Map<String, Object> output = new HashMap<>();
                output.put("loss", loss);

                trainInterpreter.runSignature(transfer_model_inputs, output, "train");
                float[] loss_output = (float[]) output.get("loss");
                if(i == features.size()-1) {
                    losses.add(loss_output[0]);
                }
            }
        }

        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);

        Map<String, Object> save_input = new HashMap<>();
        save_input.put("checkpoint_path", path.getAbsolutePath()
                + "/" + Config.WEIGHTS_FILE_NAME);

        String[] output = new String[1];
        Map<String, Object> save_output = new HashMap<>();
        save_output.put("checkpoint_path", output);

        trainInterpreter.runSignature(save_input, save_output, "save");

        String filePath = path.getAbsolutePath() + "/" + Config.LOSSES_FILE_NAME;
        Helper.writeFile(losses, filePath);
    }

    @SuppressLint("SetTextI18n")
    private void testModel() throws JSONException {

        // read file
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File file = new File(path, Config.DATASET_FILE_NAME);
        StringBuilder text = Helper.readFile(file);
        ArrayList<float[]> features = Helper.getFeaturesFromJSON(text.toString());

        // predictions
        ArrayList<Integer> predictions = new ArrayList<>();

        for(float[] feature: features) {
            Prediction prediction;
            if(Objects.equals(models[selectedModel], Config.NN_FEATURE_CLASSIFIER_NAME)) {
                prediction = Inference.doFeatureNNInference(feature, testInterpreter);
                predictions.add(prediction.getPrediction());
            } else {
                File weightsFile = new File(path, Config.WEIGHTS_FILE_NAME);
                if(weightsFile.exists()) {
                    // load file
                    String[] weightsPath = new String[]{weightsFile.getAbsolutePath()};
                    Map<String, Object> inputs = new HashMap<>();
                    inputs.put("checkpoint_path", weightsPath);
                    Map<String, Object> outputs = new HashMap<>();
                    testInterpreter.runSignature(inputs, outputs, "restore");
                }
                prediction = Inference.doTLInference(feature, testInterpreter);
                predictions.add(prediction.getPrediction());
            }
        }
        ArrayList<Integer> labels = Helper.getLabels(text.toString());
        float accuracy = calcAccuracy(predictions, labels);
        statusText.setText(getResources().getString(R.string.test_result, accuracy));
    }

    private MappedByteBuffer loadModelFile(String fileName) throws Exception {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(fileName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declareLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declareLength);
    }

    private float calcAccuracy(ArrayList<Integer> predictions, ArrayList<Integer> labels) {
        float sum = 0;
        for(int i = 0; i < predictions.size(); i++) {
            if(predictions.get(i) == labels.get(i))
                sum++;
        }
        return sum / predictions.size();
    }

    private void showMessage(String message) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast errorToast = Toast.makeText(getApplicationContext(),
                        message, Toast.LENGTH_SHORT);
                errorToast.show();
            }
        });
    }
}