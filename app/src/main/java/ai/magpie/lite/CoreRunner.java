package ai.magpie.lite;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Runs magpie's Go core (packaged as libmagpiecore.so) as a child process and
 * exposes its state, log and CLI. No AndroidX: framework only, so the APK can
 * be built with aapt2 + javac + d8 directly.
 */
public final class CoreRunner {

    public static final int GATEWAY_PORT = 3425; // default, see port()
    public static final int WEB_PORT = 3430;

    public int port() { return prefInt("gateway_port", GATEWAY_PORT); }
    public boolean lan() { return prefBool("lan", false); }
    private int prefInt(String k, int d) {
        return ctx.getSharedPreferences("magpie", Context.MODE_PRIVATE).getInt(k, d);
    }
    private boolean prefBool(String k, boolean d) {
        return ctx.getSharedPreferences("magpie", Context.MODE_PRIVATE).getBoolean(k, d);
    }
    public void setPort(int p) { ctx.getSharedPreferences("magpie", Context.MODE_PRIVATE).edit().putInt("gateway_port", p).apply(); }
    public void setLan(boolean on) { ctx.getSharedPreferences("magpie", Context.MODE_PRIVATE).edit().putBoolean("lan", on).apply(); }

    private static CoreRunner instance;

    public static synchronized CoreRunner get(Context context) {
        if (instance == null) instance = new CoreRunner(context.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final ArrayDeque<String> log = new ArrayDeque<>();
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private Process proc;
    private volatile String state = "已停止";
    private volatile String detail = "";

    private CoreRunner(Context ctx) {
        this.ctx = ctx;
    }

    public String state() { return state; }
    public String detail() { return detail; }
    public boolean isRunning() { return proc != null && proc.isAlive(); }
    public String apiBase() { return "http://127.0.0.1:" + port() + "/v1"; }
    public String consoleUrl() { return consoleUrl(null); }

    /** view: null for the last page, otherwise "plugins", "providers", "usage"… */
    public String consoleUrl(String view) {
        String url = "http://127.0.0.1:" + WEB_PORT + "/?k=" + webKey();
        return view == null || view.isEmpty() ? url : url + "&view=" + view;
    }

    public synchronized void start() {
        if (isRunning()) return;
        seed();
        File bin = binary();
        if (bin == null) {
            setState("失败", "APK 里没有 libmagpiecore.so（lib/arm64-v8a）");
            return;
        }
        if (!bin.canExecute()) {
            setState("失败", "core 不可执行：" + bin.getAbsolutePath());
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                bin.getAbsolutePath(), "web",
                "--addr", "127.0.0.1:" + WEB_PORT,
                "--no-open");
            pb.directory(home());
            pb.redirectErrorStream(true);
            pb.environment().putAll(environment());
            proc = pb.start();
            setState("启动中", "等待端口 " + GATEWAY_PORT);
            append("$ " + bin.getName() + " web --addr 127.0.0.1:" + WEB_PORT + " --no-open");
            pump(proc);
            watch(proc);
            health(proc);
        } catch (Throwable t) {
            setState("失败", String.valueOf(t.getMessage()));
            append("! " + t);
        }
    }

    public synchronized void stop() {
        Process p = proc;
        proc = null;
        if (p != null) {
            append("$ 停止 core (pid=" + p.pid() + ")");
            p.destroy();
            try { p.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            p.destroyForcibly();
        }
        setState("已停止", "");
    }

    /** Runs a magpie subcommand against the same HOME; returns stdout+stderr. */
    public String exec(String... args) {
        File bin = binary();
        if (bin == null) return "缺少 core 二进制";
        StringBuilder out = new StringBuilder();
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(bin.getAbsolutePath());
            for (String a : args) cmd.add(a);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(home());
            pb.redirectErrorStream(true);
            pb.environment().putAll(environment());
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                if (out.length() < 60000) out.append(line).append('\n');
            }
            p.waitFor(120, TimeUnit.SECONDS);
            return "$ magpie " + join(args) + "\n" + out;
        } catch (Throwable t) {
            return "失败：" + t;
        }
    }

    public String logText(int lines) {
        synchronized (log) {
            StringBuilder sb = new StringBuilder();
            int skip = Math.max(0, log.size() - lines);
            int i = 0;
            for (String s : log) {
                if (i++ >= skip) sb.append(s).append('\n');
            }
            return sb.toString();
        }
    }

    // ---- preinstalled bits --------------------------------------------------

    /**
     * WorkBuddy ships inside the APK: its plugin package, its registration and
     * a Bun built for Android. First start copies them into the places magpie
     * looks, so the provider is there without a download or an install step.
     */
    private void seed() {
        File marker = new File(ctx.getFilesDir(), "preload-v1.done");
        if (marker.exists()) return;
        try {
            File magpie = new File(new File(home(), ".config"), "magpie");
            copyAssetDir("preload/magpie", magpie);

            marker.createNewFile();
        } catch (Throwable ignored) {
            // a missing seed only means WorkBuddy is absent; the rest works
        }
    }

    private void copyAsset(String path, File out) throws Exception {
        out.getParentFile().mkdirs();
        try (java.io.InputStream in = ctx.getAssets().open(path);
             java.io.OutputStream os = new java.io.FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    private void copyAssetDir(String assetDir, File outDir) throws Exception {
        String[] names = ctx.getAssets().list(assetDir);
        if (names == null || names.length == 0) {
            copyAsset(assetDir, outDir);
            return;
        }
        outDir.mkdirs();
        for (String name : names) {
            String child = assetDir + "/" + name;
            String[] deeper = ctx.getAssets().list(child);
            if (deeper != null && deeper.length > 0) {
                copyAssetDir(child, new File(outDir, name));
            } else {
                File target = new File(outDir, name);
                if (!target.isFile()) copyAsset(child, target);
            }
        }
    }

    // ---- internals ---------------------------------------------------------

    private File binary() {
        File f = new File(ctx.getApplicationInfo().nativeLibraryDir, "libmagpiecore.so");
        return f.isFile() ? f : null;
    }

    private File home() {
        File f = new File(ctx.getFilesDir(), "home");
        f.mkdirs();
        return f;
    }

    private String webKey() {
        SharedPreferences sp = ctx.getSharedPreferences("magpie", Context.MODE_PRIVATE);
        String k = sp.getString("webkey", null);
        if (k == null || k.length() < 16) {
            byte[] raw = new byte[24];
            new SecureRandom().nextBytes(raw);
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) sb.append(String.format(Locale.US, "%02x", b));
            k = sb.toString();
            sp.edit().putString("webkey", k).apply();
        }
        return k;
    }

    private Map<String, String> environment() {
        Map<String, String> env = new HashMap<>(System.getenv());
        File home = home();
        env.put("HOME", home.getAbsolutePath());
        env.put("XDG_CONFIG_HOME", new File(home, ".config").getAbsolutePath());
        env.put("XDG_CACHE_HOME", new File(new File(ctx.getCacheDir(), "runtime"), ".cache").getAbsolutePath());
        env.put("XDG_DATA_HOME", new File(home, ".local/share").getAbsolutePath());
        File tmp = new File(new File(ctx.getCacheDir(), "runtime"), "tmp");
        tmp.mkdirs();
        env.put("TMPDIR", tmp.getAbsolutePath());
        env.put("PATH", "/product/bin:/system/bin:/system/xbin");
        env.put("MAGPIE_ADDR", (lan() ? "0.0.0.0:" : "127.0.0.1:") + port());
        env.put("MAGPIE_WEB_KEY", webKey());
        env.put("MAGPIE_DNS", dnsServers());
        // magpie logs every call (status, provider, retries) when this is on.
        env.put("MAGPIE_DEBUG", "1");
        // App data is noexec on Android 10+: Bun ships as libbun.so so it can
        // run from nativeLibraryDir, and magpie takes it from MAGPIE_BUN.
        File bun = new File(ctx.getApplicationInfo().nativeLibraryDir, "libbun.so");
        if (bun.isFile()) env.put("MAGPIE_BUN", bun.getAbsolutePath());
        return env;
    }

    /** Android has no resolv.conf: hand the pure-Go resolver real DNS servers. */
    private String dnsServers() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm.getActiveNetwork();
            LinkProperties lp = cm.getLinkProperties(net);
            if (lp != null) {
                StringBuilder sb = new StringBuilder();
                for (java.net.InetAddress a : lp.getDnsServers()) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(a.getHostAddress());
                }
                if (sb.length() > 0) return sb.toString();
            }
        } catch (Throwable ignored) {
        }
        return "223.5.5.5,1.1.1.1";
    }

    private void pump(final Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) append(line);
            } catch (Throwable ignored) {
            }
        }, "magpie-core-log");
        t.setDaemon(true);
        t.start();
    }

    private void watch(final Process p) {
        Thread t = new Thread(() -> {
            try {
                int code = p.waitFor();
                if (proc == p) {
                    proc = null;
                    setState("已停止", "core 退出，code=" + code);
                    append("! core 退出，code=" + code);
                }
            } catch (Throwable ignored) {
            }
        }, "magpie-core-watch");
        t.setDaemon(true);
        t.start();
    }

    private void health(final Process p) {
        Thread t = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 40000;
            while (System.currentTimeMillis() < deadline && proc == p) {
                if (open(port()) && open(WEB_PORT)) {
                    setState("运行中", "网关 :" + port() + " · 控制台 :" + WEB_PORT);
                    append("# 网关就绪 " + apiBase());
                    return;
                }
                if (!p.isAlive()) return;
                try { Thread.sleep(400); } catch (InterruptedException ignored) { }
            }
        }, "magpie-core-health");
        t.setDaemon(true);
        t.start();
    }

    private boolean open(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void setState(String s, String d) {
        state = s;
        detail = d == null ? "" : d;
    }

    public java.io.File logFile() {
        java.io.File dir = new java.io.File(ctx.getFilesDir(), "logs");
        dir.mkdirs();
        return new java.io.File(dir, "core.log");
    }

    /** Last n lines of the on-disk log, for the export/share button. */
    public String logTail(int n) {
        java.io.File f = logFile();
        if (!f.isFile()) return "";
        try {
            java.util.List<String> all = java.nio.file.Files.readAllLines(f.toPath());
            int from = Math.max(0, all.size() - n);
            return String.join("\n", all.subList(from, all.size()));
        } catch (Throwable t) {
            return "读取日志失败：" + t;
        }
    }

    public void clearLogFile() {
        try { logFile().delete(); } catch (Throwable ignored) { }
    }

    private void append(String line) {
        String stamped = time.format(new Date()) + " " + line;
        synchronized (log) {
            log.addLast(stamped);
            while (log.size() > 600) log.removeFirst();
        }
        android.util.Log.i("magpie-core", stamped);
        try {
            java.io.File f = logFile();
            if (f.length() > 2L * 1024 * 1024) {
                java.io.File old = new java.io.File(f.getParentFile(), "core.log.1");
                old.delete();
                f.renameTo(old);
            }
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(stamped + "\n");
            w.close();
        } catch (Throwable ignored) { }
    }

    private static String join(String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(p);
        }
        return sb.toString();
    }
}
