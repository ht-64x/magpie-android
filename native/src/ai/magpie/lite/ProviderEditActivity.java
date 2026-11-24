package ai.magpie.lite;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 原生供应商编辑：账号列表 / 设为首选 / 删除，其余高级项交给控制台。 */
public class ProviderEditActivity extends Activity {

    private String providerId = "workbuddy";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        providerId = getIntent() == null ? "workbuddy" : getIntent().getStringExtra("id");
        if (providerId == null) providerId = "workbuddy";

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.PAPER);
        root.setFitsSystemWindows(true);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        TextView back = Ui.text(this, "‹", 26, Ui.INK, true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.text(this, "workbuddy", 20, Ui.INK, true));
        titles.addView(Ui.text(this, "账号与模型", 11, Ui.INK3, false));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = Ui.dp(this, 10);
        bar.addView(titles, tp);
        root.addView(bar);

        ScrollView s = new ScrollView(this);
        body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 24));
        s.addView(body);
        root.addView(s, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        render();
    }

    private void render() {
        body.removeAllViews();
        List<Data.Account> accs = Data.accounts(this);

        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "账号 " + accs.size() + " 个", 13, Ui.INK3, true));
        Ui.space(card, this, 8);
        if (accs.isEmpty()) {
            card.addView(Ui.text(this, "还没有登录的账号，去控制台里登录或从文件导入。", 13, Ui.INK2, false));
        }
        for (Data.Account a : accs) {
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            LinearLayout col = Ui.column(this);
            col.addView(Ui.text(this, a.name, 16, Ui.INK, true));
            col.addView(Ui.mono(this, a.key, 11, Ui.INK3));
            row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (a.primary) {
                row.addView(Ui.pillTag(this, "首选", Ui.ACCENT));
            } else {
                Button star = Ui.pill(this, "设为首选", false);
                star.setOnClickListener(v -> {
                    Data.makePrimary(this, a.key);
                    GatewayService.start(this);
                    toast("已设为首选");
                    ui.postDelayed(this::render, 1200);
                });
                row.addView(star);
            }
            Button del = Ui.pill(this, "删除", false);
            del.setTextColor(Ui.WARN);
            del.setOnClickListener(v -> {
                Data.removeAccount(this, a.key);
                GatewayService.start(this);
                toast("已删除该账号");
                ui.postDelayed(this::render, 1200);
            });
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dp.leftMargin = Ui.dp(this, 8);
            row.addView(del, dp);
            card.addView(row);
            card.addView(Ui.divider(this));
        }
        body.addView(card);

        LinearLayout tips = Ui.card(this);
        tips.addView(Ui.text(this, "模型与额度", 13, Ui.INK3, true));
        Ui.space(tips, this, 8);
        tips.addView(Ui.text(this, "勾选哪些模型、看额度、导入账号，这些在 magpie 控制台里改；这里只管账号和首选顺序。",
                13, Ui.INK2, false));
        Ui.space(tips, this, 12);
        Button advanced = Ui.pill(this, "打开完整编辑", true);
        advanced.setOnClickListener(v -> {
            Intent i = new Intent(this, ConsoleActivity.class);
            i.putExtra("url", CoreRunner.get(this).consoleUrl() + "&view=providers&edit=" + providerId);
            startActivity(i);
        });
        tips.addView(advanced);
        body.addView(tips);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
