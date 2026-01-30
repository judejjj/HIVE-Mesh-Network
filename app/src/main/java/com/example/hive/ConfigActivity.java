package com.example.hive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.UUID;

public class ConfigActivity extends AppCompatActivity {

    private EditText etNickname;
    private TextView tvUuid;
    private Button btnSave;

    private static final String PREFS_NAME = "HivePrefs";
    private static final String KEY_NICKNAME = "KEY_NICKNAME";
    private static final String KEY_UUID = "KEY_MY_UUID"; // Must match what we use elsewhere

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null)
            getSupportActionBar().hide();
        setContentView(R.layout.activity_config);

        etNickname = findViewById(R.id.etNickname);
        tvUuid = findViewById(R.id.tvUuid);
        btnSave = findViewById(R.id.btnSaveParams);

        Button btnReset = findViewById(R.id.btnResetNetwork);
        Button btnWipe = findViewById(R.id.btnWipe);

        // Load existing data
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String currentNick = prefs.getString(KEY_NICKNAME, "Survivor");

        // Ensure UUID via Helper Logic
        String handle = MeshHelper.getBroadcastName(this);

        etNickname.setText(currentNick);
        tvUuid.setText("HANDLE: " + handle);

        btnSave.setOnClickListener(v -> {
            String newNick = etNickname.getText().toString().trim();
            if (newNick.isEmpty()) {
                etNickname.setError("Nickname required");
                return;
            }
            // Save raw name
            prefs.edit().putString(KEY_NICKNAME, newNick).apply();
            Toast.makeText(this, "Identity Saved", Toast.LENGTH_SHORT).show();
            finish();
        });

        btnReset.setOnClickListener(v -> {
            prefs.edit().putBoolean("RESET_NOW", true).apply();
            Toast.makeText(this, "Reboot Requested", Toast.LENGTH_SHORT).show();
            finish();
        });

        btnWipe.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            getSharedPreferences("HiveAlerts", Context.MODE_PRIVATE).edit().clear().apply();
            Toast.makeText(this, "PURGED", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}