package com.example.hive; // <--- MAKE SURE THIS MATCHES YOUR PACKAGE

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

    // P2P_CLUSTER is the correct strategy for M-to-N (Mesh) networking
    private final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    // List of all currently connected devices
    private final List<String> connectedEndpoints = new ArrayList<>();

    // Status flag to prevent overlapping operations
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

        if (!hasPermissions()) {
            appendLog("Requesting Permissions...");
            requestPermissions();
        } else {
            startOfflineNetwork();
        }

        btnSend.setOnClickListener(v -> sendMessage());
    }

    // --- 1. NETWORK INITIALIZATION ---
    private void startOfflineNetwork() {
        if (!hasPermissions()) {
            appendLog("CRITICAL: Missing Permissions.");
            return;
        }

        tvLog.setText("> HIVE MESH PROTOCOLS ACTIVE...\n");
        startAdvertising();
        startDiscovery();
    }

    private void startAdvertising() {
        AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();

        connectionsClient.startAdvertising(
                        USER_NICKNAME, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(aVoid -> appendLog("Broadcasting Identity..."))
                .addOnFailureListener(e -> appendLog("Broadcast Fail: " + e.getMessage()));
    }

    private void startDiscovery() {
        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();

        connectionsClient.startDiscovery(
                        SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(aVoid -> appendLog("Scanning for Nodes..."))
                .addOnFailureListener(e -> appendLog("Scan Fail: " + e.getMessage()));
    }

    // --- 2. FINDING DEVICES (Discovery) ---
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
                    if (isConnecting || connectedEndpoints.contains(endpointId)) {
                        return;
                    }

                    appendLog("Node Detected: " + info.getEndpointName());

                    // STOP SCANNING to prevent Error 8012 (Radio Busy)
                    connectionsClient.stopDiscovery();
                    isConnecting = true;

                    appendLog("Initiating Handshake...");
                    connectionsClient.requestConnection(USER_NICKNAME, endpointId, connectionLifecycleCallback)
                            .addOnFailureListener(e -> {
                                appendLog("Handshake Failed: " + e.getMessage());
                                isConnecting = false;
                                startDiscovery(); // Resume scanning
                            });
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                    appendLog("Signal Lost: " + endpointId);
                }
            };

    // --- 3. MANAGING CONNECTIONS ---
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
                    appendLog("Incoming Link: " + info.getEndpointName());
                    // Automatically accept the connection
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
                    isConnecting = false;

                    if (result.getStatus().isSuccess()) {
                        appendLog(">>> SECURE MESH LINK: " + endpointId + " <<<");
                        connectedEndpoints.add(endpointId);

                        // Wait 2 seconds, then start scanning again to find MORE people (Mesh building)
                        new Handler(Looper.getMainLooper()).postDelayed(() -> startDiscovery(), 2000);
                    } else {
                        appendLog("Link Failed: " + result.getStatus().getStatusCode());
                        startDiscovery(); // Try again
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    appendLog("Link Severed: " + endpointId);
                    connectedEndpoints.remove(endpointId);
                }
            };

    // --- 4. MESSAGING & REPEATER LOGIC ---

    // Send a new message from ME
    private void sendMessage() {
        String msg = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(msg)) return;

        if (!connectedEndpoints.isEmpty()) {
            // Format: [SENDER_NAME]: Message
            String fullMessage = USER_NICKNAME + ": " + msg;
            Payload payload = Payload.fromBytes(fullMessage.getBytes(StandardCharsets.UTF_8));

            // Send to everyone I am directly connected to
            connectionsClient.sendPayload(connectedEndpoints, payload);

            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            appendLog("\n[" + time + "] YOU: " + msg);
            etMessage.setText("");
        } else {
            Toast.makeText(this, "NO NODES IN RANGE", Toast.LENGTH_SHORT).show();
        }
    }

    // Receive a message from SOMEONE ELSE
    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
                    if (payload.getType() == Payload.Type.BYTES) {
                        String receivedData = new String(payload.asBytes(), StandardCharsets.UTF_8);

                        // Display it
                        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                        appendLog("\n[" + time + "] " + receivedData);

                        // REPEATER: Pass it on!
                        forwardMessageToMesh(receivedData, endpointId);
                    }
                }

                @Override
                public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) { }
            };

    // THE REPEATER FUNCTION
    private void forwardMessageToMesh(String originalMsg, String senderId) {
        // If I'm the only one here, nowhere to send.
        if (connectedEndpoints.size() <= 1) return;

        Payload payload = Payload.fromBytes(originalMsg.getBytes(StandardCharsets.UTF_8));

        // Loop through everyone I know
        for (String targetEndpoint : connectedEndpoints) {
            // Don't send it back to the person who just sent it to me
            if (!targetEndpoint.equals(senderId)) {
                connectionsClient.sendPayload(targetEndpoint, payload);
            }
        }
    }

    // --- 5. UTILITIES & PERMISSIONS ---
    private void appendLog(String text) {
        runOnUiThread(() -> tvLog.append("\n" + text));
    }

    private static final String[] REQUIRED_PERMISSIONS;
    static {
        if (Build.VERSION.SDK_INT >= 31) {
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.NEARBY_WIFI_DEVICES
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (hasPermissions()) {
                startOfflineNetwork();
            } else {
                Toast.makeText(this, "Permissions Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}