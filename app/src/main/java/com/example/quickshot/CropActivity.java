package com.example.quickshot;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Full-screen selector with compact top controls and a camera action in the selection. */
public class CropActivity extends Activity {
    private SelectionView selection; private Bitmap original; private File pending;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        pending=new File(getCacheDir(),"quickshot_pending.png"); original=BitmapFactory.decodeFile(pending.getAbsolutePath());
        if(original==null){ Toast.makeText(this,"تعذر فتح اللقطة",Toast.LENGTH_SHORT).show(); finish(); return; }
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        selection=new SelectionView(); root.addView(selection,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout tools=new LinearLayout(this); tools.setOrientation(LinearLayout.HORIZONTAL); tools.setGravity(Gravity.CENTER); tools.setPadding(dp(4),dp(4),dp(4),dp(4));
        Button full=smallButton("▣","لقطة كاملة"); Button cancel=smallButton("×","إلغاء");
        tools.addView(full,new LinearLayout.LayoutParams(dp(42),dp(42))); tools.addView(cancel,new LinearLayout.LayoutParams(dp(42),dp(42)));
        FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.CENTER_HORIZONTAL); top.topMargin=dp(6); root.addView(tools,top);
        TextView hint=new TextView(this); hint.setText("اسحب حول الجزء المطلوب ثم اضغط الكاميرا في الوسط"); hint.setTextColor(Color.WHITE); hint.setTextSize(14); hint.setGravity(Gravity.CENTER); hint.setPadding(dp(12),dp(5),dp(12),dp(5)); hint.setBackgroundColor(Color.argb(190,25,25,25));
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,dp(40),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL); hp.bottomMargin=dp(10); root.addView(hint,hp);
        full.setOnClickListener(v -> finishWith(original)); cancel.setOnClickListener(v -> closeWithoutSave());
        selection.setOnSelectionChanged(has -> hint.setText(has ? "اضغط زر الكاميرا داخل التحديد للحفظ" : "اسحب حول الجزء المطلوب ثم اضغط الكاميرا في الوسط"));
        setContentView(root);
    }
    private Button smallButton(String icon,String description){ Button b=new Button(this); b.setText(icon); b.setTextSize(19); b.setTextColor(Color.WHITE); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setAllCaps(false); b.setContentDescription(description); GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.argb(72,0,0,0)); bg.setStroke(dp(1),Color.argb(150,255,255,255)); bg.setCornerRadius(dp(8)); b.setBackground(bg); return b; }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    void saveSelection(){ Bitmap b=selection.crop(); if(b!=null) finishWith(b); }
    private void closeWithoutSave(){ if(original!=null)original.recycle(); pending.delete(); finish(); }
    private void finishWith(Bitmap bitmap){ try { save(bitmap); ScreenshotService.showFlash(this); Toast.makeText(this,"تم حفظ اللقطة في Screenshots",Toast.LENGTH_SHORT).show(); } catch(Exception e){ Toast.makeText(this,"تعذر حفظ الصورة",Toast.LENGTH_LONG).show(); } finally { if(bitmap!=original)bitmap.recycle(); if(original!=null)original.recycle(); pending.delete(); finish(); } }
    private void save(Bitmap bitmap)throws IOException{
        String name="Screenshot_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+".png";
        if(Build.VERSION.SDK_INT>=29){ ContentValues v=new ContentValues(); v.put(MediaStore.Images.Media.DISPLAY_NAME,name); v.put(MediaStore.Images.Media.MIME_TYPE,"image/png"); v.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/Screenshots"); v.put(MediaStore.Images.Media.IS_PENDING,1); Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v); if(u==null)throw new IOException("insert failed"); try(OutputStream out=getContentResolver().openOutputStream(u)){if(out==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("write failed");} v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,v,null,null); }
        else { File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Screenshots");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("mkdir failed");File f=new File(folder,name);try(OutputStream out=new FileOutputStream(f)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("write failed");}MediaScannerConnection.scanFile(this,new String[]{f.getAbsolutePath()},new String[]{"image/png"},null); }
    }
    private class SelectionView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private float downX,downY; private RectF selected; private boolean cameraPressed; private OnSelectionChanged callback;
        SelectionView(){super(CropActivity.this);setBackgroundColor(Color.BLACK);}
        void setOnSelectionChanged(OnSelectionChanged c){callback=c;}
        @Override protected void onDraw(Canvas c){ super.onDraw(c); if(original==null)return; float scale=Math.max(getWidth()/(float)original.getWidth(),getHeight()/(float)original.getHeight()); float dw=original.getWidth()*scale,dh=original.getHeight()*scale,left=(getWidth()-dw)/2,top=(getHeight()-dh)/2; RectF dest=new RectF(left,top,left+dw,top+dh);paint.setAlpha(255);c.drawBitmap(original,null,dest,paint);paint.setColor(Color.argb(110,0,0,0));c.drawRect(0,0,getWidth(),getHeight(),paint);
            if(selected!=null){paint.setColor(Color.argb(38,255,255,255));c.drawRect(selected,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(Color.WHITE);c.drawRect(selected,paint);paint.setStyle(Paint.Style.FILL);drawCamera(c,selected.centerX(),selected.centerY());}
        }
        private void drawCamera(Canvas c,float x,float y){float r=dp(21);paint.setColor(Color.argb(82,0,150,165));c.drawCircle(x,y,r,paint);paint.setColor(Color.argb(225,255,255,255));RectF body=new RectF(x-dp(10),y-dp(7),x+dp(10),y+dp(8));c.drawRoundRect(body,dp(3),dp(3),paint);Path bump=new Path();bump.moveTo(x-dp(6),y-dp(7));bump.lineTo(x-dp(3),y-dp(11));bump.lineTo(x+dp(3),y-dp(11));bump.lineTo(x+dp(6),y-dp(7));bump.close();c.drawPath(bump,paint);paint.setColor(Color.argb(150,0,128,140));c.drawCircle(x,y,dp(4),paint);}
        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getAction()){
                case MotionEvent.ACTION_DOWN:
                    if(selected!=null && selected.width()>dp(24) && selected.height()>dp(24) && Math.hypot(e.getX()-selected.centerX(),e.getY()-selected.centerY())<dp(34)){ cameraPressed=true; return true; }
                    cameraPressed=false; downX=e.getX(); downY=e.getY(); selected=new RectF(downX,downY,downX,downY); invalidate(); return true;
                case MotionEvent.ACTION_MOVE:
                    if(cameraPressed)return true;
                    selected.set(Math.min(downX,e.getX()),Math.min(downY,e.getY()),Math.max(downX,e.getX()),Math.max(downY,e.getY())); invalidate(); return true;
                case MotionEvent.ACTION_UP:
                    if(cameraPressed){ cameraPressed=false; saveSelection(); return true; }
                    selected.set(Math.min(downX,e.getX()),Math.min(downY,e.getY()),Math.max(downX,e.getX()),Math.max(downY,e.getY())); invalidate(); boolean has=selected.width()>dp(24)&&selected.height()>dp(24); if(callback!=null)callback.changed(has); return true;
            }
            return true;
        }
        Bitmap crop(){if(selected==null||selected.width()<dp(24)||selected.height()<dp(24))return null;float scale=Math.max(getWidth()/(float)original.getWidth(),getHeight()/(float)original.getHeight());float left=(getWidth()-original.getWidth()*scale)/2,top=(getHeight()-original.getHeight()*scale)/2;int l=Math.max(0,Math.round((selected.left-left)/scale)),t=Math.max(0,Math.round((selected.top-top)/scale)),r=Math.min(original.getWidth(),Math.round((selected.right-left)/scale)),b=Math.min(original.getHeight(),Math.round((selected.bottom-top)/scale));if(r<=l||b<=t)return null;return Bitmap.createBitmap(original,l,t,r-l,b-t);}
    }
    private interface OnSelectionChanged{void changed(boolean has);}
}
