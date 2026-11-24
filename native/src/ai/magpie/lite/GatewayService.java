package ai.magpie.lite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** Keeps the Go gateway alive while the app is in the background. */
public class GatewayService extends Service {

    public static final String ACTION_START = "ai.magpie.lite.START";
    public static final String ACTION_STOP = "ai.magpie.lite.STOP";
    private static final String CHANNEL = "magpie.gateway";
    private static final int ID = 4211;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, GatewayService.class).setAction(ACTION_START));
    }

    public static void stop(Context c) {
        c.startForegroundService(new Intent(c, GatewayService.class).setAction(ACTION_STOP));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "本地网关", NotificationManager.IMPORTANCE_LOW));
        startForeground(ID, build("启动中"));
        handler.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            CoreRunner core = CoreRunner.get(GatewayService.this);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(ID, build(core.state()));
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        CoreRunner core = CoreRunner.get(this);
        if (ACTION_STOP.equals(action)) {
            new Thread(core::stop, "magpie-stop").start();
            releaseWake();
            handler.removeCallbacks(tick);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        new Thread(core::start, "magpie-start").start();
        PowerManager pm = getSystemService(PowerManager.class);
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "magpie:gateway");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        releaseWake();
        CoreRunner.get(this).stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void releaseWake() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) { }
        wakeLock = null;
    }

    private Notification build(String state) {
        PendingIntent open = PendingIntent.getActivity(
            this, 1, new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(
            this, 2, new Intent(this, GatewayService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = "OpenAI 兼容 API  http://127.0.0.1:" + CoreRunner.GATEWAY_PORT + "/v1";
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("magpie · " + state)
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "停止", stop).build())
            .build();
    }
}


