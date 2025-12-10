package com.example.hive;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ConfigActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "HiveData";
    private static final String ALERTS_PREFS = "HiveAlerts";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_config);

        Button btnReset = findViewById(R.id.btnResetNetwork);
        Button btnWipe = findViewById(R.id.btnWipe);

        // RESET NETWORK
        btnReset.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean("RESET_NOW", true).apply();
            Toast.makeText(this, "Network Reboot Requested...", Toast.LENGTH_SHORT).show();
            finish();
        });

        // EMERGENCY WIPE
        btnWipe.setOnClickListener(v -> {
            // 1. Clear Chat
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
            // 2. Clear Alerts
            getSharedPreferences(ALERTS_PREFS, Context.MODE_PRIVATE).edit().clear().apply();

            Toast.makeText(this, "SYSTEM PURGED. LOGGING OUT...", Toast.LENGTH_LONG).show();

            // 3. Force Login Screen (This kills BroadcastActivity in the background)
            Intent intent = new Intent(ConfigActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}