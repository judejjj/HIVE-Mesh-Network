package com.example.hive; // <--- CHECK YOUR PACKAGE NAME

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity {

    private Button btnSOS;
    private GridLayout gridModules;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide Action Bar for that cinematic look
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_dashboard);

        // 1. Initialize Views
        btnSOS = findViewById(R.id.btnSOS);
        gridModules = findViewById(R.id.gridModules);

        // 2. The SOS Button Logic (The "Big Red Button")
        setupSOSButton();

        // 3. The Grid Logic (The Modules)
        setupGridClicks();
    }

    private void setupSOSButton() {
        // Standard Click: Warning
        btnSOS.setOnClickListener(v -> {
            Toast.makeText(this, "HOLD BUTTON FOR 2 SECONDS TO BROADCAST SIGNAL", Toast.LENGTH_SHORT).show();
        });

        // Long Click: ACTION
        btnSOS.setOnLongClickListener(v -> {
            triggerEmergencyProtocol();
            return true; // "True" tells Android we handled the click, don't do the normal click after
        });
    }

    private void triggerEmergencyProtocol() {
        // Visual Feedback (Make it feel real)
        btnSOS.setText("SIGNAL TRANSMITTED!");
        btnSOS.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));

        Toast.makeText(this, "⚠️ SOS BROADCAST SENT TO HIVE HQ ⚠️", Toast.LENGTH_LONG).show();

        // TODO: In the future, this is where we add GPS Location + SMS Manager
    }

    private void setupGridClicks() {
        // Since we didn't give IDs to the inner Layouts in XML yet,
        // we can cheat by looping through the Grid children.

        int childCount = gridModules.getChildCount();

        for (int i = 0; i < childCount; i++) {
            final int index = i;
            View card = gridModules.getChildAt(i);

            card.setOnClickListener(v -> {
                handleGridClick(index);
            });
        }
    }

    // Inside handleGridClick in DashboardActivity.java

    private void handleGridClick(int index) {
        switch (index) {
            case 0:
                // Live Map
                Toast.makeText(this, "Opening Map...", Toast.LENGTH_SHORT).show();
                break;

            case 1:
                // Broadcast
                startActivity(new Intent(this, BroadcastActivity.class));
                break;

            case 2:
                // Alerts
                startActivity(new Intent(this, AlertsActivity.class));
                break;

            case 3:
                // Settings
                startActivity(new Intent(this, ConfigActivity.class));
                break;
        }
    }
}