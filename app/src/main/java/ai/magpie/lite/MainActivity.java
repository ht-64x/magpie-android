package ai.magpie.lite;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** One screen: the switch, the API address, the console, and the CLI buttons. */
public class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView logView;
    private TextView resultView;
    private Switch toggle;
    private boolean syncing;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("magpie");
        title.setTextSize(26);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("本地 AI 网关 · OpenAI 兼容 API · 模型路由");
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 4, 0, pad);
        root.addView(subtitle);

        toggle = new Switch(this);
        toggle.setText("本地网关");
        toggle.setTextSize(18);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (syncing) return;
                if (isChecked) {
                    requestNotifications();
                    GatewayService.start(MainActivity.this);
                } else {
                    GatewayService.stop(MainActivity.this);
                }
            }
        });
        root.addView(toggle);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, pad / 2, 0, pad);
        root.addView(status);

        TextView api = new TextView(this);
        api.setText("API base_url：\n" + CoreRunner.get(this).apiBase());
        api.setTextSize(14);
        api.setTextIsSelectable(true);
        root.addView(api);

        LinearLayout apiRow = new LinearLayout(this);
        apiRow.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = new Button(this);
        copy.setText("复制地址");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = getSystemService(ClipboardManager.class);
                cm.setPrimaryClip(ClipData.newPlainText("magpie", CoreRunner.get(MainActivity.this).apiBase()));
                toast("已复制 " + CoreRunner.get(MainActivity.this).apiBase());
            }
        });
        Button selftest = new Button(this);
        selftest.setText("自测 /v1/models");
        selftest.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selfTest(); }
        });
        apiRow.addView(copy, weight());
        apiRow.addView(selftest, weight());
        root.addView(apiRow);

        Button console = new Button(this);
        console.setText("打开控制台（加 provider / 装 WorkBuddy 插件 / 配路由）");
        console.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ConsoleActivity.class));
            }
        });
        root.addView(console);

        LinearLayout pluginRow = new LinearLayout(this);
        pluginRow.setOrientation(LinearLayout.HORIZONTAL);
        Button market = new Button(this);
        market.setText("插件市场（WorkBuddy）");
        market.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, ConsoleActivity.class);
                i.putExtra("view", "plugins");
                startActivity(i);
            }
        });
        pluginRow.addView(market, weight());
        pluginRow.addView(tool("装 WorkBuddy 插件", "plugin", "add", "workbuddy"), weight());
        root.addView(pluginRow);

        LinearLayout tools1 = new LinearLayout(this);
        tools1.setOrientation(LinearLayout.HORIZONTAL);
        tools1.addView(tool("每日签到", "accounts", "checkin"), weight());
        tools1.addView(tool("账户与额度", "accounts"), weight());
        root.addView(tools1);

        LinearLayout tools2 = new LinearLayout(this);
        tools2.setOrientation(LinearLayout.HORIZONTAL);
        tools2.addView(tool("模型目录", "models"), weight());
        tools2.addView(tool("已装插件", "plugin"), weight());
        root.addView(tools2);

        LinearLayout diag = new LinearLayout(this);
        diag.setOrientation(LinearLayout.HORIZONTAL);
        Button errs = new Button(this);
        errs.setText("错误摘要");
        errs.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showErrors(); }
        });
        Button export = new Button(this);
        export.setText("导出日志");
        export.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shareLog(); }
        });
        Button wipe = new Button(this);
        wipe.setText("清空日志");
        wipe.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                CoreRunner.get(MainActivity.this).clearLogFile();
                toast("已清空");
            }
        });
        diag.addView(errs, weight());
        diag.addView(export, weight());
        diag.addView(wipe, weight());
        root.addView(diag);

        Button importAuth = new Button(this);
        importAuth.setText("导入 WorkBuddy 凭证（JSON / .info）");
        importAuth.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickAuthFile(); }
        });
        root.addView(importAuth);

        resultView = new TextView(this);
        resultView.setTextSize(12);
        resultView.setTypeface(android.graphics.Typeface.MONOSPACE);
        resultView.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(resultView);

        TextView logTitle = new TextView(this);
        logTitle.setText("日志");
        logTitle.setTextSize(14);
        logTitle.setPadding(0, pad, 0, 4);
        root.addView(logTitle);

        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        root.addView(logView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        requestNotifications();
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(4, 0, 4, 0);
        return lp;
    }

    private Button tool(String label, final String... args) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                resultView.setText("执行中… " + label);
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String out = CoreRunner.get(MainActivity.this).exec(args);
                        handler.post(new Runnable() {
                            @Override public void run() { resultView.setText(out); }
                        });
                    }
                }).start();
            }
        });
        return b;
    }

    /** Lines worth reading first: HTTP errors, timeouts, provider refusals. */
    private void showErrors() {
        String all = CoreRunner.get(this).logTail(3000);
        StringBuilder sb = new StringBuilder();
        for (String line : all.split("\n")) {
            String l = line.toLowerCase();
            if (l.contains("error") || l.contains("fail") || l.contains("timeout")
                || l.contains(" 429") || l.contains(" 500") || l.contains(" 502")
                || l.contains(" 503") || l.contains("refus") || l.contains("失败")
                || l.contains("retry") || l.contains("dial") || l.contains("x509")) {
                sb.append(line).append('\n');
            }
        }
        if (sb.length() == 0) sb.append("日志里没有错误行。把「日志」区的最后几行一起发我。");
        if (sb.length() > 20000) sb.setLength(20000);
        resultView.setText(sb.toString());
    }

    private void shareLog() {
        String text = CoreRunner.get(this).logTail(1500);
        if (text.length() > 60000) text = text.substring(text.length() - 60000);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "magpie core log");
        i.putExtra(Intent.EXTRA_TEXT, text.isEmpty() ? "（还没有日志）" : text);
        startActivity(Intent.createChooser(i, "导出日志"));
    }

    private static final int PICK_AUTH = 7;

    private void pickAuthFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, PICK_AUTH);
        } catch (Throwable t) {
            toast("没有文件选择器：" + t);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AUTH || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            java.io.InputStream in = getContentResolver().openInputStream(data.getData());
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            String text = bos.toString("UTF-8");
            org.json.JSONObject picked = new org.json.JSONObject(text);
            org.json.JSONObject cred = picked.optJSONObject("workbuddy");
            if (cred == null) {
                if (picked.has("refresh") || picked.has("access") || picked.has("access_token")) cred = picked;
            }
            if (cred == null) { toast("文件里没有 WorkBuddy 凭证字段"); return; }
            java.io.File dir = new java.io.File(new java.io.File(new java.io.File(getFilesDir(), "home"), ".config"), "magpie");
            dir.mkdirs();
            java.io.File target = new java.io.File(dir, "plugin-auth.json");
            org.json.JSONObject all = new org.json.JSONObject();
            if (target.isFile()) {
                java.util.Scanner sc = new java.util.Scanner(target, "UTF-8").useDelimiter("\\A");
                String old = sc.hasNext() ? sc.next() : "";
                sc.close();
                if (old.trim().startsWith("{")) all = new org.json.JSONObject(old);
            }
            all.put("workbuddy", cred);
            java.io.FileWriter w = new java.io.FileWriter(target, false);
            w.write(all.toString(2));
            w.close();
            GatewayService.start(this);
            toast("已导入，网关重启中");
        } catch (Throwable t) {
            toast("导入失败：" + t.getMessage());
        }
    }

    private void selfTest() {
        resultView.setText("请求 /v1/models …");
        new Thread(new Runnable() {
            @Override public void run() {
                final StringBuilder sb = new StringBuilder();
                HttpURLConnection c = null;
                try {
                    URL url = new URL(CoreRunner.get(MainActivity.this).apiBase() + "/models");
                    c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(4000);
                    c.setReadTimeout(20000);
                    int code = c.getResponseCode();
                    BufferedReader r = new BufferedReader(new InputStreamReader(
                        code >= 400 ? c.getErrorStream() : c.getInputStream()));
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) body.append(line);
                    sb.append("HTTP ").append(code).append('\n');
                    String text = body.toString();
                    int count = 0, idx = 0;
                    while ((idx = text.indexOf("\"id\":", idx + 1)) > 0) count++;
                    sb.append("模型数：").append(count).append('\n');
                    if (count == 0) sb.append("（还没有 provider：去控制台加一个，或装 WorkBuddy 插件）\n");
                    sb.append(text.length() > 600 ? text.substring(0, 600) + "…" : text);
                } catch (Throwable t) {
                    sb.append("失败：").append(t);
                } finally {
                    if (c != null) c.disconnect();
                }
                handler.post(new Runnable() {
                    @Override public void run() { resultView.setText(sb.toString()); }
                });
            }
        }).start();
    }

    private void render() {
        CoreRunner core = CoreRunner.get(this);
        boolean running = core.isRunning();
        status.setText(core.state() + (core.detail().isEmpty() ? "" : " · " + core.detail()));
        syncing = true;
        toggle.setChecked(running || core.state().startsWith("启动"));
        syncing = false;
        String log = core.logText(12);
        logView.setText(log.isEmpty() ? "（还没有输出）" : log);
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }
}
