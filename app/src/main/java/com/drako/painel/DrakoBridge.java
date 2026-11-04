package com.drako.painel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class DrakoBridge {

    private static final String API = "https://keyvault-5qbp3hgj.manus.space/api/venice/v1/validate";
    private static final String[] FF = {"com.dts.freefireth", "com.dts.freefiremax"};

    public static volatile DrakoBridge atual;

    private final Context ctx;
    private final android.webkit.WebView web;
    private final Activity act;
    private final Sh sh;

    public DrakoBridge(Context c, android.webkit.WebView w, Activity a) {
        ctx = c.getApplicationContext();
        web = w;
        act = a;
        sh = new Sh(ctx);
        atual = this;
    }

    private void js(String s) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try { web.evaluateJavascript(s, null); } catch (Exception e) {}
        });
    }

    private SharedPreferences prefs() { return ctx.getSharedPreferences("drako", 0); }

    private String token() {
        SharedPreferences p = prefs();
        String t = p.getString("token", "");
        if (t == null || t.isEmpty()) {
            t = UUID.randomUUID().toString().replace("-", "");
            p.edit().putString("token", t).apply();
        }
        return t;
    }

    private String pacote() {
        for (String p : FF) {
            try { ctx.getPackageManager().getPackageInfo(p, 0); return p; } catch (Exception e) {}
        }
        return "";
    }

    public boolean ffNaFrente() {
        if (!sh.shizukuOk() || !sh.temPermissao()) return true;
        String r = sh.exec("dumpsys window | grep -m1 mCurrentFocus");
        if (r.startsWith("ERRO")) return true;
        return r.contains("com.dts.freefire");
    }

    private boolean respondeuOk(int code, JSONObject o) {
        return code == 200 && o != null
                && (o.optBoolean("valid", false) || o.optBoolean("ok", false) || o.optBoolean("success", false));
    }

    private String post(String corpoJson) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        OutputStream os = c.getOutputStream();
        os.write(corpoJson.getBytes());
        os.flush();
        os.close();
        int code = c.getResponseCode();
        String txt = ler(code < 400 ? c.getInputStream() : c.getErrorStream());
        c.disconnect();
        return code + "|" + txt;
    }

    @JavascriptInterface
    public void pedirEstado() {
        new Thread(() -> {
            try {
                js("DRAKO.onEstado('" + sh.detectar() + "'," + (!pacote().isEmpty()) + "," + Settings.canDrawOverlays(ctx) + ")");
            } catch (Exception e) {}
        }).start();
    }

    @JavascriptInterface
    public void pedirPermissoes() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (!Settings.canDrawOverlays(ctx)) {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + ctx.getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                }
                if (Build.VERSION.SDK_INT >= 33 && act != null)
                    act.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            } catch (Exception e) {}
        });
    }

    @JavascriptInterface
    public void conectar() {
        new Thread(() -> {
            try {
                if (!sh.shizukuOk()) { js("DRAKO.onConectado(false,'SHIZUKU INATIVO. ATIVE PELO APP DO SHIZUKU VIA WI-FI.')"); return; }
                if (!sh.temPermissao()) { sh.pedirPermissao(); js("DRAKO.onConectado(false,'APROVE NO SHIZUKU E TOQUE EM CONECTAR DE NOVO.')"); return; }
                js("DRAKO.onConectado(true,'CONECTADO VIA SHIZUKU')");
            } catch (Exception e) { js("DRAKO.onConectado(false,'FALHA NA CONEXAO')"); }
        }).start();
    }

    @JavascriptInterface
    public void salvarToken(String t) {
        if (t != null && !t.isEmpty()) prefs().edit().putString("token", t).apply();
    }

    @JavascriptInterface
    public void verificarKey(String key) {
        new Thread(() -> {
            try {
                JSONObject corpo = new JSONObject();
                corpo.put("key", key);
                corpo.put("installationToken", token());
                corpo.put("package", ctx.getPackageName());

                String r = post(corpo.toString());
                int code = Integer.parseInt(r.substring(0, r.indexOf('|')));
                String txt = r.substring(r.indexOf('|') + 1);

                JSONObject o = null;
                try { o = new JSONObject(txt); } catch (Exception e) {}

                if (respondeuOk(code, o)) {
                    long exp = o.optLong("expiresAt", 0);
                    if (exp <= 0) exp = System.currentTimeMillis() + 86400000L;
                    String k = o.optString("key", key);
                    prefs().edit().putString("key", k).putLong("expira", exp).apply();
                    js("DRAKO.onKeyResult('" + k.replace("'", "") + "'," + exp + ",'')");
                } else {
                    String msg = "KEY INVALIDA";
                    if (o != null) msg = o.optString("message", o.optString("error", msg));
                    else if (code == 404) msg = "ROTA NAO ENCONTRADA";
                    js("DRAKO.onKeyResult('',0,'" + msg.replace("'", "").toUpperCase() + "')");
                }
            } catch (Exception e) {
                js("DRAKO.onKeyResult('',0,'SEM CONEXAO COM O SERVIDOR')");
            }
        }).start();
    }

    private String ler(InputStream in) {
        if (in == null) return "{}";
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
        } catch (Exception e) {}
        return sb.toString();
    }

    @JavascriptInterface
    public void sessaoOk(String key, long exp) {
        prefs().edit().putString("key", key).putLong("expira", exp).apply();
        new Handler(Looper.getMainLooper()).post(() -> DrakoService.iniciar(ctx));
    }

    public static void revalidarAtual() {
        DrakoBridge b = atual;
        if (b != null) b.revalidar();
    }

    @JavascriptInterface
    public void revalidar() {
        final String k = prefs().getString("key", "");
        if (k.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject corpo = new JSONObject();
                corpo.put("key", k);
                corpo.put("installationToken", token());
                corpo.put("package", ctx.getPackageName());

                String r = post(corpo.toString());
                int code = Integer.parseInt(r.substring(0, r.indexOf('|')));
                String txt = r.substring(r.indexOf('|') + 1);

                JSONObject o = null;
                try { o = new JSONObject(txt); } catch (Exception e) {}

                if (respondeuOk(code, o)) {
                    long exp = o.optLong("expiresAt", System.currentTimeMillis() + 86400000L);
                    prefs().edit().putLong("expira", exp).apply();
                } else {
                    js("DRAKO.onExpirado('KEY REVOGADA OU EXPIRADA')");
                }
            } catch (Exception e) {
                // sem rede: mantem ate o prazo local
            }
        }).start();
    }

    @JavascriptInterface
    public void expirou(String motivo) {
        prefs().edit().remove("expira").apply();
        new Handler(Looper.getMainLooper()).post(() -> {
            try { ctx.stopService(new Intent(ctx, DrakoService.class)); } catch (Exception e) {}
        });
    }

    @JavascriptInterface
    public void sair() {
        prefs().edit().remove("expira").remove("key").apply();
        new Handler(Looper.getMainLooper()).post(() -> {
            try { ctx.stopService(new Intent(ctx, DrakoService.class)); } catch (Exception e) {}
        });
    }

    @JavascriptInterface
    public void setOpt(String nome, boolean on) {
        switch (nome) {
            case "box": EspView.box = on; break;
            case "linha": EspView.linha = on; break;
            case "vida": EspView.vida = on; break;
            case "nome": EspView.nome = on; break;
            case "hs": EspView.hs = on; break;
            case "aimbot": EspView.aimbot = on; break;
            case "fov": EspView.fov = on; break;
        }
        DrakoService.atualizar();
        escreverConfig();
    }

    @JavascriptInterface
    public void setFov(int v) {
        EspView.raio = Math.max(60, Math.min(400, v));
        DrakoService.atualizar();
        escreverConfig();
    }

    @JavascriptInterface
    public void painel(boolean on) {
        DrakoService.painelOn = on;
        DrakoService.avisar();
    }

    @JavascriptInterface
    public void mover(int dx, int dy) {
        DrakoService.mover(dx, dy);
    }

    private void escreverConfig() {
        final String pac = pacote();
        if (pac.isEmpty()) return;
        final String base = "/sdcard/Android/data/" + pac + "/files/";
        final String json = "{\"box\":" + EspView.box + ",\"linha\":" + EspView.linha
                + ",\"vida\":" + EspView.vida + ",\"nome\":" + EspView.nome
                + ",\"hs\":" + EspView.hs + ",\"aimbot\":" + EspView.aimbot
                + ",\"ExibirFov\":" + EspView.fov + ",\"Fov\":" + EspView.raio + "}";
        new Thread(() -> {
            sh.escrever(base + "drako.json", json);
            sh.escrever(base + "DRAKO/config.json", json);
            sh.escrever(base + "menu_config.txt", json);
            sh.escrever("/sdcard/Documents/DRAKO/config.json", json);
        }).start();
    }

    @JavascriptInterface
    public void injetar() {
        new Thread(() -> {
            try {
                String pac = pacote();
                if (pac.isEmpty()) { js("DRAKO.onProgress(0,'FREE FIRE NAO INSTALADO')"); return; }
                String base = "/sdcard/Android/data/" + pac + "/files/";
                js("DRAKO.onProgress(25,'PREPARANDO PASTAS"));
                String[] pastas = {base, base + "DRAKO/", "/sdcard/Documents/DRAKO/"};
                for (String d : pastas) {
                    new File(d).mkdirs();
                    sh.exec("mkdir -p \"" + d + "\"");
                }
                js("DRAKO.onProgress(60,'ESCREVENDO CONFIG'));
                escreverConfig();
                js("DRAKO.onProgress(90,'AJUSTANDO PERMISSOES'));
                for (String d : pastas) sh.exec("chmod -R 777 \"" + d + "\"");
                js("DRAKO.onProgress(100,'INJECAO CONCLUIDA'));
                js("DRAKO.onInjetado(true)");
            } catch (Exception e) {
                js("DRAKO.onInjetado(false)");
            }
        }).start();
    }

    @JavascriptInterface
    public void removerInjecao() {
        new Thread(() -> {
            try {
                String pac = pacote();
                if (pac.isEmpty()) return;
                String base = "/sdcard/Android/data/" + pac + "/files/";
                sh.exec("rm -f \"" + base + "drako.json\"");
                sh.exec("rm -f \"" + base + "menu_config.txt\"");
                sh.exec("rm -rf \"" + base + "DRAKO\"");
                sh.exec("rm -rf \"/sdcard/Documents/DRAKO\"");
                js("DRAKO.onInjetado(false)");
            } catch (Exception e) {}
        }).start();
    }

    @JavascriptInterface
    public void abrirFF() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                String pac = pacote();
                if (pac.isEmpty()) return;
                Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pac);
                if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); }
                DrakoService.painelOn = true;
                DrakoService.avisar();
            } catch (Exception e) {}
        });
    }

    @JavascriptInterface
    public void fecharFF() {
        new Thread(() -> {
            String pac = pacote();
            if (!pac.isEmpty()) sh.exec("am force-stop " + pac);
        }).start();
    }

    @JavascriptInterface
    public void bypass() {
        new Thread(() -> {
            try {
                String pac = pacote();
                if (pac.isEmpty()) { js("DRAKO.onProgress(0,'SEM FREE FIRE')"); return; }
                js("DRAKO.onProgress(25,'LIMPANDO CACHE'));
                sh.exec("rm -rf \"/sdcard/Android/data/" + pac + "/cache\"");
                js("DRAKO.onProgress(65,'RECRIANDO PASTAS'));
                sh.exec("mkdir -p \"/sdcard/Android/data/" + pac + "/files/DRAKO\"");
                sh.exec("chmod -R 777 \"/sdcard/Android/data/" + pac + "/files\"");
                js("DRAKO.onProgress(100,'BYPASS APLICADO'));
            } catch (Exception e) {}
        }).start();
    }

    @JavascriptInterface
    public void diagnostico() {
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("modo: ").append(sh.detectar()).append('\n');
            String pac = pacote();
            sb.append("ff instalado: ").append(pac.isEmpty() ? "NAO" : "SIM").append('\n');
            sb.append("overlay: ").append(Settings.canDrawOverlays(ctx) ? "OK" : "NEGADO").append('\n');
            sb.append("servico: ").append(DrakoService.instancia() != null ? "ATIVO" : "PARADO").append('\n');
            sb.append("token: ").append(token().isEmpty() ? "AUSENTE" : "OK").append('\n');
            sb.append("key salva: ").append(prefs().getString("key", "").isEmpty() ? "NAO" : "SIM").append('\n');
            if (!pac.isEmpty()) {
                String pid = sh.exec("pidof " + pac).trim();
                sb.append("pid: ").append(pid.isEmpty() ? "NAO RODANDO" : pid).append('\n');
                sb.append("files: ").append(new File("/sdcard/Android/data/" + pac + "/files").exists() ? "OK" : "AUSENTE").append('\n');
            }
            sb.append("box=").append(EspView.box).append(" linha=").append(EspView.linha)
              .append(" fov=").append(EspView.fov).append(" raio=").append(EspView.raio).append('\n');
            js("DRAKO.onDiag('" + sb.toString().replace("\n", "\\n") + "')");
        }).start();
    }
}