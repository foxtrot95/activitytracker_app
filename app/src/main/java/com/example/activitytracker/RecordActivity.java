package com.example.activitytracker;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.TimeUnit;


public class RecordActivity extends AppCompatActivity implements SensorEventListener, AdapterView.OnItemSelectedListener {

    // sensor
    private TextView xAccText;
    private TextView yAccText;
    private TextView zAccText;

    // tracking
    private Button saveButton;
    private TextView fileName;
    private TextView timerText;
    private Button stopStartButton;
    private Button resetButton;
    private TextView statusText;

    // timer
    private Timer timer;
    private TimerTask timerTask;
    private boolean timerStarted;
    private long lastTimestamp;
    private long currentTimestamp;
    private long timeDifference;
    private long currentTimeDifference;
    private long totalTime;

    // sensor
    private Sensor sensorAcceleration;
    private SensorManager sensorManager;

    // tracking
    private boolean startTracking;
    private ArrayList<float[]> accelerationData;
    private ArrayList<Long> timeStamps;

    private int selectedActivity;

    private PowerManager.WakeLock mWakeLock;

    @SuppressLint("InvalidWakeLockTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);

        // init
        timerText = findViewById(R.id.text_timer);
        stopStartButton = findViewById(R.id.button_start_stop);
        resetButton = findViewById(R.id.button_reset);
        statusText = findViewById(R.id.text_status);
        saveButton = findViewById(R.id.button_save);
        fileName = findViewById(R.id.text_filename);

        xAccText = findViewById(R.id.text_acc_x);
        yAccText = findViewById(R.id.text_acc_y);
        zAccText = findViewById(R.id.text_acc_z);

        saveButton.setEnabled(false);

        // timer
        timer = new Timer();
        timeDifference = 0;
        totalTime = 0;
        startTracking = false;
        timerStarted = false;

        // data
        accelerationData = new ArrayList<>();
        timeStamps = new ArrayList<>();
        selectedActivity = 0;

        // lock
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LOCK");

        // sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // spinner
        Spinner dropdown = findViewById(R.id.spinner_activities);
        dropdown.setOnItemSelectedListener(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                androidx.constraintlayout.widget.R.layout.support_simple_spinner_dropdown_item,
                Config.ACTIVITIES);
        dropdown.setAdapter(adapter);

        // check sensor
        if(sensorAcceleration == null) {
            xAccText.setText(R.string.error);
            yAccText.setText(R.string.error);
            zAccText.setText(R.string.error);
        }

        // on click listener
        stopStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startStopTapped();
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetTapped();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveData();
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();

        if(sensorAcceleration != null) {
            sensorManager.registerListener(this, sensorAcceleration,
                    SensorManager.SENSOR_DELAY_FASTEST); // every 5-20 ms
        }
        mWakeLock.acquire(10*60*1000L /*10 minutes*/);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mWakeLock.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float[] currentValues = sensorEvent.values;
        
        xAccText.setText(String.format("X: %.3f", currentValues[0]));
        yAccText.setText(String.format("Y: %.3f", currentValues[1]));
        zAccText.setText(String.format("Z: %.3f", currentValues[2]));

        if(startTracking) {
            accelerationData.add(currentValues.clone());
            timeStamps.add(System.currentTimeMillis());
            Log.d("TRACK", String.valueOf(accelerationData.size()));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // do nothing
    }

    public void resetTapped() {
        if (timerTask != null) {
            timerTask.cancel();

            stopStartButton.setText(R.string.START);
            statusText.setText(R.string.not_tracking);
            timerText.setText(formatTime(0,0));

            timeDifference = 0;
            totalTime = 0;

            timerStarted = false;
            startTracking = false;

            accelerationData.clear();
            timeStamps.clear();

            saveButton.setEnabled(false);
        }
    }

    public void startStopTapped() {
        if(!timerStarted) {
            startTimer();
            timerStarted = true;

            stopStartButton.setText(R.string.STOP);
            saveButton.setEnabled(false);
        } else {
            timerTask.cancel();
            timerStarted = false;
            startTracking = false;
            timeDifference += currentTimeDifference;

            stopStartButton.setText(R.string.RESUME);
            statusText.setText(R.string.pausing);
            saveButton.setEnabled(true);
        }
    }

    private void startTimer() {
        statusText.setText(R.string.starting_soon);
        timerTask = new TimerTask() {
            @Override
            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(!startTracking) {
                            playAlertSound();
                            lastTimestamp = System.currentTimeMillis();
                        }

                        startTracking = true;
                        currentTimestamp = System.currentTimeMillis();
                        currentTimeDifference = currentTimestamp-lastTimestamp;
                        totalTime = timeDifference + currentTimeDifference;

                        timerText.setText(getTimerText());
                        statusText.setText(R.string.tracking);

                        if(currentTimeDifference >= Config.MAX_RECORDING_TIME) {
                            playAlertSound();
                            startStopTapped();
                        }
                    }
                });
            }
        };
        timer.scheduleAtFixedRate(timerTask, Config.DELAY, Config.FREQUENCY);
    }

    private String getTimerText() {
        int seconds = (int) TimeUnit.MILLISECONDS.toSeconds(totalTime) % 60;
        int minutes = (int) TimeUnit.MILLISECONDS.toMinutes(totalTime);
        return formatTime(seconds, minutes);
    }

    @SuppressLint("DefaultLocale")
    private String formatTime(int seconds, int minutes) {
        return String.format("%02d", minutes) + ":" + String.format("%02d", seconds);
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        selectedActivity = i;
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        // do nothing
    }

    private void saveData() {
        boolean isException = false;

        saveButton.setEnabled(false);
        try {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            Helper.writeJsonFile(path, fileName.getText().toString(),
                    selectedActivity, accelerationData, timeStamps);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            showMessage("File already exists!");

        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error");
            isException = true;
        }

        saveButton.setEnabled(true);

        if(!isException)
            showMessage("Saved!");
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

    public void playAlertSound() {
        ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500);
    }
}