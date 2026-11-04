package com.drako.painel;

import android.content.Context;
import java.io.*;
import java.lang.reflect.Method;

public class Sh {

    public static final String OFF = "off";
    public static final String SHZ = "shizuku";
    public static final String ARQ = "arquivos";

    private final Context ctx;

    public Sh(Context c) { ctx = c.getApplicationContext(); }

    public String detectar() { return shizukuOk() ? SHZ : ARQ; }

    public boolean shizukuOk() {
        try {
            Class<?> c = Class.forName("moe.shizuku.api.Shizuku");
            return (boolean) c.getMethod("pingBinder").invoke(null);
        } catch (Throwable t) { return false; }
    }

    public boolean temPermissao() {
        try {
            Class<?> c = Class.forName("moe.shizuku.api.Shizuku");
            int r = (int) c.getMethod("checkSelfPermission").invoke(null);
            return r == 0;
        } catch (Throwable t) { return false; }
    }

    public void pedirPermissao() {
        try {
            Class<?> c = Class.forName("moe.shizuku.api.Shizuku");
            Method m = null;
            for (Method x : c.getMethods())
                if (x.getName().equals("requestPermission") && x.getParameterCount() == 1) { m = x; break; }
            if (m != null) m.invoke(null, 0);
        } catch (Throwable t) {}
    }

    public String exec(String cmd) {
        if (!shizukuOk() || !temPermissao()) return "ERRO: sem shizuku";
        Process p = null;
        try {
            Class<?> c = Class.forName("moe.shizuku.api.Shizuku");
            Method m = c.getMethod("newProcess", String[].class, String[].class, String.class);
            p = (Process) m.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
            String out = ler(p.getInputStream());
            String err = ler(p.getErrorStream());
            p.waitFor();
            return out + err;
        } catch (Throwable t) {
            return "ERRO: " + t;
        } finally {
            if (p != null) try { p.destroy(); } catch (Exception e) {}
        }
    }

    private String ler(InputStream in) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String l;
            while ((l = br.readLine()) != null) sb.append(l).append('\n');
        } catch (Exception e) {}
        return sb.toString();
    }

    public boolean escrever(String caminho, String conteudo) {
        try {
            File f = new File(caminho);
            File d = f.getParentFile();
            if (d != null && !d.exists()) d.mkdirs();
            FileOutputStream o = new FileOutputStream(f);
            o.write(conteudo.getBytes());
            o.flush();
            o.close();
            if (shizukuOk() && temPermissao()) exec("chmod 666 \"" + caminho + "\"");
            return true;
        } catch (Exception e) { return false; }
    }
}