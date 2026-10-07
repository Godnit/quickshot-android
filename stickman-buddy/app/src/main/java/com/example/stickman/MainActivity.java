package com.example.stickman;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView permissionText;
    private Button startButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        permissionText = findViewById(R.id.permissionText);
        startButton = findViewById(R.id.startButton);
        findViewById(R.id.permissionButton).setOnClickListener(v -> openOverlaySettings());
        startButton.setOnClickListener(v -> toggleBuddy());
        updateState();
    }
    @Override protected void onResume() { super.onResume(); updateState(); }

    private void toggleBuddy() {
        if (!canDrawOverlays()) {
            Toast.makeText(this, "فعّل صلاحية الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show();
            openOverlaySettings();
            return;
        }
        Intent intent = new Intent(this, BuddyService.class);
        if (BuddyService.isRunning) stopService(intent); else startService(intent);
        updateState();
    }
    private void openOverlaySettings() {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }
    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void updateState() {
        boolean allowed = canDrawOverlays();
        permissionText.setText(allowed ? "الصلاحية مفعّلة ✓" : "امنح التطبيق صلاحية الظهور فوق التطبيقات");
        startButton.setText(BuddyService.isRunning ? "إخفاء Stick Man" : "تشغيل Stick Man");
    }
}
