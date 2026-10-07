package com.example.quickshot;
import android.app.*;import android.content.*;import android.media.projection.MediaProjectionManager;import android.os.*;import android.graphics.Color;import android.view.*;import android.widget.*;
public class MainActivity extends Activity{
 private static final int CAPTURE=42;private MediaProjectionManager projection;
 @Override public void onCreate(Bundle b){super.onCreate(b);projection=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);if(getIntent().getBooleanExtra("quick_tile",false))requestCapture();else buildUi();}
 private void buildUi(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,48,32,24);box.setGravity(Gravity.CENTER);box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);TextView t=new TextView(this);t.setText("لقطة سريعة");t.setTextSize(30);t.setTextColor(Color.rgb(0,130,145));t.setGravity(Gravity.CENTER);box.addView(t,new LinearLayout.LayoutParams(-1,100));TextView i=new TextView(this);i.setText("أضف “لقطة سريعة” من تحرير لوحة الإشعارات بجانب Wi‑Fi والكاميرا.\n\nبعد إضافتها، اضغط الاختصار لالتقاط الشاشة وحفظها في مجلد Screenshot.");i.setTextSize(18);i.setGravity(Gravity.CENTER);box.addView(i);setContentView(box);}
 private void requestCapture(){startActivityForResult(projection.createScreenCaptureIntent(),CAPTURE);}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==CAPTURE&&c==RESULT_OK&&d!=null){Intent s=new Intent(this,ScreenshotService.class);s.putExtra("resultCode",c);s.putExtra("data",d);if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);finish();}}
}
