package com.example.hive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MeshService extends Service {

    private static final String TAG = "HiveMeshService";
    private static final String SERVICE_ID = "com.example.hive";
    private static final String CHANNEL_ID = "HiveMeshChannel";
    private static final int NOTIFICATION_ID = 1;

    // Binder
    private final IBinder binder = new LocalBinder();

    // Nearby Connections
    private ConnectionsClient connectionsClient;
    private final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    // State
    private String myNickname;
    private final List<DiscoveredEndpoint> discoveredEndpoints = new ArrayList<>();
    private final List<String> connectedEndpoints = new ArrayList<>();
    private final List<DiscoveredEndpoint> connectedPeers = new ArrayList<>(); // Objects with names
    private final java.util.Map<String, String> pendingNames = new java.util.HashMap<>();

    // Replay State
    private String lastStatus = "Initializing...";
    private String lastError = null;

    private boolean isAdvertising = false;
    private boolean isDiscovering = false;

    // Listeners for Activities
    private MeshListener meshListener;

    public interface MeshListener {
        void onEndpointFound(String endpointId, String name);

        void onEndpointLost(String endpointId);

        void onMessageReceived(String endpointId, String message);

        void onMeshError(String error);

        void onMeshStatus(String status); // NEW: Successfully added this time
    }

    public class LocalBinder extends Binder {
        MeshService getService() {
            return MeshService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        connectionsClient = Nearby.getConnectionsClient(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start Foreground immediately
        startForeground(NOTIFICATION_ID, createNotification());

        // Load Identity
        SharedPreferences prefs = getSharedPreferences("HivePrefs", Context.MODE_PRIVATE);
        myNickname = prefs.getString("KEY_NICKNAME", "Unknown_Survivor");

        // Start Mesh automatically
        startMesh();

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMesh();
    }

    // --- MESH CONTROL ---

    private void startMesh() {
        updateStatus("Starting Mesh Radio...");
        startAdvertising();
        startDiscovery();
    }

    public void restartMesh() {
        updateStatus("Restarting Layer 1...");
        stopMesh();
        startMesh();
    }

    private void stopMesh() {
        connectionsClient.stopAllEndpoints();
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectedEndpoints.clear();
        discoveredEndpoints.clear();
        connectedPeers.clear();
        pendingNames.clear();
        isAdvertising = false;
        isDiscovering = false;
        updateStatus("Mesh Stopped");
    }

    private void startAdvertising() {
        if (isAdvertising)
            return;
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(myNickname, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(v -> {
                    isAdvertising = true;
                    updateStatus("Advertising Active");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Adv Fail", e);
                    lastError = "Adv Fail: " + e.getMessage();
                    if (meshListener != null)
                        meshListener.onMeshError(lastError);
                });
    }

    private void startDiscovery() {
        if (isDiscovering)
            return;
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener(v -> {
                    isDiscovering = true;
                    updateStatus("Scanning for Uplinks...");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Disc Fail", e);
                    lastError = "Scan Fail: " + e.getMessage();
                    if (meshListener != null)
                        meshListener.onMeshError(lastError);
                });
    }

    private void updateStatus(String status) {
        this.lastStatus = status;
        if (meshListener != null)
            meshListener.onMeshStatus(status);
    }

    // --- ACTIVITY API ---

    public void setMeshListener(MeshListener listener) {
        this.meshListener = listener;
        // Replay Re-notify of current state if needed
        if (listener != null) {
            if (lastError != null)
                listener.onMeshError(lastError);
            listener.onMeshStatus(lastStatus);

            // Replay peers
            for (DiscoveredEndpoint ep : discoveredEndpoints) {
                listener.onEndpointFound(ep.id, ep.name);
            }
        }
    }

    public List<DiscoveredEndpoint> getDiscoveredEndpoints() {
        return discoveredEndpoints;
    }

    public List<DiscoveredEndpoint> getConnectedPeers() {
        return connectedPeers;
    }

    public void broadcastMessage(String message) {
        if (connectedEndpoints.isEmpty())
            return;
        Payload payload = Payload.fromBytes(message.getBytes(StandardCharsets.UTF_8));
        connectionsClient.sendPayload(connectedEndpoints, payload);
    }

    public void connectToPeer(String endpointId) {
        connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback);
    }

    // --- CALLBACKS ---

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            Log.d(TAG, "Found: " + info.getEndpointName());
            discoveredEndpoints.add(new DiscoveredEndpoint(endpointId, info.getEndpointName()));
            if (meshListener != null)
                meshListener.onEndpointFound(endpointId, info.getEndpointName());

            // RESTORE AUTO-CONNECT LOGIC
            // The mesh should form automatically for Broadcasts to work.
            connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener(e -> Log.e(TAG, "Auto-Connect Fail", e));
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            Log.d(TAG, "Lost: " + endpointId);
            // Remove
            for (int i = 0; i < discoveredEndpoints.size(); i++) {
                if (discoveredEndpoints.get(i).id.equals(endpointId)) {
                    discoveredEndpoints.remove(i);
                    break;
                }
            }
            if (meshListener != null)
                meshListener.onEndpointLost(endpointId);
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            // Store name
            pendingNames.put(endpointId, info.getEndpointName());
            // Auto accept
            connectionsClient.acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                Log.d(TAG, "Connected: " + endpointId);
                connectedEndpoints.add(endpointId);

                String name = pendingNames.get(endpointId);
                if (name != null) {
                    connectedPeers.add(new DiscoveredEndpoint(endpointId, name));
                    pendingNames.remove(endpointId);
                } else {
                    connectedPeers.add(new DiscoveredEndpoint(endpointId, "Unknown Peer"));
                }
            } else {
                pendingNames.remove(endpointId);
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            Log.d(TAG, "Disconnected: " + endpointId);
            connectedEndpoints.remove(endpointId);
            for (int i = 0; i < connectedPeers.size(); i++) {
                if (connectedPeers.get(i).id.equals(endpointId)) {
                    connectedPeers.remove(i);
                    break;
                }
            }
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                String msg = new String(payload.asBytes(), StandardCharsets.UTF_8);
                if (meshListener != null)
                    meshListener.onMessageReceived(endpointId, msg);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
        }
    };

    // --- NOTIFICATION ---

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Hive Mesh Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null)
                manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Hive Mesh Active")
                .setContentText("Monitoring nearby secure uplinks...")
                .setSmallIcon(android.R.drawable.ic_menu_share) // Replace with app icon later
                .setPriority(NotificationCompat.PRIORITY_LOW);
        return builder.build();
    }

    // --- MODEL ---
    public static class DiscoveredEndpoint {
        public String id;
        public String name;

        public DiscoveredEndpoint(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
