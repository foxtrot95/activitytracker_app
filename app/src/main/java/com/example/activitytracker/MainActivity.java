package com.example.activitytracker;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    Button recordButton;
    Button trackButton;
    Button calibrateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // menu buttons
        recordButton = findViewById(R.id.button_menu_record);
        // track
        trackButton = findViewById(R.id.button_menu_track);
        // calibrate
        calibrateButton = findViewById(R.id.button_menu_calibrate);

        // on click listener
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getBaseContext(), RecordActivity.class);
                startActivity(intent);
            }
        });

        trackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getBaseContext(), TrackActivity.class);
                startActivity(intent);
            }
        });

        calibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getBaseContext(), CalibrateActivity.class);
                startActivity(intent);
            }
        });
    }
}