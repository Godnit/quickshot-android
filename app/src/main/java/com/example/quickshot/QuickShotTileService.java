package com.example.quickshot;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class QuickShotTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setLabel("لقطة شاشة");
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_camera));
            tile.setState(Tile.STATE_ACTIVE); tile.updateTile();
        }
    }
    @Override public void onClick() {
        super.onClick();
        unlockAndRun(() -> {
            Intent intent = new Intent(this,CaptureActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this,42,intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            } else startActivityAndCollapse(intent);
        });
    }
}
