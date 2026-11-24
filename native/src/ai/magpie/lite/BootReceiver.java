package ai.magpie.lite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sp = context.getSharedPreferences("magpie", Context.MODE_PRIVATE);
        if (!sp.getBoolean("autostart", false)) return;
        try {
            ai.magpie.lite.CheckinScheduler.sync(context);
            GatewayService.start(context);
        } catch (Throwable ignored) {
        }
    }
}


