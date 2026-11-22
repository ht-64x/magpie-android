package ai.magpie.lite;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The console page talks to Android through this: gateway on/off, CLI runs,
 * clipboard, and the agents installed on the phone.
 */
public class HostBridge {

    private final Activity act;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StringBuilder out = new StringBuilder();
    private volatile boolean running;

    public HostBridge(Activity act) {
        this.act = act;
    }

    @JavascriptInterface
    public String state() {
        CoreRunner core = CoreRunner.get(act);
        try {
            JSONObject o = new JSONObject();
            o.put("running", core.isRunning());
            o.put("label", core.state());
            o.put("detail", core.detail());
            o.put("gatewayPort", CoreRunner.get(act).port());
            o.put("lan", CoreRunner.get(act).lan());
            o.put("webPort", CoreRunner.WEB_PORT);
            o.put("base", core.apiBase());
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void start() {
        GatewayService.start(act);
    }

    @JavascriptInterface
    public String settings() {
        CoreRunner c = CoreRunner.get(act);
        try {
            JSONObject o = new JSONObject();
            o.put("port", c.port());
            o.put("lan", c.lan());
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void setPort(int port) {
        if (port < 1024 || port > 65535) return;
        CoreRunner.get(act).setPort(port);
        restartCore();
    }

    @JavascriptInterface
    public void setLan(boolean on) {
        CoreRunner.get(act).setLan(on);
        restartCore();
    }

    private void restartCore() {
        CoreRunner.get(act).stop();
        GatewayService.start(act);
    }

    @JavascriptInterface
    public void stop() {
        GatewayService.stop(act);
    }

    @JavascriptInterface
    public void execAsync(String jsonArgs) {
        if (running) return;
        running = true;
        synchronized (out) { out.setLength(0); }
        final String[] args;
        try {
            JSONArray a = new JSONArray(jsonArgs);
            args = new String[a.length()];
            for (int i = 0; i < a.length(); i++) args[i] = a.getString(i);
        } catch (Exception e) {
            running = false;
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                String text = CoreRunner.get(act).exec(args);
                synchronized (out) { out.append(text); }
                running = false;
            }
        }, "magpie-cli").start();
    }

    /** Calls /v1/models with the key exactly as a client would: proof it works. */
    @JavascriptInterface
    public String testKey(String key) {
        java.net.HttpURLConnection c = null;
        try {
            java.net.URL url = new java.net.URL("http://127.0.0.1:" + CoreRunner.get(act).port() + "/v1/models");
            c = (java.net.HttpURLConnection) url.openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(10000);
            c.setRequestProperty("Authorization", "Bearer " + key);
            int code = c.getResponseCode();
            java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            String body = sb.toString();
            int models = 0, i = 0;
            while ((i = body.indexOf("\"id\":", i + 1)) > 0) models++;
            return "{\"code\":" + code + ",\"models\":" + models + "}";
        } catch (Throwable t) {
            String msg = String.valueOf(t.getMessage()).replace("\"", "'");
            return "{\"code\":0,\"error\":\"" + msg + "\"}";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private java.io.File authFile() {
        java.io.File dir = new java.io.File(new java.io.File(new java.io.File(act.getFilesDir(), "home"), ".config"), "magpie");
        dir.mkdirs();
        return new java.io.File(dir, "plugin-auth.json");
    }

    private org.json.JSONObject readAuth() throws Exception {
        java.io.File f = authFile();
        if (!f.isFile()) return new org.json.JSONObject();
        java.util.Scanner sc = new java.util.Scanner(f, "UTF-8").useDelimiter("\\A");
        String s = sc.hasNext() ? sc.next() : "";
        sc.close();
        return s.trim().isEmpty() ? new org.json.JSONObject() : new org.json.JSONObject(s);
    }

    private void writeAuth(org.json.JSONObject o) throws Exception {
        java.io.File f = authFile();
        java.io.File bak = new java.io.File(f.getParentFile(), "plugin-auth.json.bak");
        if (f.isFile()) {
            java.nio.file.Files.copy(f.toPath(), bak.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        java.io.FileWriter w = new java.io.FileWriter(f, false);
        w.write(o.toString(2));
        w.close();
        restartCore();
    }

    /** Accounts of WorkBuddy in file order: id, masked name. */
    @JavascriptInterface
    public String wbAccounts() {
        JSONArray arr = new JSONArray();
        try {
            org.json.JSONObject all = readAuth();
            java.util.Iterator<String> it = all.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (!k.startsWith("workbuddy")) continue;
                org.json.JSONObject v = all.optJSONObject(k);
                String who = "";
                if (v != null) {
                    who = v.optString("email");
                    if (who.isEmpty()) who = v.optString("nickname");
                    if (who.isEmpty()) who = jwtClaim(v.optString("refresh"));
                    if (who.isEmpty()) who = jwtClaim(v.optString("access"));
                    if (who.isEmpty()) who = v.optString("uid");
                    if (who.isEmpty()) who = v.optString("accountId");
                }
                JSONObject o = new JSONObject();
                o.put("key", k);
                o.put("who", who);
                o.put("primary", k.equals("workbuddy"));
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        return arr.toString();
    }

    /** nickname / email out of a JWT, for a readable account label. */
    private String jwtClaim(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "";
            byte[] raw = android.util.Base64.decode(parts[1],
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
            org.json.JSONObject c = new org.json.JSONObject(new String(raw, "UTF-8"));
            for (String k : new String[] { "email", "nickname", "name", "preferred_username", "sub" }) {
                String v = c.optString(k);
                if (!v.isEmpty() && !v.contains("@")) return v;
                if (!v.isEmpty()) return v;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    @JavascriptInterface
    public void wbRemove(String key) {
        try {
            org.json.JSONObject all = readAuth();
            all.remove(key);
            writeAuth(all);
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void wbMove(String key, int delta) {
        try {
            org.json.JSONObject all = readAuth();
            java.util.List<String> keys = new java.util.ArrayList<>();
            java.util.Iterator<String> it = all.keys();
            while (it.hasNext()) keys.add(it.next());
            int from = keys.indexOf(key);
            int to = from + delta;
            if (from < 0 || to < 0 || to >= keys.size()) return;
            java.util.Collections.swap(keys, from, to);
            org.json.JSONObject out = new org.json.JSONObject();
            for (String k : keys) out.put(k, all.get(k));
            writeAuth(out);
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public String autoCheckin() {
        try {
            JSONObject o = new JSONObject();
            o.put("on", CheckinScheduler.enabled(act));
            o.put("hour", CheckinScheduler.hour(act));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void setAutoCheckin(boolean on) {
        CheckinScheduler.setEnabled(act, on);
    }

    @JavascriptInterface
    public void setCheckinHour(int hour) {
        CheckinScheduler.setHour(act, hour);
    }

    @JavascriptInterface
    public boolean taskRunning() { return running; }

    @JavascriptInterface
    public String taskOutput() {
        synchronized (out) { return out.toString(); }
    }

    @JavascriptInterface
    public String agents() {
        PackageManager pm = act.getPackageManager();
        JSONArray arr = new JSONArray();
        String[] words = {
            "dsh", "oprit", "deepseek", "kimi", "claude", "codex", "cursor", "copilot",
            "chatgpt", "gemini", "qwen", "ollama", "lm studio", "lmstudio", "openrouter",
            "workbuddy", "codebuddy", "trae", "opencode", "agent", "通义", "豆包", "智谱",
            "文心", "腾讯元宝", "kimi", "魔搭", "modelscope", "perplexity", "poe"
        };
        List<JSONObject> found = new ArrayList<>();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (app.packageName.equals(act.getPackageName())) continue;
            String label = String.valueOf(pm.getApplicationLabel(app));
            String hay = (label + " " + app.packageName).toLowerCase(Locale.ROOT);
            boolean hit = false;
            for (String w : words) {
                if (hay.contains(w)) { hit = true; break; }
            }
            if (!hit) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("pkg", app.packageName);
                o.put("label", label);
                found.add(o);
            } catch (Exception ignored) { }
            if (found.size() >= 40) break;
        }
        Collections.sort(found, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                return a.optString("label").compareToIgnoreCase(b.optString("label"));
            }
        });
        for (JSONObject o : found) arr.put(o);
        return arr.toString();
    }

    @JavascriptInterface
    public void copy(String text) {
        if (text == null) return;
        ClipboardManager cm = act.getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("magpie", text));
    }

    @JavascriptInterface
    public void toast(final String msg) {
        ui.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void openSettings() {
        ui.post(new Runnable() {
            @Override public void run() {
                act.startActivity(new Intent(act, MainActivity.class));
            }
        });
    }
}
