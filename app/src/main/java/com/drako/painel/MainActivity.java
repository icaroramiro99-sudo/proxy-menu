package com.drako.painel;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new DrakoBridge(this, web, this), "DrakoNative");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    public void onBackPressed() {
        if (DrakoService.instancia() != null) {
            DrakoService.painelOn = !DrakoService.painelOn;
            DrakoService.avisar();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        try {
            if (web != null) {
                web.removeJavascriptInterface("DrakoNative");
                web.destroy();
            }
        } catch (Exception e) {}
        super.onDestroy();
    }
}