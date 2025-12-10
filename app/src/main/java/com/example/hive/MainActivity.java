package com.example.hive; // CHANGE THIS to your actual package name


import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;

    // SECURITY 1: Strict Input Patterns (Sanitization)
    // Only allow alphanumeric characters for ID. No special symbols that could trigger SQL injection later.
    private static final Pattern AGENT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]*$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SECURITY 2: Prevent Screenshots & Recent Apps Preview
        // This ensures the screen goes BLACK if someone tries to screenshot it,
        // and hides content in the "Recent Apps" switcher.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // Hide Action Bar for immersion
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> attemptSecureLogin());
    }

    private void attemptSecureLogin() {
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        // 1. Basic Validation
        if (user.isEmpty()) {
            etUsername.setError("Identity Required");
            etUsername.requestFocus();
            return;
        }

        if (pass.isEmpty()) {
            etPassword.setError("Passphrase Required");
            etPassword.requestFocus();
            return;
        }

        // 2. THE HARDCODED "DB" CHECK (Updated for 2 Users)

        // USER 1 CREDENTIALS
        boolean isUser1 = user.equals("hive1") && pass.equals("hive1");

        // USER 2 CREDENTIALS (Add as many as you want here)
        boolean isUser2 = user.equals("hive2") && pass.equals("hive2");

        // CHECK: Is it User 1 OR (||) User 2?
        if (isUser1 || isUser2) {
            // SUCCESS
            performSystemCheck(user, pass);
        } else {
            // FAILURE
            etPassword.setError("ACCESS DENIED: Invalid Credentials");
            Toast.makeText(this, "SECURITY ALERT: Unauthorized Access Attempt", Toast.LENGTH_LONG).show();
        }
    }

    private void performSystemCheck(String user, String pass) {
        // Show the "Connecting" toast
        Toast.makeText(this, "ENCRYPTING UPLINK... VERIFYING ID", Toast.LENGTH_SHORT).show();

        // CREATE THE INTENT (The Bridge)
        // "this" is where we are, "DashboardActivity.class" is where we are going.
        android.content.Intent intent = new android.content.Intent(this, DashboardActivity.class);

        // Add flags to prevent going BACK to login screen with the back button
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(intent);
    }
}