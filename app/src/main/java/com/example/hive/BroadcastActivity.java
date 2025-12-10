package com.example.hive;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BroadcastActivity extends AppCompatActivity {

    private EditText etMessage;
    private Button btnSend;
    private TextView tvLog;
    private ScrollView scrollLog;

    private ConnectionsClient connectionsClient;
    private final String SERVICE_ID = "com.example.hive.OFFLINE_LINK";
    private final String USER_NICKNAME = "Agent " + Build.MODEL;

    private static final String PREFS_NAME = "HiveData";
    private static final String KEY_LOGS = "chat_logs";
    private static final String ALERTS_PREFS = "HiveAlerts";

    private final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private final List<String> connectedEndpoints = new ArrayList<>();

    // Status flags
    private boolean isConnecting = false;
    private boolean isAdvertising = false;
    private boolean isDiscovering = false;

    // SOS GHOST VARIABLES
    private String pendingSosPayload = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_broadcast);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSendBroadcast);
        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);

        connectionsClient = Nearby.getConnectionsClient(this);

        loadChatHistory();

        // 1. Setup SOS if coming from Map Screen
        handleIncomingSOS();

        if (!hasPermissions()) {
            requestPermissions();
        } else {
            // Reset network to ensure clean state
            resetNetwork();
        }

        btnSend.setOnClickListener(v -> sendManualMessage());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if Settings requested a Reset
        checkResetFlag();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNetwork();
    }

    // --- SOS LOGIC ---
    private void handleIncomingSOS() {
        if (getIntent().hasExtra("SOS_MSG")) {
            String sosMsg = getIntent().getStringExtra("SOS_MSG");
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

            // A. Sender (You) saves it locally first
            String alertPayload = "[URGENT] " + sosMsg;
            saveToAlertsDB(time, alertPayload);

            // B. Load into RAM to send to others
            pendingSosPayload = alertPayload;

            Toast.makeText(this, "SOS ARMED. CONNECTING...", Toast.LENGTH_LONG).show();
        }
    }

    // --- NETWORK MANAGEMENT (Fixes Handshake Bugs) ---
    private void stopNetwork() {
        connectionsClient.stopAllEndpoints();
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectedEndpoints.clear();
        isConnecting = false;
        isAdvertising = false;
        isDiscovering = false;
    }

    private void resetNetwork() {
        stopNetwork();
        new Handler(Looper.getMainLooper()).postDelayed(this::startOfflineNetwork, 1000);
    }

    private void checkResetFlag() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean("RESET_NOW", false)) {
            appendLog("\n>>> MANUAL RESET INITIATED <<<");
            resetNetwork();
            prefs.edit().putBoolean("RESET_NOW", false).apply();
        }
    }

    private void startOfflineNetwork() {
        if (!hasPermissions()) return;
        tvLog.append("\n> NETWORK ACTIVE. SCANNING...");
        startAdvertising();
        startDiscovery();
    }

    private void startAdvertising() {
        if (isAdvertising) return;
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(USER_NICKNAME, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(v -> isAdvertising = true)
                .addOnFailureListener(e -> appendLog("Adv Fail: " + e.getMessage()));
    }

    private void startDiscovery() {
        if (isDiscovering) return;
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(v -> isDiscovering = true)
                .addOnFailureListener(e -> appendLog("Scan Fail: " + e.getMessage()));
    }

    // --- CONNECTION HANDLERS ---
    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            if (isConnecting || connectedEndpoints.contains(endpointId)) return;

            appendLog("Found: " + info.getEndpointName());

            // CRITICAL FIX: Stop scanning before connecting
            connectionsClient.stopDiscovery();
            isDiscovering = false;
            isConnecting = true;

            connectionsClient.requestConnection(USER_NICKNAME, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener(e -> {
                        appendLog("Req Fail. Retrying...");
                        isConnecting = false;
                        startDiscovery(); // Restart scan
                    });
        }
        @Override public void onEndpointLost(@NonNull String endpointId) {}
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            connectionsClient.acceptConnection(endpointId, payloadCallback);
        }

        @Override public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            isConnecting = false;
            if (result.getStatus().isSuccess()) {
                appendLog(">>> CONNECTED: " + endpointId);
                connectedEndpoints.add(endpointId);

                // --- SENDER LOGIC: AUTO-SEND SOS ---
                if (!pendingSosPayload.isEmpty()) {
                    // Send the SOS to the new person
                    sendRawPayload(pendingSosPayload);
                    appendLog("⚠️ SOS UPLOADED TO " + endpointId);
                    // Note: We DO NOT leave the screen. Sender stays here to connect to more people.
                }

                // Restart discovery to find more people
                new Handler(Looper.getMainLooper()).postDelayed(BroadcastActivity.this::startDiscovery, 2000);
            } else {
                startDiscovery();
            }
        }

        @Override public void onDisconnected(@NonNull String endpointId) {
            appendLog("Disconnected: " + endpointId);
            connectedEndpoints.remove(endpointId);
            startDiscovery();
        }
    };

    // --- MESSAGING ---
    private void sendManualMessage() {
        String msg = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(msg)) return;
        // Normal chat messages
        String fullMsg = USER_NICKNAME + ": " + msg;
        sendRawPayload(fullMsg);
        etMessage.setText("");
    }

    private void sendRawPayload(String payloadString) {
        if (!connectedEndpoints.isEmpty()) {
            Payload payload = Payload.fromBytes(payloadString.getBytes(StandardCharsets.UTF_8));
            connectionsClient.sendPayload(connectedEndpoints, payload);

            // Only log if it's me chatting manually
            if (!payloadString.contains("[URGENT]")) {
                String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                appendLog("\n[" + time + "] YOU: " + payloadString.replace(USER_NICKNAME+": ", ""));
            }
        }
    }

    // --- RECEIVER LOGIC (The Jump) ---
    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                String data = new String(payload.asBytes(), StandardCharsets.UTF_8);
                String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

                // 1. CHECK FOR EMERGENCY
                if (data.contains("[URGENT]")) {
                    // I am the RECEIVER (B). I must be warned.

                    // Save to DB
                    saveToAlertsDB(time, data);

                    // Pass it on (Repeater)
                    forwardMessageToMesh(data, endpointId);

                    // JUMP TO RED SCREEN
                    Toast.makeText(BroadcastActivity.this, "⚠️ EMERGENCY ALERT RECEIVED", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(BroadcastActivity.this, AlertsActivity.class));
                    // Optional: finish(); if you want to close chat
                }
                else {
                    // Normal Chat
                    appendLog("\n[" + time + "] " + data);
                    forwardMessageToMesh(data, endpointId);
                }
            }
        }
        @Override public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {}
    };

    private void forwardMessageToMesh(String msg, String senderId) {
        if (connectedEndpoints.size() <= 1) return;
        Payload payload = Payload.fromBytes(msg.getBytes(StandardCharsets.UTF_8));
        for (String id : connectedEndpoints) {
            if (!id.equals(senderId)) connectionsClient.sendPayload(id, payload);
        }
    }

    // --- UTILS ---
    private void appendLog(String text) {
        runOnUiThread(() -> {
            tvLog.append("\n" + text);
            saveChatHistory(tvLog.getText().toString());
            scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void saveChatHistory(String fullLog) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_LOGS, fullLog).apply();
    }

    private void saveToAlertsDB(String time, String msg) {
        SharedPreferences prefs = getSharedPreferences(ALERTS_PREFS, Context.MODE_PRIVATE);
        String old = prefs.getString("alert_logs", "");
        String newEntry = "🔴 [" + time + "] " + msg + "\n\n" + old;
        prefs.edit().putString("alert_logs", newEntry).apply();
    }

    private void loadChatHistory() {
        String logs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LOGS, "");
        if (!logs.isEmpty()) tvLog.setText(logs);
    }

    private static final String[] PERMISSIONS;
    static {
        if (Build.VERSION.SDK_INT >= 31) {
            PERMISSIONS = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.NEARBY_WIFI_DEVICES};
        } else {
            PERMISSIONS = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE};
        }
    }

    private boolean hasPermissions() {
        for (String p : PERMISSIONS) if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    private void requestPermissions() { ActivityCompat.requestPermissions(this, PERMISSIONS, 100); }

    @Override public void onRequestPermissionsResult(int r, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == 100 && hasPermissions()) resetNetwork();
    }
}