package com.example.quickshot;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int padding = (int)(24 * getResources().getDisplayMetrics().density);
        box.setPadding(padding,padding,padding,padding);
        TextView title = new TextView(this); title.setText("لقطة سريعة"); title.setTextSize(30);
        title.setTextColor(Color.rgb(0,130,145)); title.setGravity(Gravity.CENTER); box.addView(title);
        TextView instructions = new TextView(this);
        instructions.setText("أضف «لقطة شاشة» من تحرير الاختصارات بجانب Wi‑Fi.\n\nاضغط الاختصار: تُغلق اللوحة وتُحفظ الصورة في Pictures/Screenshots.\n\nامنح إذن التقاط الشاشة عند الطلب. يبقى الاختصار جاهزًا ما دامت جلسة الالتقاط تعمل؛ وإذا أوقفها النظام سيطلب الإذن مجددًا.");
        instructions.setTextSize(18); instructions.setGravity(Gravity.CENTER);
        instructions.setPadding(0,padding,0,padding); box.addView(instructions);
        Button capture = new Button(this); capture.setText("تفعيل الالتقاط وتجربة لقطة");
        capture.setOnClickListener(v -> { startActivity(new Intent(this,CaptureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); moveTaskToBack(true); });
        box.addView(capture, new LinearLayout.LayoutParams(-1,-2));
        Button effect = new Button(this); effect.setText("السماح بوميض الأطراف بعد الحفظ (اختياري)");
        effect.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        box.addView(effect,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(box); setContentView(scroll);
    }
}
