package com.example.activitytracker;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;

import java.io.File;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.tensorflow.lite.Interpreter;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;


public class TrackActivity extends AppCompatActivity implements SensorEventListener, AdapterView.OnItemSelectedListener {

    private Button buttonTrack;

    private Interpreter modelInterpreter;

    // sensor
    private Sensor sensorAcceleration;
    private SensorManager sensorManager;

    private long lastTimestamp;

    private ArrayList<Float> accelerationDataX;
    private ArrayList<Float> accelerationDataY;
    private ArrayList<Float> accelerationDataZ;

    private ArrayList<String> predictions;

    private CustomAdapter customAdapter;

    private ArrayList<Long> timeStamps;

    // List items
    private ArrayList<String> descriptionList;

    private ArrayList<String> dateTimeList;
    private ArrayList<Integer> iconList;

    private ArrayList<String> confidenceList;

    private final int[] icons = {R.drawable.walking, R.drawable.jogging, R.drawable.jumping,
            R.drawable.squatting, R.drawable.sitting, R.drawable.standing};

    private Map<Integer, String> model_types;
    private String currentModelType;

    private boolean isTracking;

    MappedByteBuffer knnModel;
    MappedByteBuffer nnModel;
    MappedByteBuffer nnRawModel;
    MappedByteBuffer transferModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track);
        modelInterpreter = null;
        try {
            knnModel = loadModelFile(Config.KNN_MODEL);
            nnModel = loadModelFile(Config.NEURAL_NETWORK_FEATURE_MODEL);
            nnRawModel = loadModelFile(Config.NEURAL_NETWORK_RAW_MODEL);
            transferModel = loadModelFile(Config.TRANSFER_LEARNING_MODEL);
        } catch (Exception e){
            e.printStackTrace();
        }

        model_types = new HashMap<>();
        model_types.put(0, Config.KNN_CLASSIFIER_NAME);
        model_types.put(1, Config.NN_FEATURE_CLASSIFIER_NAME);
        model_types.put(2, Config.NN_RAW_CLASSIFIER_NAME);
        model_types.put(3, Config.TRANSFER_LEARNING_CLASSIFIER_NAME);

        currentModelType = null;
        isTracking = false;

        // init
        buttonTrack = findViewById(R.id.button_track);
        ListView listPredictions = findViewById(R.id.list_predictions);
        Button buttonClearList = findViewById(R.id.button_clear_list);

        // sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        lastTimestamp = System.currentTimeMillis();

        // spinner
        Spinner dropdown = findViewById(R.id.spinner_model);
        dropdown.setOnItemSelectedListener(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>((Context) this,
                androidx.constraintlayout.widget.R.layout.support_simple_spinner_dropdown_item,
                new ArrayList<String>(model_types.values()));
        dropdown.setAdapter(adapter);

        // data
        accelerationDataX = new ArrayList<>();
        accelerationDataY = new ArrayList<>();
        accelerationDataZ = new ArrayList<>();

        predictions = new ArrayList<>();
        timeStamps = new ArrayList<>();
        descriptionList = new ArrayList<>();
        dateTimeList = new ArrayList<>();
        iconList = new ArrayList<>();
        confidenceList = new ArrayList<>();

        // list view
        customAdapter = new CustomAdapter(getApplicationContext());
        listPredictions.setAdapter(customAdapter);

        buttonTrack.setText(R.string.START);
        buttonTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {tapTrack();};
        });

        buttonClearList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {clearList();};
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
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        if(isTracking) {
            float[] currentValues = sensorEvent.values;

            accelerationDataX.add(currentValues[0]);
            accelerationDataY.add(currentValues[1]);
            accelerationDataZ.add(currentValues[2]);

            long currentTimestamp = System.currentTimeMillis();
            timeStamps.add(currentTimestamp);
            if(currentTimestamp -lastTimestamp >= 200
                    && currentTimestamp -timeStamps.get(0) > 2000) {
                Log.v("time ready", "FEATURE");
                lastTimestamp = currentTimestamp;
                predictActivity();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // do nothing
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if(Objects.equals(model_types.get(i), Config.KNN_CLASSIFIER_NAME)) {
            modelInterpreter = new Interpreter(knnModel);
            currentModelType = model_types.get(i);
        } else if(Objects.equals(model_types.get(i), Config.NEURAL_NETWORK_FEATURE_MODEL)){
            modelInterpreter = new Interpreter(nnModel);
            currentModelType = model_types.get(i);
        } else if(Objects.equals(model_types.get(i), Config.TRANSFER_LEARNING_CLASSIFIER_NAME)) {
            modelInterpreter = new Interpreter(transferModel);
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File weightsFile = new File(path, "weights.json");
            if(weightsFile.exists()) {
                String[] weightsPath = new String[]{weightsFile.getAbsolutePath()};
                Map<String, Object> inputs = new HashMap<>();
                inputs.put("checkpoint_path", weightsPath);
                Map<String, Object> outputs = new HashMap<>();
                modelInterpreter.runSignature(inputs, outputs, "restore");
            }
            currentModelType = model_types.get(i);
        } else if(Objects.equals(model_types.get(i), Config.NN_RAW_CLASSIFIER_NAME)){
            modelInterpreter = new Interpreter(nnRawModel);
            currentModelType = model_types.get(i);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        // do nothing
    }

    public float[][] transpose(float[][] array) {
        // empty or unset array, nothing do to here
        if (array == null || array.length == 0)
            return array;

        int width = array.length;
        int height = array[0].length;

        float[][] transposed_array = new float[height][width];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                transposed_array[y][x] = array[x][y];
            }
        }
        return transposed_array;
    }

    private float[][][] prepareRawInput() {
        long lastTimestamp = timeStamps.get(timeStamps.size()-1);
        int index = 0;
        for(int i = timeStamps.size()-1; i >= 0; i--) {
            long diff = lastTimestamp - timeStamps.get(i);
            if(diff > Config.WINDOW_SIZE_RAW) {
                index = i;
                break;
            }
        }

        ArrayList<Float> accelerationDataXTemp =
                new ArrayList<>(accelerationDataX.subList(index, accelerationDataX.size() - 1));
        Collections.copy(accelerationDataXTemp,
                accelerationDataX.subList(index, accelerationDataX.size() - 1));
        ArrayList<Float> accelerationDataYTemp =
                new ArrayList<>(accelerationDataY.subList(index, accelerationDataY.size() - 1));
        Collections.copy(accelerationDataYTemp,
                accelerationDataY.subList(index, accelerationDataY.size() - 1));
        ArrayList<Float> accelerationDataZTemp =
                new ArrayList<>(accelerationDataZ.subList(index, accelerationDataZ.size() - 1));
        Collections.copy(accelerationDataZTemp,
                accelerationDataZ.subList(index, accelerationDataZ.size() - 1));

        float[] accX = Helper.createFloatArray(accelerationDataXTemp);
        float[] accY = Helper.createFloatArray(accelerationDataYTemp);
        float[] accZ = Helper.createFloatArray(accelerationDataZTemp);

        accX = Arrays.copyOfRange(accX, 0, 760);
        accY = Arrays.copyOfRange(accY, 0, 760);
        accZ = Arrays.copyOfRange(accZ, 0, 760);

        float[][] accelerationDataTemp = {accX, accY, accZ};
        accelerationDataTemp = transpose(accelerationDataTemp);

        float[][][] accelerationData = {accelerationDataTemp};

        return accelerationData;
    }

    private void predictActivity() {
        Prediction prediction = null;
        long lastTimestamp = timeStamps.get(timeStamps.size()-1);
        int index = 0;

        for(int i = timeStamps.size()-1; i >= 0; i--) {
            long diff = lastTimestamp - timeStamps.get(i);
            if(diff > Config.WINDOW_SIZE) {
                index = i;
                break;
            }
        }

        ArrayList<Float> accelerationDataXTemp = new ArrayList<>
                (accelerationDataX.subList(index, accelerationDataX.size() - 1));
        ArrayList<Float> accelerationDataYTemp = new ArrayList<>
                (accelerationDataY.subList(index, accelerationDataY.size() - 1));
        ArrayList<Float> accelerationDataZTemp = new ArrayList<>
                (accelerationDataZ.subList(index, accelerationDataZ.size() - 1));

        if(Objects.equals(currentModelType, Config.KNN_CLASSIFIER_NAME)) {
            float[] feature = Helper.computeFeatures(accelerationDataXTemp, accelerationDataYTemp,
                        accelerationDataZTemp);
            prediction = Inference.doKNNInference(feature, modelInterpreter);
        } else if(Objects.equals(currentModelType, Config.NN_FEATURE_CLASSIFIER_NAME)) {
            float[] feature = Helper.computeFeatures(accelerationDataXTemp, accelerationDataYTemp,
                    accelerationDataZTemp);
            prediction = Inference.doFeatureNNInference(feature, modelInterpreter);
        } else if(Objects.equals(currentModelType, Config.TRANSFER_LEARNING_CLASSIFIER_NAME)) {
            float[] feature = Helper.computeFeatures(accelerationDataXTemp, accelerationDataYTemp,
                    accelerationDataZTemp);
            prediction = Inference.doTLInference(feature, modelInterpreter);
        } else if(Objects.equals(currentModelType, Config.NN_RAW_CLASSIFIER_NAME)){
            float[][][] accelerationData = prepareRawInput();
            prediction = Inference.doRawNNInference(accelerationData, modelInterpreter);
        }

        // update
        // add only to list if changed
        predictions.add(Config.ACTIVITIES[prediction.getPrediction()]);
        if(predictions.size() > 5) {
            String lastItem = descriptionList.get(0);

            if(!Objects.equals(Config.ACTIVITIES[prediction.getPrediction()], lastItem)
                    && Helper.areElementsEqual(predictions, Config.MINIMUM_EQUAL_ELEMENTS)){

                descriptionList.add(0, Config.ACTIVITIES[prediction.getPrediction()]);
                dateTimeList.add(0, getDateTime());
                iconList.add(0, icons[prediction.getPrediction()]);
                confidenceList.add(0, prediction.getFormattedConfidence());

                customAdapter.updateItems(descriptionList, iconList, dateTimeList, confidenceList);
            }
        } else {
            if(descriptionList.isEmpty()) {
                descriptionList.add(0, Config.ACTIVITIES[prediction.getPrediction()]);
                dateTimeList.add(0, getDateTime());
                iconList.add(0, icons[prediction.getPrediction()]);
                confidenceList.add(0, prediction.getFormattedConfidence());

                customAdapter.updateItems(descriptionList, iconList, dateTimeList, confidenceList);
            }
        }
    }

    private String getDateTime() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());

        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
        return simpleDateFormat.format(timestamp);
    }

    private void tapTrack() {
        isTracking = !isTracking;

        if(isTracking) {
            lastTimestamp = System.currentTimeMillis();
            buttonTrack.setText(R.string.STOP);
        } else {
            buttonTrack.setText(R.string.START);
        }
    }

    private void clearList() {
        descriptionList.clear();
        dateTimeList.clear();
        iconList.clear();
        confidenceList.clear();
        predictions.clear();
        customAdapter.updateItems(descriptionList, iconList, dateTimeList, confidenceList);
    }

    private MappedByteBuffer loadModelFile(String fileName) throws Exception {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(fileName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declareLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declareLength);
    }
}

