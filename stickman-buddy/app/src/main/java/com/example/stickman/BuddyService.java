package com.example.stickman;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.Random;

public class BuddyService extends Service {
    public static boolean isRunning = false;
    private WindowManager windowManager;
    private BuddyView buddyView;
    private WindowManager.LayoutParams params;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { stopSelf(); return; }
        isRunning = true;
        startForegroundCompat();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(250, 300, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 22; params.y = 180;
        buddyView = new BuddyView(this);
        windowManager.addView(buddyView, params);
    }

    private void startForegroundCompat() {
        String channelId = "stickman_buddy";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(channelId, "Stick Man Buddy", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channelId) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Stick Man يعمل").setContentText("الشخصية ظاهرة فوق التطبيقات").setOngoing(true);
        startForeground(7, builder.build());
    }

    public void moveBuddy() {
        if (windowManager == null || buddyView == null) return;
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        params.x = 12 + new Random().nextInt(Math.max(1, m.widthPixels - 274));
        params.y = 55 + new Random().nextInt(Math.max(1, m.heightPixels - 370));
        windowManager.updateViewLayout(buddyView, params);
        buddyView.say("طلعت فوق التطبيق 😄", 3);
    }

    @Override public void onDestroy() {
        isRunning = false;
        if (buddyView != null && windowManager != null) { try { windowManager.removeView(buddyView); } catch (Exception ignored) {} }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    private class BuddyView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float downX, downY, startX, startY;
        private boolean moving;
        private String message = "أهلاً! أنا معك 👋";
        private long messageUntil = System.currentTimeMillis() + 3000;
        private final Handler handler = new Handler();

        BuddyView(Context c) { super(c); p.setTypeface(Typeface.DEFAULT_BOLD); setLayerType(View.LAYER_TYPE_SOFTWARE, null); handler.postDelayed(this::invalidate, 3200); }
        void say(String text, int seconds) { message = text; messageUntil = System.currentTimeMillis() + seconds * 1000L; invalidate(); handler.postDelayed(this::invalidate, seconds * 1000L); }
        private void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align) { p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(size); p.setTextAlign(align); c.drawText(s, x, y, p); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (System.currentTimeMillis() < messageUntil) { p.setColor(Color.WHITE); p.setStyle(Paint.Style.FILL); c.drawRoundRect(new RectF(12, 3, 238, 40), 18, 18, p); text(c, message, 125, 27, 14, Color.rgb(18,32,55), Paint.Align.CENTER); }
            p.setColor(0x55000000); c.drawOval(new RectF(86, 238, 170, 252), p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.WHITE);
            c.drawCircle(125, 88, 24, p); c.drawLine(125, 112, 125, 180, p); c.drawLine(125, 126, 91, 169, p); c.drawLine(125, 126, 159, 169, p); c.drawLine(125, 180, 96, 230, p); c.drawLine(125, 180, 154, 230, p);
            p.setStyle(Paint.Style.FILL); c.drawCircle(116, 84, 3, p); c.drawCircle(134, 84, 3, p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); c.drawArc(new RectF(115, 88, 135, 103), 20, 140, false, p);
            button(c, 8, 260, 78, 292, 0xff2e7dd2, "👍"); button(c, 86, 260, 164, 292, 0xffd7772c, "🤝"); button(c, 172, 260, 242, 292, 0xff19966e, "↗");
        }
        private void button(Canvas c, float l, float t, float rr, float b, int color, String label) { p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawRoundRect(new RectF(l,t,rr,b), 12, 12, p); text(c, label, (l+rr)/2, t+22, 18, Color.WHITE, Paint.Align.CENTER); }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downX=e.getRawX(); downY=e.getRawY(); startX=params.x; startY=params.y; moving=false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getRawX()-downX, dy=e.getRawY()-downY;
                    if (Math.abs(dx)>6 || Math.abs(dy)>6) moving=true;
                    if (moving) { params.x=(int)(startX+dx); params.y=(int)(startY+dy); windowManager.updateViewLayout(buddyView, params); }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moving) {
                        float x=e.getX(), y=e.getY();
                        if (y >= 252) { if (x<86) say("طيب 👍", 3); else if (x<171) say("مصافحة 🤝", 3); else moveBuddy(); }
                        else if (y>50 && y<245) say("لمستني! 👋", 3);
                    }
                    return true;
            }
            return true;
        }
    }
}
