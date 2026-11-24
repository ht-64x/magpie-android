package ai.magpie.lite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarm fires: check in, then arm tomorrow's. */
public class CheckinReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    CoreRunner.get(context).exec(new String[] { "accounts", "checkin" });
                } catch (Throwable ignored) {
                } finally {
                    CheckinScheduler.sync(context);
                    pending.finish();
                }
            }
        }, "magpie-checkin").start();
    }
}


