package com.sparkle.glass;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private GlassRenderer r;
    private GLSurfaceView gv;
    private float offX, offY;
    private float dens;

    private interface Cb { void on(int v); }

    private static final float[][] TINTS = {
        {0.81f, 0.90f, 1f}, {1f, 1f, 1f}, {1f, 0.80f, 0.85f},
        {0.80f, 1f, 0.88f}, {1f, 0.90f, 0.60f}, {0.75f, 0.70f, 1f}
    };
    private int tintIdx = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dens = getResources().getDisplayMetrics().density;
        r = new GlassRenderer(dens);

        gv = new GLSurfaceView(this);
        gv.setEGLContextClientVersion(2);
        gv.setRenderer(r);
        gv.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        gv.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                offX = r.cx - e.getX();
                offY = r.cy - e.getY();
            } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                r.cx = e.getX() + offX;
                r.cy = e.getY() + offY;
            }
            return true;
        });

        FrameLayout root = new FrameLayout(this);
        root.addView(gv);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        panel.setPadding(pad, pad, pad, pad);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(Color.argb(190, 18, 16, 24));
        bgd.setCornerRadius(dp(20));
        panel.setBackground(bgd);

        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        final Button toggle = btn("Sparkle Glass · লুকাও");
        toggle.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "Sparkle Glass · লুকাও" : "Sparkle Glass · দেখাও");
        });
        panel.addView(toggle);

        slider(body, "ঝাপসা", 40, 10, v -> r.blur = v);
        slider(body, "স্বচ্ছতা", 100, 22, v -> r.opac = v / 100f);
        slider(body, "ঘনত্ব", 66, 30, v -> r.thick = v + 4);
        slider(body, "আকার", 270, 130, v -> r.size = v + 90);
        slider(body, "গতি", 30, 10, v -> r.speed = v / 10f);

        LinearLayout shapes = row();
        shapes.addView(tag("আকৃতি"));
        String[] sn = {"গোল কোণ", "বৃত্ত", "ক্যাপসুল"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            Button x = btn(sn[i]);
            x.setOnClickListener(v -> r.shape = k);
            shapes.addView(x, new LinearLayout.LayoutParams(0, -2, 1));
        }
        body.addView(shapes);

        LinearLayout modes = row();
        modes.addView(tag("অ্যানিমেশন"));
        String[] mn = {"ঝিলিক", "ঢেউ", "বন্ধ"};
        final int[] mv = {1, 2, 0};
        for (int i = 0; i < 3; i++) {
            final int k = mv[i];
            Button x = btn(mn[i]);
            x.setOnClickListener(v -> r.mode = k);
            modes.addView(x, new LinearLayout.LayoutParams(0, -2, 1));
        }
        body.addView(modes);

        LinearLayout tl = row();
        tl.addView(tag("রং"));
        Button tb = btn("রং বদলান");
        tb.setOnClickListener(v -> {
            tintIdx = (tintIdx + 1) % TINTS.length;
            r.tint = TINTS[tintIdx];
        });
        tl.addView(tb, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(tl);

        panel.addView(body);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(8), dp(8), dp(8), dp(20));
        root.addView(panel, lp);
        setContentView(root);
    }

    private int dp(int v) { return Math.round(v * dens); }

    private TextView tag(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.rgb(200, 195, 215));
        t.setTextSize(13);
        t.setMinWidth(dp(76));
        return t;
    }

    private Button btn(String s) {
        Button x = new Button(this);
        x.setText(s);
        x.setAllCaps(false);
        x.setTextSize(13);
        x.setTextColor(Color.WHITE);
        x.setMinHeight(0);
        x.setMinimumHeight(dp(36));
        return x;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private void slider(LinearLayout parent, String name, int max, int init, final Cb cb) {
        LinearLayout l = row();
        l.addView(tag(name));
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(init);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { cb.on(p); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        l.addView(sb, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(l);
        cb.on(init);
    }

    @Override protected void onPause() { super.onPause(); gv.onPause(); }
    @Override protected void onResume() { super.onResume(); gv.onResume(); }
}
