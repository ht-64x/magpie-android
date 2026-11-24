package ai.magpie.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

/** One alarm a day: runs `accounts checkin` for every signed-in vendor. */
public final class CheckinScheduler {

    private static final int REQUEST = 77;

    public static boolean enabled(Context c) {
        return prefs(c).getBoolean("auto_checkin", false);
    }

    public static int hour(Context c) {
        return prefs(c).getInt("checkin_hour", 9);
    }

    public static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("auto_checkin", on).apply();
        sync(c);
    }

    public static void setHour(Context c, int h) {
        prefs(c).edit().putInt("checkin_hour", Math.max(0, Math.min(23, h))).apply();
        sync(c);
    }

    /** Cancels and re-arms: after boot, after a toggle, and after each run. */
    public static void sync(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(
            c, REQUEST, new Intent(c, CheckinReceiver.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (!enabled(c)) return;

        Calendar at = Calendar.getInstance();
        at.set(Calendar.HOUR_OF_DAY, hour(c));
        at.set(Calendar.MINUTE, 5);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        if (at.getTimeInMillis() <= System.currentTimeMillis()) at.add(Calendar.DAY_OF_YEAR, 1);
        // inexact on purpose: no SCHEDULE_EXACT_ALARM permission to ask for
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(), pi);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("magpie", Context.MODE_PRIVATE);
    }
}

