package com.example.hive; // <--- CHECK YOUR PACKAGE NAME

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

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

    private ConnectionsClient connectionsClient;
    private final String SERVICE_ID = "com.example.hive.OFFLINE_LINK";
    private final String USER_NICKNAME = "Agent " + Build.MODEL;

    // P2P_CLUSTER allows devices to be both senders and receivers (Mesh-like)
    private final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    private final List<String> connectedEndpoints = new ArrayList<>();

    // Flag to prevent stopping discovery multiple times
    private boolean isConnecting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_broadcast);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSendBroadcast);
        tvLog = findViewById(R.id.tvLog);

        connectionsClient = Nearby.getConnectionsClient(this);

        // CHECK PERMISSIONS
        if (!hasPermissions()) {
            appendLog("Requesting Permissions...");
            requestPermissions();
        } else {
            startOfflineNetwork();
        }

        btnSend.setOnClickListener(v -> sendMessage());
    }

    // --- 1. NETWORK STARTUP ---
    private void startOfflineNetwork() {
        if (!hasPermissions()) {
            appendLog("ERROR: Missing Permissions. Cannot Start.");
            return;
        }

        tvLog.setText("> INITIALIZING OFFLINE PROTOCOLS...\n");
        startAdvertising();
        startDiscovery();
    }

    private void startAdvertising() {
        AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();

        connectionsClient.startAdvertising(
                        USER_NICKNAME, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(aVoid -> appendLog("Broadcasting Signal (Advertising)..."))
                .addOnFailureListener(e -> appendLog("Advertising Failed: " + e.getMessage()));
    }

    private void startDiscovery() {
        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();

        connectionsClient.startDiscovery(
                        SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(aVoid -> appendLog("Scanning for Agents (Discovery)..."))
                .addOnFailureListener(e -> appendLog("Scanner Failed: " + e.getMessage()));
    }

    // --- 2. CONNECTION HANDLERS ---

    // FOUND A DEVICE
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
                    if (isConnecting || connectedEndpoints.contains(endpointId)) {
                        return; // Already busy connecting or connected
                    }

                    appendLog("Device Found: " + info.getEndpointName());

                    // CRITICAL FIX FOR 8012 ERROR:
                    // We MUST stop discovery before requesting a connection.
                    // The radio cannot scan and connect at the same time.
                    connectionsClient.stopDiscovery();
                    isConnecting = true;

                    appendLog("Requesting Link...");
                    connectionsClient.requestConnection(USER_NICKNAME, endpointId, connectionLifecycleCallback)
                            .addOnFailureListener(e -> {
                                appendLog("Request Failed: " + e.getMessage());
                                isConnecting = false;
                                // Restart discovery if connection failed so we can find others
                                startDiscovery();
                            });
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                    appendLog("Device Lost: " + endpointId);
                }
            };

    // CONNECTION STATUS CHANGES
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
                    appendLog("Incoming Link: " + info.getEndpointName());
                    // Auto-Accept connection
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
                    isConnecting = false; // We are done connecting (success or fail)

                    if (result.getStatus().isSuccess()) {
                        appendLog(">>> SECURE LINK ESTABLISHED <<<");
                        connectedEndpoints.add(endpointId);

                        // Restart discovery to find MORE people (Mesh building)
                        // Wait a tiny bit to let the connection stabilize
                        new android.os.Handler().postDelayed(() -> startDiscovery(), 2000);

                    } else {
                        appendLog("Link Failed: " + result.getStatus().getStatusCode());
                        // If failed, start scanning again
                        startDiscovery();
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    appendLog("Link Severed: " + endpointId);
                    connectedEndpoints.remove(endpointId);
                }
            };

    // --- 3. SEND/RECEIVE ---

    private void sendMessage() {
        String msg = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(msg)) return;

        if (!connectedEndpoints.isEmpty()) {
            Payload payload = Payload.fromBytes(msg.getBytes(StandardCharsets.UTF_8));
            connectionsClient.sendPayload(connectedEndpoints, payload);

            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            appendLog("\n[" + time + "] YOU: " + msg);
            etMessage.setText("");
        } else {
            Toast.makeText(this, "NO DEVICES CONNECTED", Toast.LENGTH_SHORT).show();
            appendLog("Error: No live connections.");
        }
    }

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
                    if (payload.getType() == Payload.Type.BYTES) {
                        String receivedMsg = new String(payload.asBytes(), StandardCharsets.UTF_8);
                        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                        appendLog("\n[" + time + "] INCOMING: " + receivedMsg);
                    }
                }

                @Override
                public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) { }
            };

    private void appendLog(String text) {
        // Run on UI Thread to prevent crashes
        runOnUiThread(() -> tvLog.append("\n" + text));
    }

    // --- 4. ROBUST PERMISSIONS ---

    private static final String[] REQUIRED_PERMISSIONS;

    static {
        if (Build.VERSION.SDK_INT >= 31) {
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.NEARBY_WIFI_DEVICES // Crucial for Android 13+
            };
        } else {
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
            };
        }
    }

    private boolean hasPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 100);
    }

    // What happens AFTER user clicks "Allow"
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (hasPermissions()) {
                appendLog("Permissions Granted. Starting Network...");
                startOfflineNetwork();
            } else {
                Toast.makeText(this, "Permissions Denied. Offline Mode Failed.", Toast.LENGTH_LONG).show();
                appendLog("ERROR: User Denied Permissions.");
            }
        }
    }
}