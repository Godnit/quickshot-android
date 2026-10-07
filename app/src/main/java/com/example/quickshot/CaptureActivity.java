package com.example.quickshot;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

/** Invisible bridge: collapse Quick Settings, obtain consent if needed, then return. */
public class CaptureActivity extends Activity {
    private static final int STORAGE = 41, CAPTURE = 42;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        if (state == null) begin();
    }
    private void begin() {
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE);
            return;
        }
        if (ScreenshotService.isReady()) {
            startService(new Intent(this, ScreenshotService.class).setAction(ScreenshotService.ACTION_CAPTURE));
            close();
        } else {
            MediaProjectionManager manager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent consent = Build.VERSION.SDK_INT >= 34
                ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                : manager.createScreenCaptureIntent();
            startActivityForResult(consent, CAPTURE);
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == STORAGE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) begin();
            else { Toast.makeText(this, "يلزم إذن التخزين لحفظ الصور على هذا الجهاز", Toast.LENGTH_LONG).show(); close(); }
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == CAPTURE) {
            if (result == RESULT_OK && data != null) {
                Intent service = new Intent(this, ScreenshotService.class).setAction(ScreenshotService.ACTION_PREPARE);
                service.putExtra("resultCode", result).putExtra("data", data);
                startForegroundService(service);
            } else Toast.makeText(this, "لم يتم منح إذن التقاط الشاشة", Toast.LENGTH_SHORT).show();
            close();
        }
    }
    private void close() { finish(); overridePendingTransition(0, 0); }
}
