package com.drako.painel;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.util.Random;

public class EspView extends View {

    public static volatile boolean box, linha, vida, nome, hs, aimbot;
    public static volatile boolean fov = true;
    public static volatile boolean ativo = true;
    public static volatile int raio = 140;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Alvo[] alvos = new Alvo[5];
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random r = new Random();
    private boolean vivo = true;

    static class Alvo {
        float bx, by, amp, vel, t, esc;
    }

    public EspView(Context c) {
        super(c);
        p.setStrokeWidth(3);
        p.setTextSize(11 * getResources().getDisplayMetrics().density);
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < alvos.length; i++) { alvos[i] = new Alvo(); nascer(alvos[i], i); }
        h.postDelayed(tick, 40);
    }

    private void nascer(Alvo a, int i) {
        a.bx = 0.18f + i * 0.15f + (r.nextFloat() - 0.5f) * 0.08f;
        a.by = 0.28f + r.nextFloat() * 0.32f;
        a.amp = 0.04f + r.nextFloat() * 0.05f;
        a.vel = 0.6f + r.nextFloat() * 0.9f;
        a.t = r.nextFloat() * 6.28f;
        a.esc = 0.75f + r.nextFloat() * 0.55f;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!vivo) return;
            for (Alvo a : alvos) a.t += 0.035f * a.vel;
            invalidate();
            h.postDelayed(this, 40);
        }
    };

    public void parar() { vivo = false; h.removeCallbacksAndMessages(null); }

    private float px(Alvo a, float w) {
        float x = (a.bx + (float) Math.sin(a.t) * a.amp) * w;
        return Math.max(w * 0.04f, Math.min(w * 0.88f, x));
    }

    private float py(Alvo a, float hh) {
        float y = (a.by + (float) Math.cos(a.t * 0.7f) * a.amp * 0.35f) * hh;
        return Math.max(hh * 0.12f, Math.min(hh * 0.72f, y));
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (!ativo) return;
        float w = getWidth(), hh = getHeight();
        if (w <= 0 || hh <= 0) return;
        float cx = w / 2f, cy = hh / 2f;

        if (fov) {
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xFFDC2626);
            p.setAlpha(190);
            cv.drawCircle(cx, cy, raio, p);
        }

        if (aimbot) {
            fill.setColor(0xFFFF2D2D);
            cv.drawCircle(cx, cy, 7, fill);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xFFFF2D2D);
            p.setAlpha(255);
            cv.drawLine(cx - 24, cy, cx - 10, cy, p);
            cv.drawLine(cx + 10, cy, cx + 24, cy, p);
            cv.drawLine(cx, cy - 24, cx, cy - 10, p);
            cv.drawLine(cx, cy + 10, cx, cy + 24, p);
        }

        if (!box && !linha && !vida && !nome && !hs) return;

        p.setAlpha(255);
        for (Alvo a : alvos) {
            float x = px(a, w), y = py(a, hh);
            float bw = w * 0.075f * a.esc;
            float bh = bw * 2.1f;
            float hp = 0.35f + ((float) Math.sin(a.t * 0.45f) + 1f) * 0.32f;

            if (linha) {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFFDC2626);
                p.setAlpha(150);
                cv.drawLine(cx, hh - 24, x + bw / 2f, y + bh, p);
                p.setAlpha(255);
            }

            if (box) {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFFFF3B3B);
                cv.drawRect(x, y, x + bw, y + bh, p);
            }

            if (vida) {
                fill.setColor(0xFF1A1A1A);
                cv.drawRect(x - 9, y, x - 5, y + bh, fill);
                fill.setColor(hp > 0.6f ? 0xFF22C55E : hp > 0.3f ? 0xFFEAB308 : 0xFFEF4444);
                float vh = bh * hp;
                cv.drawRect(x - 9, y + bh - vh, x - 5, y + bh, fill);
            }

            if (nome) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                cv.drawText("ALVO", x, y - 8, p);
            }

            if (hs) {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFFFFD400);
                cv.drawCircle(x + bw / 2f, y + bh * 0.1f, bw * 0.28f, p);
            }
        }
    }
}