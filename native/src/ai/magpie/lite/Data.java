package ai.magpie.lite;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 直接读 magpie 的配置文件 + 调它的 CLI：不需要额外服务或鉴权。 */
public final class Data {

    public static final class Provider {
        public String id = "";
        public String name = "";
        public String host = "";
        public int models = 0;
        public String account = "";
    }

    public static final class Key {
        public String id = "";
        public String name = "";
        public boolean off = false;
        public String secret = "";
    }

    private static File dir(Context c) {
        return new File(new File(new File(c.getFilesDir(), "home"), ".config"), "magpie");
    }

    private static JSONObject read(Context c, String name) {
        try {
            File f = new File(dir(c), name);
            if (!f.isFile()) return null;
            java.util.Scanner sc = new java.util.Scanner(f, "UTF-8").useDelimiter("\\A");
            String s = sc.hasNext() ? sc.next() : "";
            sc.close();
            return s.trim().isEmpty() ? null : new JSONObject(s);
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<Provider> providers(Context c) {
        List<Provider> out = new ArrayList<>();
        JSONObject o = read(c, "providers.json");
        if (o == null) return out;
        JSONArray arr = o.optJSONArray("providers");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p == null) continue;
            Provider x = new Provider();
            x.id = p.optString("id");
            x.name = p.optString("name", x.id);
            x.host = p.optString("chat", p.optString("anthropic", ""));
            JSONArray ms = p.optJSONArray("models");
            x.models = ms == null ? 0 : ms.length();
            out.add(x);
        }
        return out;
    }

    public static List<Key> keys(Context c) {
        List<Key> out = new ArrayList<>();
        JSONArray arr = readArray(c, "caller-keys.json");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject k = arr.optJSONObject(i);
            if (k == null) continue;
            Key x = new Key();
            x.id = k.optString("id");
            x.name = k.optString("name", x.id);
            x.off = k.optBoolean("off", false);
            x.secret = k.optString("secret", "");
            out.add(x);
        }
        return out;
    }

    /** 顶层数组（caller-keys.json）与对象包裹（providers.json）都吃。 */
    private static JSONArray readArray(Context c, String name) {
        try {
            File f = new File(dir(c), name);
            if (!f.isFile()) return null;
            java.util.Scanner sc = new java.util.Scanner(f, "UTF-8").useDelimiter("\\A");
            String s = sc.hasNext() ? sc.next() : "";
            sc.close();
            s = s.trim();
            if (s.isEmpty()) return null;
            if (s.startsWith("[")) return new JSONArray(s);
            JSONObject o = new JSONObject(s);
            return o.optJSONArray("keys");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 用这把密钥真发一次 /v1/models：返回 "200 · 16 个模型" 或错误。 */
    public static String testKey(Context c, String secret) {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL("http://127.0.0.1:" + CoreRunner.get(c).port() + "/v1/models");
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(8000);
            if (secret != null && !secret.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + secret);
            int code = conn.getResponseCode();
            java.io.InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            String body = sb.toString();
            int n = 0, i = 0;
            while ((i = body.indexOf("\"id\":", i + 1)) > 0) n++;
            return code == 200 ? ("可用 · " + n + " 个模型") : ("HTTP " + code);
        } catch (Throwable t) {
            return "连不上网关";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }


    public static final class Account {
        public String key = "";
        public String name = "";
        public boolean primary = false;
    }

    /** plugin-auth.json：workbuddy、workbuddy#xxxx 每个键是一个账号。 */
    public static List<Account> accounts(Context c) {
        List<Account> out = new ArrayList<>();
        JSONObject o = read(c, "plugin-auth.json");
        if (o == null) return out;
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!k.startsWith("workbuddy")) continue;
            JSONObject v = o.optJSONObject(k);
            Account a = new Account();
            a.key = k;
            a.primary = k.equals("workbuddy");
            a.name = claim(v, "refresh");
            if (a.name.isEmpty()) a.name = claim(v, "access");
            if (a.name.isEmpty()) a.name = a.primary ? "主账号" : k.replace("workbuddy#", "账号 ");
            out.add(a);
        }
        return out;
    }

    private static String claim(JSONObject v, String field) {
        try {
            if (v == null) return "";
            String tok = v.optString(field);
            String[] parts = tok.split("\\.");
            if (parts.length < 2) return "";
            byte[] raw = android.util.Base64.decode(parts[1],
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
            JSONObject c = new JSONObject(new String(raw, "UTF-8"));
            for (String k : new String[] { "email", "nickname", "name", "preferred_username" }) {
                String s = c.optString(k);
                if (!s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static void removeAccount(Context c, String key) {
        try {
            JSONObject all = read(c, "plugin-auth.json");
            if (all == null) return;
            all.remove(key);
            write(c, "plugin-auth.json", all.toString(2));
        } catch (Throwable ignored) {
        }
    }

    /** 设为首选：把它改名成 "workbuddy"，原来的降级为 workbuddy#尾号。 */
    public static void makePrimary(Context c, String key) {
        try {
            JSONObject all = read(c, "plugin-auth.json");
            if (all == null || key.equals("workbuddy")) return;
            Object mine = all.get(key);
            Object old = all.get("workbuddy");
            all.remove(key);
            all.remove("workbuddy");
            String suffix = key.contains("#") ? key.substring(key.indexOf('#') + 1) : "alt";
            all.put("workbuddy", mine);
            if (old != null) all.put("workbuddy#" + suffix, old);
            write(c, "plugin-auth.json", all.toString(2));
        } catch (Throwable ignored) {
        }
    }

    private static void write(Context c, String name, String text) throws Exception {
        File f = new File(dir(c), name);
        File bak = new File(dir(c), name + ".bak");
        if (f.isFile()) java.nio.file.Files.copy(f.toPath(), bak.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        java.io.FileWriter w = new java.io.FileWriter(f, false);
        w.write(text);
        w.close();
    }

    public static String addKey(Context c, String name) {
        String out = CoreRunner.get(c).exec(new String[] { "gateway-key", "add", name });
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("sk-[A-Za-z0-9_\\-]+").matcher(out);
        return m.find() ? m.group() : "";
    }

    public static void removeKey(Context c, String id) {
        CoreRunner.get(c).exec(new String[] { "gateway-key", "remove", id });
    }

    public static void toggleKey(Context c, String id, boolean off) {
        CoreRunner.get(c).exec(new String[] { "gateway-key", off ? "off" : "on", id });
    }
}


