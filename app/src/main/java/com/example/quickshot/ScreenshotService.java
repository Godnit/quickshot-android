package com.example.quickshot;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.widget.Toast;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ScreenshotService extends Service {
    public static final String ACTION_PREPARE = "com.example.quickshot.PREPARE";
    public static final String ACTION_CAPTURE = "com.example.quickshot.CAPTURE";
    private static volatile boolean ready;
    public static boolean isReady() { return ready; }
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread; private Handler worker;
    private MediaProjection projection; private ImageReader reader; private VirtualDisplay display;
    private int width,height; private boolean busy,pending;
    private final Runnable timeout = () -> { if (busy) { busy=false; pending=false; message("لم تصل صورة من النظام. أعد المحاولة أو فعّل الالتقاط مجددًا"); } };

    @Override public void onCreate() {
        super.onCreate(); thread = new HandlerThread("ScreenshotWriter"); thread.start(); worker = new Handler(thread.getLooper());
        NotificationChannel channel = new NotificationChannel("capture","لقطة سريعة",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
    private void foreground() {
        Intent stop = new Intent(this,ScreenshotService.class).setAction("stop");
        PendingIntent stopIntent=PendingIntent.getService(this,0,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this,"capture")
            .setSmallIcon(R.drawable.ic_camera).setContentTitle("لقطة سريعة جاهزة")
            .setContentText("اضغط اختصار لقطة شاشة بجانب Wi‑Fi").setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"إيقاف",stopIntent).build()).build();
        if(Build.VERSION.SDK_INT>=29) startForeground(10,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(10,notification);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent==null || "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(ACTION_PREPARE.equals(intent.getAction()) && intent.hasExtra("data")) {
            foreground();
            worker.post(() -> {
                try {
                    MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
                    projection=manager.getMediaProjection(intent.getIntExtra("resultCode",0),(Intent)intent.getParcelableExtra("data"));
                    if(projection==null) throw new IllegalStateException("No projection");
                    projection.registerCallback(new MediaProjection.Callback(){
                        @Override public void onStop(){ ready=false; stopSelf(); }
                        @Override public void onCapturedContentResize(int w,int h){ if(display!=null && (w!=width || h!=height)) resize(w,h); }
                    },worker);
                    ensureDisplay(); ready=true; requestCapture();
                } catch(Exception error){ Log.e("QuickShot","Prepare failed",error); message("تعذر تفعيل الالتقاط. امنح إذن النظام مجددًا"); stopSelf(); }
            });
        } else if(ACTION_CAPTURE.equals(intent.getAction())) {
            worker.post(() -> { if(ready && projection!=null) requestCapture(); else { message("انتهت جلسة الالتقاط. اضغط الاختصار مجددًا لمنح الإذن"); stopSelf(); } });
        }
        return START_NOT_STICKY;
    }
    private void ensureDisplay() {
        WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        int w,h;
        if(Build.VERSION.SDK_INT>=30) { Rect bounds=wm.getMaximumWindowMetrics().getBounds(); w=bounds.width(); h=bounds.height(); }
        else { DisplayMetrics metrics=new DisplayMetrics(); wm.getDefaultDisplay().getRealMetrics(metrics); w=metrics.widthPixels; h=metrics.heightPixels; }
        if(display==null) {
            width=w; height=h; reader=newReader(w,h);
            display=projection.createVirtualDisplay("QuickShot",w,h,getResources().getConfiguration().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
        } else if(w!=width || h!=height) resize(w,h);
    }
    private ImageReader newReader(int w,int h) {
        ImageReader result=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,3);
        result.setOnImageAvailableListener(this::onFrame,worker); return result;
    }
    private void resize(int w,int h) {
        if(w<=0 || h<=0) return;
        ImageReader previous=reader; reader=newReader(w,h); width=w; height=h;
        display.resize(w,h,getResources().getConfiguration().densityDpi); display.setSurface(reader.getSurface());
        if(previous!=null) previous.close();
    }
    private void requestCapture() {
        if(busy) return; busy=true; pending=false;
        // Wait until the transparent bridge and notification shade have disappeared.
        worker.postDelayed(() -> {
            if(!busy || projection==null) return;
            try { ensureDisplay(); pending=true; resize(width,height);
                worker.postDelayed(this::pollFrame,120); worker.postDelayed(timeout,5000);
            } catch(Exception error) { Log.e("QuickShot","Capture failed",error); busy=false; message("تعذر قراءة الشاشة. أعد تفعيل الالتقاط"); ready=false; stopSelf(); }
        },700);
    }
    private void onFrame(ImageReader source) {
        if(source!=reader) return;
        Image image=null;
        try {
            image=source.acquireLatestImage();
            if(image==null || !pending) return;
            pending=false; worker.removeCallbacks(timeout);
            Bitmap bitmap=copyImage(image); image.close(); image=null;
            try { openCropChooser(bitmap); }
            catch(Exception error) { Log.e("QuickShot","Review failed",error); bitmap.recycle(); message("تعذر فتح شاشة تحديد الجزء"); }
            busy=false;
        } catch(Exception error) { Log.e("QuickShot","Save failed",error); pending=false; busy=false; worker.removeCallbacks(timeout); message("تعذر حفظ الصورة. تحقق من إذن التخزين والمساحة المتاحة"); }
        finally { if(image!=null) image.close(); }
    }
    private void openCropChooser(Bitmap bitmap) throws IOException {
        File pendingFile=new File(getCacheDir(),"quickshot_pending.png");
        try(OutputStream out=new FileOutputStream(pendingFile)) {
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("Pending PNG write failed");
        } finally { bitmap.recycle(); }
        Intent review=new Intent(this,CropActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION);
        main.post(() -> startActivity(review));
    }
    private void pollFrame() { if(pending) { onFrame(reader); if(pending) worker.postDelayed(this::pollFrame,120); } }
    private Bitmap copyImage(Image image) {
        Image.Plane plane=image.getPlanes()[0];
        Bitmap bitmap=Bitmap.createBitmap(image.getWidth(),image.getHeight(),Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(FramePixels.packRgba(plane.getBuffer(),image.getWidth(),image.getHeight(),plane.getRowStride(),plane.getPixelStride()));
        return bitmap;
    }
    private void save(Bitmap bitmap) throws IOException {
        String name="Screenshot_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+".png";
        if(Build.VERSION.SDK_INT>=29) {
            ContentValues values=new ContentValues(); values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/Screenshots");
            values.put(MediaStore.Images.Media.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(uri==null) throw new IOException("MediaStore insert returned null");
            try {
                try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                    if(out==null || !bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("PNG write failed");
                }
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0);
                if(getContentResolver().update(uri,values,null,null)==0) throw new IOException("Publishing failed");
            } catch(Exception error) { getContentResolver().delete(uri,null,null); throw error; }
        } else {
            File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Screenshots");
            if(!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create Screenshots");
            File file=new File(folder,name);
            try(OutputStream out=new FileOutputStream(file)) {
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("PNG write failed");
            } catch(Exception error) { file.delete(); throw error; }
            MediaScannerConnection.scanFile(this,new String[]{file.getAbsolutePath()},new String[]{"image/png"},null);
        }
    }
    private void message(String text) { main.post(() -> Toast.makeText(this,text,Toast.LENGTH_LONG).show()); }
    public static void showFlash(Context context) {
        if(!Settings.canDrawOverlays(context)) return;
        WindowManager wm=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        final float density=context.getResources().getDisplayMetrics().density;
        View effect=new View(this){
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                float edge=30*density;
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(new LinearGradient(0,0,0,edge, new int[]{0x00FFFFFF,0x42FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP)); canvas.drawRect(0,0,getWidth(),edge,paint);
                paint.setShader(new LinearGradient(0,getHeight(),0,getHeight()-edge, new int[]{0x00FFFFFF,0x42FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP)); canvas.drawRect(0,getHeight()-edge,getWidth(),getHeight(),paint);
                paint.setShader(new LinearGradient(0,0,edge,0, new int[]{0x00FFFFFF,0x36FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP)); canvas.drawRect(0,0,edge,getHeight(),paint);
                paint.setShader(new LinearGradient(getWidth(),0,getWidth()-edge,0, new int[]{0x00FFFFFF,0x36FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP)); canvas.drawRect(getWidth()-edge,0,getWidth(),getHeight(),paint);
                paint.setShader(null);
            }
        };
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        try {
            wm.addView(effect,params); effect.setAlpha(.72f);
            effect.animate().alpha(0f).setDuration(560).setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> { try { wm.removeView(effect); } catch(Exception ignored){} }).start();
        } catch(Exception error){ Log.w("QuickShot","Optional flash unavailable",error); }
    }
    @Override public void onDestroy() {
        ready=false;
        worker.post(() -> { worker.removeCallbacksAndMessages(null); pending=false; busy=false;
            if(display!=null) display.release(); if(reader!=null) reader.close();
            if(projection!=null) projection.stop(); thread.quitSafely(); });
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){ return null; }
}
