package com.example.hive;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PeerListActivity extends AppCompatActivity {

    private static final String SERVICE_ID = "com.example.hive";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    // --- UI ELEMENTS ---
    private LinearLayout layoutScanner;
    private LinearLayout layoutChat;

    // Scanner UI
    private TextView tvPeerStatus, tvEmpty;
    private RecyclerView rvPeers;
    private Button btnScan;
    private PeerAdapter adapter;
    private final List<DiscoveredPeer> discoveredPeers = new ArrayList<>();

    // Chat UI (Radio Mode)
    private TextView tvChatStatus;
    private RecyclerView rvChat;
    private EditText etMessage;
    private ImageButton btnSend; // Changed to ImageButton
    private ImageButton btnMic; // PTT
    // REMOVED btnBack

    // Chat Data
    private ChatAdapter chatAdapter;
    private final List<String> chatMessages = new ArrayList<>();

    // --- PTT VARIABLES ---
    private MediaRecorder recorder;
    private String audioFileName;

    // --- NEARBY VARIABLES ---
    private ConnectionsClient connectionsClient;
    private String myNickname;
    private String connectedEndpointId;
    private String connectedNodeName;
    private boolean isChatting = false;

    static class DiscoveredPeer {
        String endpointId;
        String name;

        DiscoveredPeer(String id, String name) {
            this.endpointId = id;
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peer_list);

        // Bind UI
        layoutScanner = findViewById(R.id.layout_scanner);
        layoutChat = findViewById(R.id.layout_chat);

        tvPeerStatus = findViewById(R.id.tvPeerStatus);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvPeers = findViewById(R.id.rvPeers);
        btnScan = findViewById(R.id.btnScan);

        tvChatStatus = findViewById(R.id.tvChatStatus);
        rvChat = findViewById(R.id.rvChat); // UPDATED ID
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        // btnBack REMOVED
        btnMic = findViewById(R.id.btnMic); // PTT

        // Setup Audio File
        audioFileName = getExternalCacheDir().getAbsolutePath() + "/voice_note.3gp";

        // Setup Peer List
        rvPeers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PeerAdapter(discoveredPeers, peer -> {
            // SENDER LOGIC: INITIATE CALL
            connectTo(peer);
        });
        rvPeers.setAdapter(adapter);

        // Setup Chat List (Recycler)
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(chatMessages);
        rvChat.setAdapter(chatAdapter);

        // Setup Chat Buttons
        btnSend.setOnClickListener(v -> sendMessage());
        // btnBack Listener REMOVED
        btnScan.setOnClickListener(v -> restartDiscovery());

        btnMic.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startRecording();
                    return true;
                case MotionEvent.ACTION_UP:
                    stopRecording();
                    return true;
            }
            return false;
        });

        myNickname = MeshHelper.getBroadcastName(this);
        tvPeerStatus.setText("My Handle: " + myNickname);

        connectionsClient = Nearby.getConnectionsClient(this);
    }

    @Override
    public void onBackPressed() {
        if (isChatting) {
            disconnectAndReturn();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startAdvertising();
        startDiscovery();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (connectionsClient != null) {
            connectionsClient.stopAllEndpoints();
            connectionsClient.stopAdvertising();
            connectionsClient.stopDiscovery();
        }
    }

    // --- CORE NETWORKING ---

    private void startAdvertising() {
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(myNickname, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener(v -> {
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Adv Fail", Toast.LENGTH_SHORT).show());
    }

    private void startDiscovery() {
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnFailureListener(e -> tvEmpty.setText("Scanning..."))
                .addOnFailureListener(e -> Toast.makeText(this, "Scan Fail", Toast.LENGTH_SHORT).show());
    }

    private void restartDiscovery() {
        connectionsClient.stopDiscovery();
        discoveredPeers.clear();
        adapter.notifyDataSetChanged();
        startDiscovery();
    }

    private void connectTo(DiscoveredPeer peer) {
        Toast.makeText(this, "Calling " + peer.name + "...", Toast.LENGTH_SHORT).show();
        // Stop discovery to stabilize connection
        connectionsClient.stopDiscovery();

        connectionsClient.requestConnection(myNickname, peer.endpointId, connectionLifecycleCallback)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Call Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    startDiscovery(); // Resume if failed
                });
    }

    private void disconnectAndReturn() {
        if (connectedEndpointId != null) {
            connectionsClient.disconnectFromEndpoint(connectedEndpointId);
        }
        connectedEndpointId = null;
        isChatting = false;
        chatMessages.clear();
        chatAdapter.notifyDataSetChanged();

        // Reset UI
        layoutChat.setVisibility(View.GONE);
        layoutScanner.setVisibility(View.VISIBLE);

        // Resume Scan
        startDiscovery();
        startAdvertising(); // Re-advertise in case we stopped
    }

    private void sendMessage() {
        String msg = etMessage.getText().toString().trim();
        if (msg.isEmpty() || connectedEndpointId == null)
            return;

        Payload payload = Payload.fromBytes(msg.getBytes(StandardCharsets.UTF_8));
        connectionsClient.sendPayload(connectedEndpointId, payload);

        addChatMessage("ME: " + msg);
        etMessage.setText("");
    }

    private void addChatMessage(String text) {
        runOnUiThread(() -> {
            chatMessages.add(text);
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
            rvChat.scrollToPosition(chatMessages.size() - 1);
        });
    }

    // --- PTT LOGIC ---

    private void startRecording() {
        addChatMessage("SYS: Recording...");
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(audioFileName);

        try {
            recorder.prepare();
            recorder.start();
            btnMic.setColorFilter(Color.RED); // Visual cue
        } catch (Exception e) {
            addChatMessage("SYS: Rec Start Fail: " + e.getMessage());
            Log.e("PTT", "START FAIL", e);
        }
    }

    private void stopRecording() {
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
                btnMic.setColorFilter(0xFF00E5FF); // Reset cue (Cyan)
                processAudioFile();
            }
        } catch (Exception e) {
            addChatMessage("SYS: Rec Stop Fail: " + e.getMessage());
            Log.e("PTT", "STOP FAIL", e);
        }
    }

    private void processAudioFile() {
        if (connectedEndpointId == null) {
            addChatMessage("SYS: No Peer Connected.");
            return;
        }

        try {
            File file = new File(audioFileName);
            FileInputStream fis = new FileInputStream(file);
            byte[] fileBytes = new byte[(int) file.length()];
            fis.read(fileBytes);
            fis.close();

            // HEADER STRATEGY: "VOICE:" + Bytes
            String header = "VOICE:";
            byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
            byte[] payloadBytes = new byte[headerBytes.length + fileBytes.length];

            System.arraycopy(headerBytes, 0, payloadBytes, 0, headerBytes.length);
            System.arraycopy(fileBytes, 0, payloadBytes, headerBytes.length, fileBytes.length);

            Payload payload = Payload.fromBytes(payloadBytes);
            connectionsClient.sendPayload(connectedEndpointId, payload);

            // SAVE LOCAL & DISPLAY BUBBLE
            String savedPath = saveToSessionFile(fileBytes);
            addChatMessage("AUDIO:ME:" + savedPath);

        } catch (Exception e) {
            addChatMessage("SYS: Audio Process Fail: " + e.getMessage());
        }
    }

    private String saveToSessionFile(byte[] audioData) {
        try {
            // Unique Filename
            String filename = "audio_" + System.currentTimeMillis() + ".3gp";
            File dir = getExternalCacheDir();
            File dest = new File(dir, filename);

            FileOutputStream fos = new FileOutputStream(dest);
            fos.write(audioData);
            fos.close();

            return dest.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    // --- CALLBACKS ---

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            if (isChatting)
                return; // Don't update list while chatting

            boolean exists = false;
            for (DiscoveredPeer p : discoveredPeers) {
                if (p.name.equals(info.getEndpointName())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                discoveredPeers.add(new DiscoveredPeer(endpointId, info.getEndpointName()));
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(View.GONE);
            }
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            // Optional cleanup
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            // RECEIVER LOGIC: AUTO-ACCEPT
            connectionsClient.acceptConnection(endpointId, payloadCallback);

            connectedNodeName = info.getEndpointName();
            Toast.makeText(PeerListActivity.this, "Incoming: " + connectedNodeName, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                // SUCCESSFUL CONNECTION
                connectedEndpointId = endpointId;
                isChatting = true;

                // Stop discovery/advertising to save bandwidth/battery and focus on chat
                connectionsClient.stopDiscovery();
                connectionsClient.stopAdvertising();

                // SWITCH UI
                layoutScanner.setVisibility(View.GONE);
                layoutChat.setVisibility(View.VISIBLE);

                tvChatStatus.setText("CONNECTED: " + connectedNodeName);
                addChatMessage("SYS: Secure Channel Initialized...");

            } else {
                Toast.makeText(PeerListActivity.this, "Connection Failed", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            if (endpointId.equals(connectedEndpointId)) {
                Toast.makeText(PeerListActivity.this, "Disconnected", Toast.LENGTH_LONG).show();
                disconnectAndReturn();
            }
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                byte[] receivedBytes = payload.asBytes();
                String checkHeader = new String(receivedBytes, StandardCharsets.UTF_8);

                if (checkHeader.startsWith("VOICE:")) {
                    // IT IS AUDIO
                    try {
                        // Strip Header
                        int headerLen = "VOICE:".length();
                        int audioLen = receivedBytes.length - headerLen;
                        byte[] audioBytes = new byte[audioLen];
                        System.arraycopy(receivedBytes, headerLen, audioBytes, 0, audioLen);

                        // Save Locally
                        String filePath = saveToSessionFile(audioBytes);
                        addChatMessage("AUDIO:THEM:" + filePath);

                    } catch (Exception e) {
                        addChatMessage("SYS: Audio Save Fail: " + e.getMessage());
                    }

                } else {
                    // IT IS TEXT
                    String msg = new String(receivedBytes, StandardCharsets.UTF_8);
                    String name = connectedNodeName.split("#")[0];
                    addChatMessage(name + ": " + msg);
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
        }
    };

    // --- ADAPTERS ---
    interface OnPeerClickListener {
        void onClick(DiscoveredPeer peer);
    }

    static class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.PeerViewHolder> {
        private final List<DiscoveredPeer> list;
        private final OnPeerClickListener listener;

        PeerAdapter(List<DiscoveredPeer> list, OnPeerClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent,
                    false);
            return new PeerViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
            DiscoveredPeer peer = list.get(position);
            holder.text.setText(peer.name);
            holder.itemView.setOnClickListener(v -> listener.onClick(peer));
            // Force text color for visibility on dark theme
            holder.text.setTextColor(0xFF00E5FF); // Cyan
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class PeerViewHolder extends RecyclerView.ViewHolder {
            TextView text;

            PeerViewHolder(View v) {
                super(v);
                text = v.findViewById(android.R.id.text1);
            }
        }
    }

    // --- CHAT ADAPTER (THE BUBBLES) ---
    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
        private final List<String> msgs;

        ChatAdapter(List<String> msgs) {
            this.msgs = msgs;
        }

        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent,
                    false);
            return new ChatViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            String msg = msgs.get(position);
            holder.text.setText(msg);
            holder.text.setTextColor(Color.WHITE);
            holder.itemView.setOnClickListener(null); // Reset Listener

            if (msg.startsWith("AUDIO:")) {
                // FORMAT: AUDIO:SENDER:FILEPATH
                String[] parts = msg.split(":", 3);
                if (parts.length == 3) {
                    String sender = parts[1]; // ME or THEM
                    String path = parts[2];

                    holder.text.setText("▶ 🎤 Voice Note (Tap to Play)");
                    if (sender.equals("ME")) {
                        holder.text.setTextColor(Color.GREEN);
                        holder.text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                    } else {
                        holder.text.setTextColor(Color.CYAN);
                        holder.text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    }

                    // CLICK TO PLAY
                    holder.itemView.setOnClickListener(v -> {
                        try {
                            MediaPlayer mp = new MediaPlayer();
                            mp.setDataSource(path);
                            mp.prepare();
                            mp.start();
                            Toast.makeText(v.getContext(), "Playing...", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(v.getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else if (msg.startsWith("ME:")) {
                holder.text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                holder.text.setTextColor(Color.GREEN);
            } else if (msg.startsWith("SYS:")) {
                holder.text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                holder.text.setTextColor(Color.GRAY);
                holder.text.setTextSize(12);
            } else {
                holder.text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                holder.text.setTextColor(Color.CYAN);
            }
        }

        @Override
        public int getItemCount() {
            return msgs.size();
        }

        static class ChatViewHolder extends RecyclerView.ViewHolder {
            TextView text;

            ChatViewHolder(View v) {
                super(v);
                text = v.findViewById(android.R.id.text1);
            }
        }
    }
}