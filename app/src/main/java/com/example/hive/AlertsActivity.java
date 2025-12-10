package com.example.hive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AlertsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_alerts);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView tvAlertLog = findViewById(R.id.tvAlertLog);

        // Load only verified alerts
        SharedPreferences alertPrefs = getSharedPreferences("HiveAlerts", Context.MODE_PRIVATE);
        String alerts = alertPrefs.getString("alert_logs", "");

        if (alerts.isEmpty()) {
            tvAlertLog.setText("No active alerts in sector.");
        } else {
            tvAlertLog.setText(alerts);
        }
    }
}