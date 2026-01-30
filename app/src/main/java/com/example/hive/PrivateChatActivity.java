package com.example.hive;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.view.MotionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PrivateChatActivity extends AppCompatActivity {

    private static final String SERVICE_ID = "com.example.hive";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    private TextView tvTargetName;
    private RecyclerView rvChat;
    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnMic; // PTT

    private MediaRecorder recorder;
    private String audioFileName;

    private ChatAdapter adapter;
    private final List<String> messages = new ArrayList<>();

    private ConnectionsClient connectionsClient;
    private String myNickname;
    private String targetName;
    private String targetEndpointId;

    private final Set<String> discoveredEndpoints = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_chat);

        tvTargetName = findViewById(R.id.tvTargetName);
        rvChat = findViewById(R.id.rvPrivateChat);
        etMessage = findViewById(R.id.etPrivateMessage);
        btnSend = findViewById(R.id.btnSendPrivate);
        btnMic = findViewById(R.id.btnMic); // PTT

        // Setup Audio File
        audioFileName = getExternalCacheDir().getAbsolutePath() + "/voice_note.3gp";

        // GET TARGET FROM INTENT
        targetName = getIntent().getStringExtra("TARGET_NAME");
        if (targetName == null)
            targetName = "Unknown Target";

        tvTargetName.setText("SNIPING: " + targetName);

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages);
        rvChat.setAdapter(adapter);

        // Load Identity via Helper
        myNickname = MeshHelper.getBroadcastName(this);

        connectionsClient = Nearby.getConnectionsClient(this);

        btnSend.setOnClickListener(v -> sendMessage());

        // PTT LISTENER
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

        addSystemMessage("Initializing Sniper Mode...");
        addSystemMessage("Target: " + targetName);
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

    private void startAdvertising() {
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(myNickname, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnFailureListener(e -> addSystemMessage("Adv Fail: " + e.getMessage()));
    }

    private void startDiscovery() {
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnFailureListener(e -> addSystemMessage("Scan Fail: " + e.getMessage()));
    }

    private void sendMessage() {
        String msg = etMessage.getText().toString().trim();
        if (msg.isEmpty())
            return;

        if (targetEndpointId == null) {
            addSystemMessage("Target not connected.");
            return;
        }

        Payload payload = Payload.fromBytes(msg.getBytes(StandardCharsets.UTF_8));
        connectionsClient.sendPayload(targetEndpointId, payload);

        addMessage("ME: " + msg);
        etMessage.setText("");
    }

    private void addMessage(String text) {
        runOnUiThread(() -> {
            messages.add(text);
            adapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);
        });
    }

    private void addSystemMessage(String text) {
        addMessage("SYS: " + text);
    }

    // --- PTT LOGIC ---

    private void startRecording() {
        addSystemMessage("Recording...");
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
            addSystemMessage("Rec Start Fail: " + e.getMessage());
        }
    }

    private void stopRecording() {
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
                btnMic.setColorFilter(Color.WHITE); // Reset cue
                addSystemMessage("Sending Audio...");
                processAudioFile();
            }
        } catch (Exception e) {
            addSystemMessage("Rec Stop Fail: " + e.getMessage());
        }
    }

    private void processAudioFile() {
        if (targetEndpointId == null) {
            addSystemMessage("No Target Connected.");
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
            connectionsClient.sendPayload(targetEndpointId, payload);
            addMessage("SYS: [AUDIO SENT]");

            // SAVE LOCAL COPY & UPDATE UI
            String savedPath = saveToSessionFile(fileBytes);
            addMessage("AUDIO:ME:" + savedPath);

        } catch (Exception e) {
            addSystemMessage("Audio Process Fail: " + e.getMessage());
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
            if (discoveredEndpoints.contains(endpointId))
                return;
            discoveredEndpoints.add(endpointId);

            String foundName = info.getEndpointName();
            // SNIPER CHECK
            if (foundName.equals(targetName)) {
                addSystemMessage("Target Located: " + foundName);
                connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback)
                        .addOnFailureListener(e -> addSystemMessage("Req Fail: " + e.getMessage()));
            }
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            discoveredEndpoints.remove(endpointId);
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            // Check if this is who we want (Either they called us, or we called them)
            if (info.getEndpointName().equals(targetName) || targetName.equals(info.getEndpointName())) {
                connectionsClient.acceptConnection(endpointId, payloadCallback);
                addSystemMessage("Accepting Target Link...");
            } else {
                if (targetName.equals(info.getEndpointName())) { // paranoia check
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                } else {
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                }
            }
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                targetEndpointId = endpointId;
                addSystemMessage(">>> SECURE UPLINK ESTABLISHED");
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            addSystemMessage("Link Broken.");
            if (endpointId.equals(targetEndpointId))
                targetEndpointId = null;
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
                        addMessage("AUDIO:THEM:" + filePath);

                    } catch (Exception e) {
                        addSystemMessage("Audio Save Fail: " + e.getMessage());
                    }

                } else {
                    // IT IS TEXT
                    String msg = new String(receivedBytes, StandardCharsets.UTF_8);
                    String displayName = targetName.split("#")[0];
                    addMessage(displayName + ": " + msg);
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
        }
    };

    // --- ADAPTER ---
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
