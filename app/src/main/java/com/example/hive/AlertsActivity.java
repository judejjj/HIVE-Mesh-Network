package com.example.hive; // CHANGE THIS

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class AlertsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_alerts);
    }
}