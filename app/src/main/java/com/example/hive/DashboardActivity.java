package com.example.hive;

import android.content.Intent;
import android.os.Bundle;
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
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_dashboard);

        btnSOS = findViewById(R.id.btnSOS);
        gridModules = findViewById(R.id.gridModules);

        setupSOSButton();
        setupGridClicks();
    }

    private void setupSOSButton() {
        // SINGLE CLICK -> GO TO SOS PAGE
        btnSOS.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, SosActivity.class));
        });
    }

    private void setupGridClicks() {
        int childCount = gridModules.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final int index = i;
            gridModules.getChildAt(i).setOnClickListener(v -> handleGridClick(index));
        }
    }

    private void handleGridClick(int index) {
        switch (index) {
            case 0: Toast.makeText(this, "Map Offline", Toast.LENGTH_SHORT).show(); break;
            case 1: startActivity(new Intent(this, BroadcastActivity.class)); break;
            case 2: startActivity(new Intent(this, AlertsActivity.class)); break;
            case 3: startActivity(new Intent(this, ConfigActivity.class)); break;
        }
    }
}