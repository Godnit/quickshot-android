package com.example.quickshot;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.*;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Full-screen review screen: save the whole image or drag a rectangle and save only it. */
public class CropActivity extends Activity {
    private SelectionView selection;
    private Bitmap original;
    private File pending;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pending=new File(getCacheDir(),"quickshot_pending.png");
        original=BitmapFactory.decodeFile(pending.getAbsolutePath());
        if(original==null){ Toast.makeText(this,"تعذر فتح اللقطة",Toast.LENGTH_SHORT).show(); finish(); return; }
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(48,48,48)); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER); bar.setPadding(12,10,12,10); bar.setBackgroundColor(Color.argb(225,35,35,35));
        Button full=new Button(this); full.setText("▣  لقطة كاملة"); full.setTextSize(15); full.setTextColor(Color.WHITE); full.setAllCaps(false); full.setBackgroundColor(Color.rgb(0,128,140));
        Button crop=new Button(this); crop.setText("✂  قص الجزء"); crop.setTextSize(15); crop.setTextColor(Color.WHITE); crop.setAllCaps(false); crop.setBackgroundColor(Color.rgb(0,128,140)); crop.setEnabled(false);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(52),1); bp.setMargins(dp(6),0,dp(6),0); bar.addView(full,bp); bar.addView(crop,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(72))); selection=new SelectionView(); root.addView(selection,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=new TextView(this); hint.setText("اسحب حول الجزء المطلوب، أو اختر لقطة كاملة"); hint.setTextColor(Color.WHITE); hint.setTextSize(15); hint.setGravity(Gravity.CENTER); hint.setPadding(8,4,8,8); root.addView(hint,new LinearLayout.LayoutParams(-1,dp(42)));
        full.setOnClickListener(v -> finishWith(original)); crop.setOnClickListener(v -> { Bitmap b=selection.crop(); if(b!=null) finishWith(b); });
        selection.setOnSelectionChanged(has -> crop.setEnabled(has)); setContentView(root);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void finishWith(Bitmap bitmap){
        try { save(bitmap); ScreenshotService.showFlash(this); Toast.makeText(this,"تم حفظ اللقطة في Screenshots",Toast.LENGTH_SHORT).show(); }
        catch(Exception e){ Toast.makeText(this,"تعذر حفظ الصورة",Toast.LENGTH_LONG).show(); }
        finally { if(bitmap!=original) bitmap.recycle(); if(original!=null) original.recycle(); pending.delete(); finish(); }
    }
    private void save(Bitmap bitmap) throws IOException {
        String name="Screenshot_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+".png";
        if(Build.VERSION.SDK_INT>=29){
            ContentValues v=new ContentValues(); v.put(MediaStore.Images.Media.DISPLAY_NAME,name); v.put(MediaStore.Images.Media.MIME_TYPE,"image/png"); v.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/Screenshots"); v.put(MediaStore.Images.Media.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v); if(uri==null) throw new IOException("insert failed");
            try(OutputStream out=getContentResolver().openOutputStream(uri)){ if(out==null || !bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("write failed"); }
            v.clear(); v.put(MediaStore.Images.Media.IS_PENDING,0); getContentResolver().update(uri,v,null,null);
        } else {
            File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Screenshots"); if(!folder.isDirectory()&&!folder.mkdirs()) throw new IOException("mkdir failed");
            File file=new File(folder,name); try(OutputStream out=new FileOutputStream(file)){ if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("write failed"); }
            MediaScannerConnection.scanFile(this,new String[]{file.getAbsolutePath()},new String[]{"image/png"},null);
        }
    }
    private class SelectionView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private float downX,downY; private RectF selected; private OnSelectionChanged callback;
        SelectionView(){super(CropActivity.this); setBackgroundColor(Color.rgb(55,55,55));}
        void setOnSelectionChanged(OnSelectionChanged c){callback=c;}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c); if(original==null)return;
            float scale=Math.min(getWidth()/(float)original.getWidth(),getHeight()/(float)original.getHeight()); float dw=original.getWidth()*scale, dh=original.getHeight()*scale; float left=(getWidth()-dw)/2, top=(getHeight()-dh)/2;
            RectF dest=new RectF(left,top,left+dw,top+dh); paint.setAlpha(255); c.drawBitmap(original,null,dest,paint); paint.setColor(Color.argb(105,0,0,0)); c.drawRect(0,0,getWidth(),getHeight(),paint);
            if(selected!=null){ paint.setColor(Color.argb(48,255,255,255)); c.drawRect(selected,paint); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(Color.WHITE); c.drawRect(selected,paint); paint.setStyle(Paint.Style.FILL); }
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            switch(e.getAction()){case MotionEvent.ACTION_DOWN: downX=e.getX(); downY=e.getY(); selected=new RectF(downX,downY,downX,downY); invalidate(); return true; case MotionEvent.ACTION_MOVE: case MotionEvent.ACTION_UP: selected.set(Math.min(downX,e.getX()),Math.min(downY,e.getY()),Math.max(downX,e.getX()),Math.max(downY,e.getY())); invalidate(); if(callback!=null) callback.changed(selected.width()>dp(24)&&selected.height()>dp(24)); return true;} return true;
        }
        Bitmap crop(){ if(selected==null||selected.width()<dp(24)||selected.height()<dp(24))return null; float scale=Math.min(getWidth()/(float)original.getWidth(),getHeight()/(float)original.getHeight()); float left=(getWidth()-original.getWidth()*scale)/2, top=(getHeight()-original.getHeight()*scale)/2; int l=Math.max(0,Math.round((selected.left-left)/scale)), t=Math.max(0,Math.round((selected.top-top)/scale)); int r=Math.min(original.getWidth(),Math.round((selected.right-left)/scale)), b=Math.min(original.getHeight(),Math.round((selected.bottom-top)/scale)); if(r<=l||b<=t)return null; return Bitmap.createBitmap(original,l,t,r-l,b-t); }
    }
    private interface OnSelectionChanged { void changed(boolean has); }
}
