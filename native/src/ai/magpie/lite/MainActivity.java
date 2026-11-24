package ai.magpie.lite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 原生外壳：底部四个分栏，数据直接读配置与 CLI，不再把控制台塞进 WebView。 */
public class MainActivity extends Activity {

    private FrameLayout content;
    private LinearLayout nav;
    private TextView statusDot, statusText, statusSub;
    private Switch gatewaySwitch;
    private int tab = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GatewayService.start(this);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.PAPER);

        root.addView(buildHeader());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildNav());
        root.setFitsSystemWindows(true);
        setContentView(root);

        show(0);
        tick();
    }

    private View buildHeader() {
        LinearLayout bar = Ui.row(this);
        int px = Ui.dp(this, 16), py = Ui.dp(this, 12);
        bar.setPadding(px, py, px, py);

        LinearLayout title = Ui.column(this);
        title.addView(Ui.text(this, "magpie", 24, Ui.INK, true));
        LinearLayout line = Ui.row(this);
        statusDot = Ui.text(this, "●", 10, Ui.INK3, false);
        statusText = Ui.text(this, "网关", 13, Ui.INK2, true);
        statusSub = Ui.mono(this, "", 10, Ui.INK3);
        line.addView(statusDot);
        line.addView(Ui.text(this, " ", 4, Ui.INK3, false));
        line.addView(statusText);
        line.addView(Ui.text(this, " ", 4, Ui.INK3, false));
        line.addView(statusSub);
        title.addView(line);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        gatewaySwitch = new Switch(this);
        gatewaySwitch.setOnCheckedChangeListener((b, on) -> {
            if (on) GatewayService.start(this); else GatewayService.stop(this);
            ui.postDelayed(this::refreshHeader, 600);
        });
        bar.addView(gatewaySwitch);
        return bar;
    }

    private View buildNav() {
        nav = Ui.row(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Ui.CARD);
        int h = Ui.dp(this, 58);
        nav.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        String[] labels = { "首页", "供应商", "API", "设置" };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, labels[i], 13, Ui.INK3, true);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> show(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h, 1f);
            lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
            t.setLayoutParams(lp);
            nav.addView(t);
        }
        return nav;
    }

    private void paintNav() {
        for (int i = 0; i < nav.getChildCount(); i++) {
            TextView t = (TextView) nav.getChildAt(i);
            t.setTextColor(i == tab ? Ui.INK : Ui.INK3);
            if (i == tab) {
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(Ui.dp(this, 999));
                bg.setColor(Ui.PAPER);
                t.setBackground(bg);
            } else {
                t.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private void show(int idx) {
        tab = idx;
        paintNav();
        content.removeAllViews();
        View v;
        switch (idx) {
            case 1: v = providersView(); break;
            case 2: v = apiView(); break;
            case 3: v = settingsView(); break;
            default: v = homeView();
        }
        content.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refreshHeader();
    }

    private ScrollView scroll() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        return s;
    }

    private LinearLayout body() {
        LinearLayout b = Ui.column(this);
        b.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        return b;
    }

    // ---------- 首页 ----------
    private View homeView() {
        ScrollView s = scroll();
        LinearLayout b = body();

        LinearLayout hero = Ui.card(this);
        CoreRunner core = CoreRunner.get(this);
        hero.addView(Ui.text(this, core.state(), 26, Ui.INK, true));
        Ui.space(hero, this, 6);
        hero.addView(Ui.text(this, core.detail().isEmpty() ? "本地网关 · 127.0.0.1" : core.detail(),
                13, Ui.INK2, false));
        Ui.space(hero, this, 14);
        LinearLayout stats = Ui.row(this);
        stats.addView(stat("供应商", String.valueOf(Data.providers(this).size())));
        stats.addView(stat("本地密钥", String.valueOf(Data.keys(this).size())));
        stats.addView(stat("端口", String.valueOf(core.port())));
        hero.addView(stats);
        b.addView(hero);

        LinearLayout api = Ui.card(this);
        api.addView(Ui.text(this, "本地 API", 13, Ui.INK3, true));
        Ui.space(api, this, 8);
        api.addView(Ui.mono(this, core.apiBase(), 14, Ui.INK));
        Ui.space(api, this, 10);
        LinearLayout row = Ui.row(this);
        Button copy = Ui.pill(this, "复制地址", false);
        copy.setOnClickListener(v -> {
            android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("magpie", core.apiBase()));
            toast("已复制");
        });
        Button console = Ui.pill(this, "打开控制台", true);
        console.setOnClickListener(v -> startActivity(new Intent(this, ConsoleActivity.class)));
        row.addView(copy);
        row.addView(Ui.text(this, "  ", 6, Ui.INK3, false));
        row.addView(console);
        api.addView(row);
        b.addView(api);

        LinearLayout tips = Ui.card(this);
        tips.addView(Ui.text(this, "给手机上的 App 用", 13, Ui.INK3, true));
        Ui.space(tips, this, 8);
        tips.addView(Ui.text(this, "base_url 填上面的地址，key 在 API 分栏里给每个 App 单独建一把。",
                13, Ui.INK2, false));
        b.addView(tips);

        s.addView(b);
        return s;
    }

    private View stat(String label, String value) {
        LinearLayout c = Ui.column(this);
        c.setPadding(0, 0, Ui.dp(this, 24), 0);
        c.addView(Ui.text(this, value, 22, Ui.INK, true));
        c.addView(Ui.text(this, label, 11, Ui.INK3, false));
        return c;
    }

    // ---------- 供应商 ----------
    private View providersView() {
        ScrollView s = scroll();
        LinearLayout b = body();
        List<Data.Provider> list = Data.providers(this);

        if (list.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.addView(Ui.text(this, "还没有供应商", 18, Ui.INK, true));
            Ui.space(empty, this, 6);
            empty.addView(Ui.text(this, "在控制台里添加，或用 WorkBuddy 登录后自动出现。", 13, Ui.INK2, false));
            Ui.space(empty, this, 12);
            Button go = Ui.pill(this, "去控制台添加", true);
            go.setOnClickListener(v -> startActivity(new Intent(this, ConsoleActivity.class)));
            empty.addView(go);
            b.addView(empty);
        } else {
            for (Data.Provider p : list) {
                LinearLayout card = Ui.card(this);
                LinearLayout head = Ui.row(this);
                head.addView(Ui.text(this, p.name.isEmpty() ? p.id : p.name, 17, Ui.INK, true),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                head.addView(Ui.pillTag(this, p.models + " 个模型", Ui.ACCENT));
                card.addView(head);
                Ui.space(card, this, 6);
                card.addView(Ui.mono(this, p.host.isEmpty() ? "插件提供 · 点开编辑" : p.host, 11, Ui.INK3));
                final String pid = p.id;
                card.setOnClickListener(v -> {
                    if (pid.startsWith("workbuddy")) {
                        Intent pi = new Intent(this, ProviderEditActivity.class);
                        pi.putExtra("id", pid);
                        startActivity(pi);
                    } else {
                        Intent ci = new Intent(this, ConsoleActivity.class);
                        ci.putExtra("url", CoreRunner.get(this).consoleUrl() + "&view=providers&edit=" + pid);
                        startActivity(ci);
                    }
                });
                b.addView(card);
            }
        }
        s.addView(b);
        return s;
    }

    // ---------- API ----------
    private View apiView() {
        ScrollView s = scroll();
        LinearLayout b = body();

        LinearLayout head = Ui.card(this);
        head.addView(Ui.text(this, "自己建 API，跑在本机", 18, Ui.INK, true));
        Ui.space(head, this, 6);
        head.addView(Ui.mono(this, CoreRunner.get(this).apiBase(), 13, Ui.INK2));
        Ui.space(head, this, 6);
        head.addView(Ui.text(this, "真实 key 只存在这台手机里，每个 App 拿一把本地密钥。", 13, Ui.INK2, false));
        b.addView(head);

        List<Data.Key> keys = Data.keys(this);
        for (Data.Key k : keys) {
            LinearLayout card = Ui.card(this);
            LinearLayout top = Ui.row(this);
            top.addView(Ui.text(this, k.name.isEmpty() ? k.id : k.name, 16, Ui.INK, true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(Ui.pillTag(this, k.off ? "已停用" : "启用", k.off ? Ui.INK3 : Ui.ACCENT));
            card.addView(top);
            Ui.space(card, this, 4);
            String tail = k.secret.length() >= 8 ? k.secret.substring(k.secret.length() - 8) : k.id;
            card.addView(Ui.mono(this, "sk-…" + tail, 11, Ui.INK3));
            Ui.space(card, this, 10);
            LinearLayout acts = Ui.row(this);
            Button del = Ui.pill(this, "删除", false);
            del.setOnClickListener(v -> {
                Data.removeKey(this, k.id);
                toast("已删除");
                show(2);
            });
            Button test = Ui.pill(this, "测试", true);
            final String secret = k.secret;
            test.setOnClickListener(v -> {
                test.setText("测试中…");
                new Thread(() -> {
                    final String r = Data.testKey(this, secret);
                    ui.post(() -> {
                        test.setText("测试");
                        toast(r);
                    });
                }).start();
            });
            Button sw = Ui.pill(this, k.off ? "启用" : "停用", false);
            sw.setOnClickListener(v -> {
                Data.toggleKey(this, k.id, !k.off);
                toast(k.off ? "已启用" : "已停用");
                show(2);
            });
            acts.addView(test);
            acts.addView(Ui.text(this, "  ", 6, Ui.INK3, false));
            acts.addView(sw);
            acts.addView(Ui.text(this, "  ", 6, Ui.INK3, false));
            acts.addView(del);
            card.addView(acts);
            b.addView(card);
        }

        LinearLayout create = Ui.card(this);
        create.addView(Ui.text(this, "新建密钥", 13, Ui.INK3, true));
        Ui.space(create, this, 8);
        EditText input = new EditText(this);
        input.setHint("名称，例如 Oprit AI");
        input.setTextSize(15);
        create.addView(input);
        Ui.space(create, this, 8);
        Button add = Ui.pill(this, "创建", true);
        add.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = "新密钥";
            final String n = name;
            new Thread(() -> {
                final String secret = Data.addKey(this, n);
                ui.post(() -> {
                    toast(secret.isEmpty() ? "创建失败，看日志" : "已创建：" + secret.substring(0, Math.min(12, secret.length())) + "…");
                    show(2);
                });
            }).start();
        });
        create.addView(add);
        b.addView(create);

        s.addView(b);
        return s;
    }



    // ---------- 设置 ----------
    private View settingsView() {
        ScrollView s = scroll();
        LinearLayout b = body();

        LinearLayout net = Ui.card(this);
        net.addView(Ui.text(this, "监听", 13, Ui.INK3, true));
        Ui.space(net, this, 8);
        LinearLayout pr = Ui.row(this);
        pr.addView(Ui.text(this, "端口", 14, Ui.INK, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        EditText port = new EditText(this);
        port.setText(String.valueOf(CoreRunner.get(this).port()));
        port.setTextSize(14);
        pr.addView(port, new LinearLayout.LayoutParams(Ui.dp(this, 90), ViewGroup.LayoutParams.WRAP_CONTENT));
        net.addView(pr);
        Ui.space(net, this, 8);
        LinearLayout lr = Ui.row(this);
        lr.addView(Ui.text(this, "允许局域网访问", 14, Ui.INK, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch lan = new Switch(this);
        lan.setChecked(CoreRunner.get(this).lan());
        lan.setOnCheckedChangeListener((v, on) -> {
            CoreRunner.get(this).setLan(on);
            GatewayService.start(this);
            toast(on ? "已开放局域网" : "已关闭");
        });
        lr.addView(lan);
        net.addView(lr);
        Ui.space(net, this, 12);
        Button save = Ui.pill(this, "保存端口（会重启网关）", true);
        save.setOnClickListener(v -> {
            try {
                int p = Integer.parseInt(port.getText().toString().trim());
                CoreRunner.get(this).setPort(p);
                GatewayService.start(this);
                toast("端口改为 " + p);
            } catch (Throwable t) {
                toast("端口要是数字");
            }
        });
        net.addView(save);
        b.addView(net);

        LinearLayout tools = Ui.card(this);
        tools.addView(Ui.text(this, "诊断", 13, Ui.INK3, true));
        Ui.space(tools, this, 10);
        Button log = Ui.pill(this, "查看核心日志", false);
        log.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        tools.addView(log);
        Ui.space(tools, this, 8);
        Button console = Ui.pill(this, "高级控制台（WebView）", false);
        console.setOnClickListener(v -> startActivity(new Intent(this, ConsoleActivity.class)));
        tools.addView(console);
        b.addView(tools);

        s.addView(b);
        return s;
    }

    private void refreshHeader() {
        CoreRunner c = CoreRunner.get(this);
        boolean live = c.isRunning();
        statusDot.setTextColor(live ? Ui.ACCENT : Ui.INK3);
        statusText.setText(live ? "运行中" : "已停止");
        statusSub.setText(live ? (":" + c.port()) : "");
        if (gatewaySwitch.isChecked() != live) gatewaySwitch.setChecked(live);
    }

    private void tick() {
        ui.postDelayed(() -> {
            refreshHeader();
            if (tab == 0) show(0);
            tick();
        }, 6000);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        show(tab);
    }
}

