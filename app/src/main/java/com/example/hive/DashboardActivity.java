package com.example.hive;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DashboardActivity extends AppCompatActivity {

    private Button btnSOS;
    private ImageButton btnHeaderSettings;
    private GridLayout gridModules;
    private static final int PERMISSION_REQUEST_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null)
            getSupportActionBar().hide();
        setContentView(R.layout.activity_dashboard);

        // --- IDENTITY SYSTEM ---
        checkAndGenerateIdentity();

        btnSOS = findViewById(R.id.btnSOS);
        btnHeaderSettings = findViewById(R.id.btnHeaderSettings);
        gridModules = findViewById(R.id.gridModules);

        setupSOSButton();
        setupGridClicks();
        setupHeaderSettings();

        // --- PERMISSIONS FIRST, THEN SERVICE ---
        checkAndRequestPermissions();
    }

    // --- SERVICE MANAGEMENT ---

    private void startMeshService() {
        Intent serviceIntent = new Intent(this, MeshService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // --- PERMISSION LOGIC (COPIED & IMPROVED) ---

    private void checkAndRequestPermissions() {
        List<String> requiredPermissions = new ArrayList<>();

        // 1. Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS); // Service Notification
        }
        // 2. Android 12 (API 31/32)
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        // 3. Android 11 and below
        else {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // Check missing
        List<String> missingPermissions = new ArrayList<>();
        for (String perm : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(perm);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            // Already have permissions, start service!
            startMeshService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startMeshService();
            } else {
                Toast.makeText(this, "Permissions Necessary for Mesh Network", Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- UI SETUP ---

    private void checkAndGenerateIdentity() {
        SharedPreferences prefs = getSharedPreferences("HivePrefs", Context.MODE_PRIVATE);
        String myUuid = prefs.getString("KEY_MY_UUID", null);
        String myNickname = prefs.getString("KEY_NICKNAME", null);

        if (myUuid == null) {
            myUuid = UUID.randomUUID().toString();
            prefs.edit().putString("KEY_MY_UUID", myUuid).apply();
        }

        if (myNickname == null) {
            String shortId = myUuid.substring(0, 4).toUpperCase();
            myNickname = "Survivor_" + shortId;
            prefs.edit().putString("KEY_NICKNAME", myNickname).apply();
            prefs.edit().putString("username", myNickname).apply();
        }
    }

    private void setupHeaderSettings() {
        btnHeaderSettings.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, ConfigActivity.class));
        });
    }

    private void setupSOSButton() {
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
            case 0:
                Toast.makeText(this, "Map Offline", Toast.LENGTH_SHORT).show();
                break;
            case 1:
                startActivity(new Intent(this, BroadcastActivity.class));
                break;
            case 2:
                startActivity(new Intent(this, AlertsActivity.class));
                break;
            case 3:
                startActivity(new Intent(this, PeerListActivity.class));
                break; // DIRECT UPLINK
        }
    }
}