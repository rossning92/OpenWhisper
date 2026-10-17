package com.ross.speechtotext;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ModelDownloadService extends Service {
    private static final String CHANNEL = "OfflineModelDownload";
    private static final int NOTIFICATION = 2;
    static final String CANCEL = "cancel";
    private static final String MODEL_KEY = "model_key";
    static volatile boolean downloading;
    private static volatile String status;
    private static volatile OfflineModel statusModel;
    private OfflineModel selectedModel;
    private volatile Call call;
    private volatile boolean cancelled;

    static void startDownload(Context context, OfflineModel model) {
        // A retry must not reuse the previous attempt's error as its starting state.
        status = null;
        context.startForegroundService(new Intent(context, ModelDownloadService.class)
                .putExtra(MODEL_KEY, model.key));
    }

    static String statusFor(OfflineModel model) {
        return statusModel == model ? status : null;
    }

    static boolean isDownloading(OfflineModel model) {
        return downloading && statusModel == model;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && CANCEL.equals(intent.getAction())) {
            cancelled = true;
            if (call != null) call.cancel();
            if (!downloading) stopSelf();
            return START_NOT_STICKY;
        }
        if (downloading) return START_NOT_STICKY;
        downloading = true;
        cancelled = false;
        selectedModel = OfflineModel.selected(this);
        if (intent != null) {
            String key = intent.getStringExtra(MODEL_KEY);
            for (OfflineModel model : OfflineModel.ALL) {
                if (model.key.equals(key)) selectedModel = model;
            }
        }
        status = "Downloading " + selectedModel.label + "...";
        statusModel = selectedModel;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Offline model download",
                NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION, notification(0));
        new Thread(() -> download(), "OfflineModelDownload").start();
        return START_NOT_STICKY;
    }

    private Notification notification(int progress) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel = PendingIntent.getService(this, 0,
                new Intent(this, ModelDownloadService.class).setAction(CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_graphic_eq)
                .setContentTitle("Downloading " + selectedModel.label).setContentText(status)
                .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
                .setProgress(100, progress, false)
                .addAction(new Notification.Action.Builder(null, "Cancel", cancel).build()).build();
    }

    private void download() {
        OfflineModel model = selectedModel;
        File target = model.file(this);
        File partial = new File(target.getPath() + ".part");
        try {
            if (model.ready(target)) {
                model.select(this);
                status = "Ready: " + model.label;
                return;
            }
            if (partial.exists() && !partial.delete()) throw new Exception("Could not clear interrupted download");
            if (target.getParentFile().getUsableSpace() < model.bytes + 64 * 1024 * 1024) {
                long requiredMB = (model.bytes + 64 * 1024 * 1024 + 999999) / 1000000;
                throw new Exception("Not enough storage; free at least " + requiredMB + " MB");
            }
            OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS).build();
            call = client.newCall(new Request.Builder().url(model.url).build());
            if (cancelled) call.cancel();
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            long count = 0;
            int lastProgress = -1;
            try (Response response = call.execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new Exception("Download failed: HTTP " + response.code());
                }
                try (InputStream input = response.body().byteStream();
                     FileOutputStream output = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[65536];
                    int size;
                    while ((size = input.read(buffer)) != -1) {
                        if (cancelled) throw new Exception("Download cancelled");
                        count += size;
                        if (count > model.bytes) throw new Exception("Unexpected model size");
                        hash.update(buffer, 0, size);
                        output.write(buffer, 0, size);
                        int progress = (int) (count * 100 / model.bytes);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            status = "Downloading " + model.label + ": " + progress + "%";
                            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(progress));
                        }
                    }
                    output.getFD().sync();
                }
            }
            StringBuilder digest = new StringBuilder();
            for (byte value : hash.digest()) digest.append(String.format(Locale.ROOT, "%02x", value & 255));
            if (count != model.bytes || !model.sha256.equals(digest.toString())) {
                throw new Exception("Model verification failed; tap to retry");
            }
            if (cancelled) throw new Exception("Download cancelled");
            if (!partial.renameTo(target)) throw new Exception("Could not save model");
            model.select(this);
            status = "Ready: " + model.label;
        } catch (Exception error) {
            String message = error.getMessage();
            status = cancelled ? "Download cancelled; tap to retry"
                    : (message != null ? message : "Download failed; tap to retry");
        } finally {
            partial.delete();
            call = null;
            downloading = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    @Override public void onDestroy() {
        cancelled = true;
        if (call != null) call.cancel();
        super.onDestroy();
    }
}
