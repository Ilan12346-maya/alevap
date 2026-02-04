package com.alevap;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.view.Gravity;

public class CrashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        String error = getIntent().getStringExtra("error");
        if (error == null) error = "No error message provided.";

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);
        layout.setBackgroundColor(0xFF1E1E1E);

        TextView title = new TextView(this);
        title.setText("App Crash Detected");
        title.setTextColor(Color.RED);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.setMargins(0, 30, 0, 30);
        scrollView.setLayoutParams(scrollParams);

        TextView errorText = new TextView(this);
        errorText.setText(error);
        errorText.setTextColor(Color.WHITE);
        errorText.setTextSize(14);
        errorText.setPadding(10, 10, 10, 10);
        scrollView.addView(errorText);
        layout.addView(scrollView);

        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);

        Button copyButton = new Button(this);
        copyButton.setText("Copy Log");
        String finalError = error;
        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Crash Log", finalError);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        buttonLayout.addView(copyButton);

        Button restartButton = new Button(this);
        restartButton.setText("Restart App");
        restartButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        buttonLayout.addView(restartButton);

        layout.addView(buttonLayout);

        setContentView(layout);
    }
}