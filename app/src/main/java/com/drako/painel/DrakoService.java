package com.drako.painel;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class DrakoService extends Service {

    public static volatile boolean painelOn = true;
    private static DrakoService inst;

    public static DrakoService instancia() { return inst; }

    public static void iniciar(Context c) {
        try {
            Intent i = new Intent(c, DrakoService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception e) {}
    }

    public static void avisar() {
        DrakoService s = inst;
        if (s != null) new Handler(Looper.getMainLooper()).post(s::montar);
    }

    public static void atualizar() {
        DrakoService s = inst;
        if (s != null && s.esp != null) new Handler(Looper.getMainLooper()).post(() -> s.esp.invalidate());
    }

    public static void mover(int dx, int dy) {
        DrakoService s = inst;
        if (s == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                s.lw.x = Math.max(0, s.lw.x + dx);
                s.lw.y = Math.max(0, s.lw.y + dy);
                s.wm.updateViewLayout(s.web, s.lw);
            } catch (Exception e) {}
        });
    }

    private WindowManager wm;
    private WebView web;
    private EspView esp;
    private WindowManager.LayoutParams lw;
    private boolean add = false;
    private int ciclo = 0;
    private final Handler vigia = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                String canal = "drako";
                NotificationChannel ch = new NotificationChannel(canal, "DRAKO", NotificationManager.IMPORTANCE_LOW);
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(ch);
                startForeground(77, new Notification.Builder(this, canal)
                        .setContentTitle("PROXY MENU DRAKO")
                        .setContentText("Painel ativo")
                        .setSmallIcon(android.R.drawable.ic_menu_compass)
                        .build());
            } else {
                startForeground(77, new Notification.Builder(this)
                        .setContentTitle("PROXY MENU DRAKO")
                        .setContentText("Painel ativo")
                        .setSmallIcon(android.R.drawable.ic_menu_compass)
                        .build());
            }
        } catch (Exception e) {}
        montar();
        vigia.postDelayed(checar, 2000);
    }

    private final Runnable checar = new Runnable() {
        @Override public void run() {
            try {
                long exp = getSharedPreferences("drako", 0).getLong("expira", 0);
                if (exp > 0 && System.currentTimeMillis() > exp) {
                    if (web != null) web.evaluateJavascript("DRAKO.onExpirado('KEY EXPIRADA')", null);
                    getSharedPreferences("drako", 0).edit().remove("expira").apply();
                } else {
                    DrakoBridge b = DrakoBridge.atual;
                    if (b != null) EspView.ativo = b.ffNaFrente();
                    if (++ciclo >= 30) {
                        ciclo = 0;
                        if (exp > 0) DrakoBridge.revalidarAtual();
                    }
                }
            } catch (Exception e) {}
            vigia.postDelayed(this, 2000);
        }
    };

    private void montar() {
        try {
            if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm == null) return;
            int tipo = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            if (!add) {
                esp = new EspView(this);
                WindowManager.LayoutParams le = new WindowManager.LayoutParams(
                        -1, -1, tipo,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                le.gravity = Gravity.TOP | Gravity.START;
                wm.addView(esp, le);

                web = new WebView(this);
                web.getSettings().setJavaScriptEnabled(true);
                web.getSettings().setDomStorageEnabled(true);
                web.setBackgroundColor(0);
                web.setWebViewClient(new WebViewClient());
                web.addJavascriptInterface(new DrakoBridge(this, web, null), "N");
                web.loadUrl("file:///android_asset/overlay.html");

                int larg = (int) (320 * getResources().getDisplayMetrics().density);
                lw = new WindowManager.LayoutParams(
                        larg, WindowManager.LayoutParams.WRAP_CONTENT, tipo,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                lw.gravity = Gravity.TOP | Gravity.START;
                lw.x = 24;
                lw.y = 180;
                wm.addView(web, lw);
                add = true;
            }
            if (web != null) web.setVisibility(painelOn ? WebView.VISIBLE : WebView.GONE);
        } catch (Exception e) {}
    }

    @Override
    public int onStartCommand(Intent i, int f, int s) { return START_STICKY; }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        inst = null;
        vigia.removeCallbacksAndMessages(null);
        EspView.ativo = true;
        try { if (esp != null) esp.parar(); } catch (Exception e) {}
        try { if (wm != null && web != null && add) wm.removeView(web); } catch (Exception e) {}
        try { if (wm != null && esp != null && add) wm.removeView(esp); } catch (Exception e) {}
        add = false;
        try { if (web != null) web.destroy(); } catch (Exception e) {}
        super.onDestroy();
    }
}