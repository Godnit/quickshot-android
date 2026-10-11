package com.godnit.nativesoccer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class SoccerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private long lastNanos = System.nanoTime();
    private float playerX = -2f, playerZ = 4f;
    private float ballX = 0f, ballZ = 0f, ballVX = 0f, ballVZ = 0f;
    private float moveX = 0f, moveZ = 0f;
    private float[] homeX = {-8f, -4f, 0f, 5f, 9f};
    private float[] homeZ = {-4f, -6f, -5f, -3f, 0f};
    private float[] awayX = {8f, 4f, 0f, -5f, -9f};
    private float[] awayZ = {4f, 6f, 5f, 3f, 0f};
    private int scoreHome = 0, scoreAway = 0;
    private float matchTime = 180f;
    private boolean paused = false;
    private String pressedAction = "";

    public SoccerView(Context context) {
        super(context);
        setFocusable(true);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
    }

    private float depthScale(float z) {
        return 0.55f + ((z + 9f) / 18f) * 0.50f;
    }

    private float sx(float x, float z, float width) {
        return width * 0.5f + x * depthScale(z) * (width / 28f);
    }

    private float sy(float z, float height) {
        return height * 0.50f + z * depthScale(z) * (height / 23f);
    }

    private void fill(Canvas c, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setAlpha(255);
        c.drawColor(color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float dt = Math.min(0.05f, (System.nanoTime() - lastNanos) / 1_000_000_000f);
        lastNanos = System.nanoTime();
        if (!paused) update(dt);
        drawWorld(canvas);
        postInvalidateDelayed(16);
    }

    private void update(float dt) {
        if (matchTime > 0f) matchTime -= dt;
        if (matchTime <= 0f) {
            matchTime = 0f;
            paused = true;
        }
        playerX += moveX * 5.5f * dt;
        playerZ += moveZ * 5.5f * dt;
        playerX = clamp(playerX, -12.5f, 12.5f);
        playerZ = clamp(playerZ, -7.7f, 7.7f);

        for (int i = 0; i < awayX.length; i++) {
            float dx = ballX - awayX[i];
            float dz = ballZ - awayZ[i];
            float length = Math.max(0.01f, (float) Math.sqrt(dx * dx + dz * dz));
            awayX[i] += dx / length * 1.1f * dt;
            awayZ[i] += dz / length * 1.1f * dt;
        }
        ballX += ballVX * dt;
        ballZ += ballVZ * dt;
        ballVX *= Math.pow(0.16, dt);
        ballVZ *= Math.pow(0.16, dt);

        float dx = ballX - playerX;
        float dz = ballZ - playerZ;
        if (Math.sqrt(dx * dx + dz * dz) < 1.0 && moveX != 0f || moveZ != 0f) {
            ballVX += moveX * 0.8f * dt;
            ballVZ += moveZ * 0.8f * dt;
        }
        if (ballX > 14.2f) {
            scoreHome++;
            resetBall();
        } else if (ballX < -14.2f) {
            scoreAway++;
            resetBall();
        }
    }

    private float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    private void resetBall() {
        ballX = 0f;
        ballZ = 0f;
        ballVX = 0f;
        ballVZ = 0f;
    }

    private void drawWorld(Canvas c) {
        float w = getWidth(), h = getHeight();
        fill(c, Color.rgb(102, 174, 222));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(25, 133, 84));
        path.reset();
        path.moveTo(sx(-14f, -9f, w), sy(-9f, h));
        path.lineTo(sx(14f, -9f, w), sy(-9f, h));
        path.lineTo(sx(14f, 9f, w), sy(9f, h));
        path.lineTo(sx(-14f, 9f, w), sy(9f, h));
        path.close();
        c.drawPath(path, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.WHITE);
        c.drawPath(path, paint);
        c.drawLine(sx(-14f, 0f, w), sy(0f, h), sx(14f, 0f, w), sy(0f, h), paint);
        c.drawCircle(sx(0f, 0f, w), sy(0f, h), 2.5f * depthScale(0f) * (w / 28f), paint);
        c.drawRect(sx(-6f, -9f, w), sy(-9f, h), sx(6f, -4f, w), sy(-4f, h), paint);
        c.drawRect(sx(-6f, 4f, w), sy(4f, h), sx(6f, 9f, w), sy(9f, h), paint);

        for (int i = 0; i < homeX.length; i++) drawPlayer(c, homeX[i], homeZ[i], Color.rgb(30, 108, 255), w, h);
        for (int i = 0; i < awayX.length; i++) drawPlayer(c, awayX[i], awayZ[i], Color.rgb(235, 66, 66), w, h);
        drawPlayer(c, playerX, playerZ, Color.rgb(30, 108, 255), w, h);
        drawBall(c, w, h);
        drawHud(c, w, h);
        if (paused) drawPause(c, w, h);
    }

    private void drawPlayer(Canvas c, float x, float z, int color, float w, float h) {
        float scale = depthScale(z) * (w / 28f);
        float xPos = sx(x, z, w), yPos = sy(z, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(80, 0, 0, 0));
        c.drawOval(new RectF(xPos - 0.42f * scale, yPos + 0.28f * scale,
                xPos + 0.42f * scale, yPos + 0.48f * scale), paint);
        paint.setColor(color);
        c.drawCircle(xPos, yPos, 0.42f * scale, paint);
        paint.setColor(Color.rgb(247, 207, 160));
        c.drawCircle(xPos, yPos - 0.56f * scale, 0.25f * scale, paint);
    }

    private void drawBall(Canvas c, float w, float h) {
        float scale = depthScale(ballZ) * (w / 28f);
        float xPos = sx(ballX, ballZ, w), yPos = sy(ballZ, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(70, 0, 0, 0));
        c.drawOval(new RectF(xPos - 0.25f * scale, yPos + 0.15f * scale,
                xPos + 0.25f * scale, yPos + 0.30f * scale), paint);
        paint.setColor(Color.WHITE);
        c.drawCircle(xPos, yPos, 0.22f * scale, paint);
    }

    private void drawHud(Canvas c, float w, float h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(26f);
        c.drawText("BLUE  " + scoreHome + " - " + scoreAway + "  RED", 28f, 38f, paint);
        int seconds = (int) matchTime;
        c.drawText(String.format("%02d:%02d", seconds / 60, seconds % 60), 28f, 70f, paint);
        drawButton(c, w - 170f, 20f, w - 20f, 68f, "SETTINGS");
        drawButton(c, w - 190f, h - 92f, w - 20f, h - 22f, "SHOOT");
        drawButton(c, w - 385f, h - 92f, w - 215f, h - 22f, "PASS");
        drawButton(c, w - 580f, h - 92f, w - 395f, h - 22f, "TACKLE");
        drawDpad(c, h);
    }

    private void drawButton(Canvas c, float left, float top, float right, float bottom, String label) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 8, 34, 52));
        c.drawRoundRect(new RectF(left, top, right, bottom), 12f, 12f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.WHITE);
        c.drawRoundRect(new RectF(left, top, right, bottom), 12f, 12f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(16f);
        float tw = paint.measureText(label);
        c.drawText(label, (left + right - tw) / 2f, (top + bottom) / 2f + 6f, paint);
    }

    private void drawDpad(Canvas c, float h) {
        float y = h - 150f;
        drawButton(c, 120f, y, 190f, y + 60f, "^");
        drawButton(c, 45f, y + 65f, 115f, y + 125f, "<");
        drawButton(c, 195f, y + 65f, 265f, y + 125f, ">");
        drawButton(c, 120f, y + 130f, 190f, y + 190f, "v");
    }

    private void drawPause(Canvas c, float w, float h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(210, 0, 0, 0));
        c.drawRect(0, 0, w, h, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(34f);
        c.drawText("SOCCER 3D NATIVE", w / 2f - 165f, h / 2f - 35f, paint);
        paint.setTextSize(20f);
        c.drawText("Tap SETTINGS to resume", w / 2f - 120f, h / 2f + 10f, paint);
    }

    private void action(String name) {
        if (name.equals("settings")) {
            paused = !paused;
            return;
        }
        if (paused) return;
        float dx = ballX - playerX, dz = ballZ - playerZ;
        float length = Math.max(0.1f, (float) Math.sqrt(dx * dx + dz * dz));
        if (name.equals("shoot")) {
            ballVX = dx / length * 10f;
            ballVZ = dz / length * 10f;
        } else if (name.equals("pass")) {
            ballVX = dx / length * 6f;
            ballVZ = dz / length * 6f;
        } else if (name.equals("tackle")) {
            playerX += moveX * 1.0f;
            playerZ += moveZ * 1.0f;
        }
    }

    private String hitAction(float x, float y, float w, float h) {
        if (x > w - 185f && y > h - 110f) return "shoot";
        if (x > w - 390f && x < w - 205f && y > h - 110f) return "pass";
        if (x > w - 585f && x < w - 390f && y > h - 110f) return "tackle";
        if (x > w - 180f && y < 100f) return "settings";
        return "";
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY(), w = getWidth(), h = getHeight();
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            if (x < 300f && y > h - 210f) {
                float cy = h - 120f;
                if (y < cy - 20f) { moveX = 0f; moveZ = -1f; }
                else if (y > cy + 55f) { moveX = 0f; moveZ = 1f; }
                else if (x < 135f) { moveX = -1f; moveZ = 0f; }
                else if (x > 175f) { moveX = 1f; moveZ = 0f; }
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            moveX = 0f;
            moveZ = 0f;
            String action = hitAction(x, y, w, h);
            if (!action.isEmpty()) action(action);
            return true;
        }
        return true;
    }
}
