package ai.magpie.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** 设计 token 与控件工厂：纸色 + 单一橙强调，8pt 栅格。 */
public final class Ui {

    public static final int PAPER = Color.parseColor("#F2F0EB");
    public static final int CARD = Color.parseColor("#FBFAF7");
    public static final int INK = Color.parseColor("#14171C");
    public static final int INK2 = Color.parseColor("#59606C");
    public static final int INK3 = Color.parseColor("#9AA1AC");
    public static final int ACCENT = Color.parseColor("#0EA47A");
    public static final int WARN = Color.parseColor("#C2410C");
    public static final int LINE = Color.parseColor("#E3DFD6");

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(dp(c, 3), 1f);
        return t;
    }

    public static TextView mono(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color, false);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout box = column(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(c, 14));
        bg.setStroke(dp(c, 1), LINE);
        box.setBackground(bg);
        int p = dp(c, 16);
        box.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        box.setLayoutParams(lp);
        return box;
    }

    public static Button pill(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinHeight(dp(c, 40));
        b.setMinimumHeight(dp(c, 40));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 999));
        bg.setColor(primary ? INK : CARD);
        bg.setStroke(dp(c, primary ? 0 : 1), LINE);
        b.setBackground(bg);
        b.setTextColor(primary ? CARD : INK);
        return b;
    }

    public static Switch toggle(Context c, boolean on) {
        Switch s = new Switch(c);
        s.setChecked(on);
        return s;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        lp.topMargin = dp(c, 12);
        lp.bottomMargin = dp(c, 12);
        v.setLayoutParams(lp);
        return v;
    }

    public static TextView pillTag(Context c, String s, int color) {
        TextView t = text(c, s, 11, color, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 999));
        bg.setColor((color & 0x00FFFFFF) | 0x14000000);
        t.setBackground(bg);
        t.setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3));
        return t;
    }

    public static void space(ViewGroup parent, Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        parent.addView(v);
    }
}
