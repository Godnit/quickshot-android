package com.example.quickshot;
import android.content.*;import android.graphics.drawable.Icon;import android.service.quicksettings.Tile;import android.service.quicksettings.TileService;import android.os.Build;
public class QuickShotTileService extends TileService{
 @Override public void onStartListening(){super.onStartListening();Tile t=getQsTile();if(t!=null){t.setLabel("لقطة شاشة");t.setIcon(Icon.createWithResource(this,R.drawable.ic_camera));t.setState(Tile.STATE_ACTIVE);t.updateTile();}}
 @Override public void onClick(){super.onClick();Intent i=new Intent(this,MainActivity.class);i.putExtra("quick_tile",true);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivityAndCollapse(i);}
}
