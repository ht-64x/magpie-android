package ai.magpie.app;

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
        JSONObject o = read(c, "caller-keys.json");
        if (o == null) return out;
        JSONArray arr = o.optJSONArray("keys");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject k = arr.optJSONObject(i);
            if (k == null) continue;
            Key x = new Key();
            x.id = k.optString("id");
            x.name = k.optString("name", x.id);
            x.off = k.optBoolean("off", false);
            out.add(x);
        }
        return out;
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
