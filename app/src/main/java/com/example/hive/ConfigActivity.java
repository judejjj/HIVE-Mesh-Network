package com.example.hive; // CHANGE THIS

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ConfigActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_config);

        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnWipe = findViewById(R.id.btnWipe);

        // LOGOUT LOGIC
        btnLogout.setOnClickListener(v -> {
            // Go back to Login Screen
            Intent intent = new Intent(ConfigActivity.this, MainActivity.class);
            // Clear the "back stack" so pressing Back doesn't return to the Dashboard
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        // WIPE LOGIC (Mock)
        btnWipe.setOnClickListener(v -> {
            Toast.makeText(this, "LOCAL CACHE PURGED", Toast.LENGTH_SHORT).show();
        });
    }
}