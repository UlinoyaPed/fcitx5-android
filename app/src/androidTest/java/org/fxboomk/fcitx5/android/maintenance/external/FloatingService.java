/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.maintenance.external;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Runs under the test APK UID; framework-only to avoid target/Kotlin classpath assumptions. */
public class FloatingService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "stop_service".equals(intent.getAction())) {
            handler.removeCallbacksAndMessages(null); stopForeground(true); stopSelf(); return START_NOT_STICKY;
        }
        String host = intent.getStringExtra("voiceHostPackage");
        String token = intent.getStringExtra("voiceSessionToken");
        if (host == null || token == null) throw new AssertionError("Missing session-token fallback protocol");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("maintenance", "Maintenance validation", NotificationManager.IMPORTANCE_LOW));
        startForeground(2401, new Notification.Builder(this, "maintenance").setContentTitle("Voice regression fixture").setSmallIcon(android.R.drawable.ic_btn_speak_now).build());
        handler.postDelayed(() -> sendBroadcast(new Intent(host + ".plugin.VOICE_INPUT_PARTIAL").setPackage(host)
                .putExtra("voiceSessionToken", token).putExtra("partialText", "fallback partial")), 250);
        handler.postDelayed(() -> sendBroadcast(new Intent(host + ".plugin.VOICE_INPUT_COMMIT").setPackage(host)
                .putExtra("voiceSessionToken", token).putExtra("commitText", " fallback 👋 ")), 700);
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
