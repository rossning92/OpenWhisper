package com.ross.speechtotext;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import androidx.preference.PreferenceManager;
import android.content.res.Configuration;

import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingService extends Service {
    static boolean isFeatureEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(MainActivity.KEY_FEATURE_ENABLED, true);
    }

    static void startIfReady(Context context) {
        String key = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(MainActivity.KEY_OPENAI_API_KEY, "");
        boolean canShowOverlay = Settings.canDrawOverlays(context)
                || TypeAccessibilityService.getInstance() != null;
        if (isFeatureEnabled(context) && canShowOverlay && !key.isEmpty()) {
            context.startForegroundService(new Intent(context, FloatingService.class));
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, FloatingService.class));
    }

    private static FloatingService instance;

    static FloatingService getInstance() {
        return instance;
    }

    private WindowManager windowManager;
    private boolean overlayAttached;
    private LinearLayout container;
    private WindowManager.LayoutParams params;

    private FloatingActionButton floatingView;
    private FloatingActionButton termuxScriptView;
    private FloatingActionButton cancelView;
    private WaveformView waveformView;
    private boolean onLeft = true;
    private int waveW, waveH;
    private boolean useSmallControls;
    private boolean isDictating = false;
    private boolean isTranscribing = false;
    private ObjectAnimator spinAnimator;
    private AlertDialog transcriptionErrorDialog;
    private Transcriber failedTranscriber;
    private boolean failedTranscriptionRunsTermuxScript;
    private boolean sendMessageAfterTranscription;

    private volatile Transcriber transcriber;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String CHANNEL_ID = "FloatingServiceChannel";
    private static final String TERMUX_BASH_PATH =
            "/data/data/com.termux/files/usr/bin/bash";
    private static final String TERMUX_SCRIPT_PATH =
            "/data/data/com.termux/files/home/myscripts/bin/handle_transcription.sh";
    private Context themedContext;
    private Notification notification;

    private String getPrefSuffix() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                ? "_landscape"
                : "_portrait";
    }

    private void savePosition() {
        SharedPreferences prefs = getSharedPreferences("FloatingButtonPrefs", MODE_PRIVATE);
        String suffix = getPrefSuffix();
        prefs.edit()
                .putInt("x" + suffix, params.x)
                .putInt("y" + suffix, params.y)
                .apply();
    }

    private void updatePositionFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("FloatingButtonPrefs", MODE_PRIVATE);
        String suffix = getPrefSuffix();
        params.x = prefs.getInt("x" + suffix, prefs.getInt("x", 100));
        params.y = prefs.getInt("y" + suffix, prefs.getInt("y", 100));
        windowManager.updateViewLayout(container, params);
        // Re-dock to the nearest edge once laid out. getWidth() and the cutout
        // insets are only valid post-layout, and the saved x must be re-snapped
        // to this orientation's usable width (insets differ per orientation).
        container.post(this::savePositionToEdge);
    }

    // Width available to the container, excluding system bars and display cutout.
    // The window is positioned relative to this inset content area, so pinning
    // the right edge must use this (not the full display width), or the WM clamps
    // the window and it no longer sits flush.
    private int usableWidth() {
        WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        Insets insets = metrics.getWindowInsets().getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        return metrics.getBounds().width() - insets.left - insets.right;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updatePositionFromPrefs();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();

        notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Speech To Text")
                .setContentText("Service is running")
                .setSmallIcon(R.drawable.ic_graphic_eq)
                .build();
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        themedContext = com.google.android.material.color.DynamicColors.wrapContextIfAvailable(
                new ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar));

        useSmallControls = readUseSmallControls();
        updateWaveformDimensions();

        floatingView = createFab(R.drawable.ic_graphic_eq);
        floatingView.setHapticFeedbackEnabled(true);
        floatingView.setOnClickListener(v -> toggleDictation());

        cancelView = createFab(R.drawable.ic_close);
        cancelView.setVisibility(View.GONE);
        cancelView.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            cancelDictation();
        });

        termuxScriptView = createFab(R.drawable.ic_terminal);
        termuxScriptView.setVisibility(View.GONE);
        termuxScriptView.setContentDescription("Stop and run Termux script");
        termuxScriptView.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (isDictating && !isTranscribing) finishDictation(true);
        });

        waveformView = new WaveformView(themedContext);
        waveformView.setScale(getControlScale());
        waveformView.setVisibility(View.GONE);

        container = new LinearLayout(themedContext);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);

        View.OnTouchListener dragTouchListener = new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private static final int CLICK_THRESHOLD = 10;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isDragging = false;
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dX = Math.abs(event.getRawX() - initialTouchX);
                        float dY = Math.abs(event.getRawY() - initialTouchY);
                        if (dX > CLICK_THRESHOLD || dY > CLICK_THRESHOLD) {
                            isDragging = true;
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(container, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            savePositionToEdge();
                        } else {
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        };
        floatingView.setOnTouchListener(dragTouchListener);
        waveformView.setOnTouchListener(dragTouchListener);
        termuxScriptView.setOnTouchListener(dragTouchListener);
        cancelView.setOnTouchListener(dragTouchListener);

        applyChildOrder();

        TypeAccessibilityService accessibilityService = TypeAccessibilityService.getInstance();
        if (accessibilityService != null) {
            attachOverlay(accessibilityService, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        } else if (Settings.canDrawOverlays(this)) {
            attachOverlay(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
    }

    private void attachOverlay(Context windowContext, int layoutType) {
        windowManager = (WindowManager) windowContext.getSystemService(WINDOW_SERVICE);
        params = createOverlayLayoutParams(layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        windowManager.addView(container, params);
        overlayAttached = true;
        updatePositionFromPrefs();
    }

    private void detachOverlay() {
        if (overlayAttached) windowManager.removeView(container);
        overlayAttached = false;
    }

    void onAccessibilityServiceConnected(TypeAccessibilityService service) {
        if (overlayAttached
                && params.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) return;
        detachOverlay();
        attachOverlay(service, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    }

    void onAccessibilityServiceDisconnected() {
        // AccessibilityService and FloatingService callbacks run on the main thread.
        if (!overlayAttached
                || params.type != WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) return;
        detachOverlay();
        if (Settings.canDrawOverlays(this)) {
            attachOverlay(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
    }

    private LinearLayout.LayoutParams fabLayoutParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    // The FAB hugs the docked edge and the action buttons grow inward, so their
    // order reverses with the edge.
    private void applyChildOrder() {
        container.removeAllViews();
        if (onLeft) {
            container.addView(floatingView, fabLayoutParams());
            container.addView(termuxScriptView, fabLayoutParams());
            container.addView(waveformView, new LinearLayout.LayoutParams(waveW, waveH));
            container.addView(cancelView, fabLayoutParams());
        } else {
            container.addView(cancelView, fabLayoutParams());
            container.addView(waveformView, new LinearLayout.LayoutParams(waveW, waveH));
            container.addView(termuxScriptView, fabLayoutParams());
            container.addView(floatingView, fabLayoutParams());
        }
    }

    private FloatingActionButton createFab(int iconRes) {
        FloatingActionButton fab = new FloatingActionButton(themedContext);
        fab.setImageResource(iconRes);
        applyFabSize(fab);
        fab.setUseCompatPadding(true);
        fab.setCompatElevation(0f);
        return fab;
    }

    private boolean readUseSmallControls() {
        String buttonSize = PreferenceManager.getDefaultSharedPreferences(this).getString(
                MainActivity.KEY_FLOATING_BUTTON_SIZE,
                MainActivity.FLOATING_BUTTON_SIZE_NORMAL);
        return MainActivity.FLOATING_BUTTON_SIZE_SMALL.equals(buttonSize);
    }

    private int getFabSize() {
        return useSmallControls
                ? FloatingActionButton.SIZE_MINI
                : FloatingActionButton.SIZE_NORMAL;
    }

    private float getControlScale() {
        return useSmallControls ? 1f : 1.4f;
    }

    private void applyFabSize(FloatingActionButton fab) {
        fab.setSize(getFabSize());
        fab.setMaxImageSize((int) (24 * getResources().getDisplayMetrics().density
                * getControlScale()));
    }

    private void updateWaveformDimensions() {
        float density = getResources().getDisplayMetrics().density;
        float scale = getControlScale();
        waveW = (int) (72 * density * scale);
        waveH = (int) (40 * density * scale);
    }

    void updateControlSize() {
        useSmallControls = readUseSmallControls();
        applyFabSize(floatingView);
        applyFabSize(termuxScriptView);
        applyFabSize(cancelView);
        updateWaveformDimensions();
        waveformView.setScale(getControlScale());
        applyChildOrder();
        container.requestLayout();
        container.post(this::savePositionToEdge);
    }

    private WindowManager.LayoutParams createOverlayLayoutParams(int layoutType, int flags) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, layoutType, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        return lp;
    }

    private void toggleDictation() {
        if (isTranscribing || transcriptionErrorDialog != null) return;
        floatingView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (!isDictating) {
            enterDictationMode();
        } else {
            finishDictation();
        }
    }

    // Invoked by the accessibility service on the hardware long-press hotkey.
    void onHotkey() {
        mainHandler.post(this::toggleDictation);
    }

    boolean isDictating() {
        return isDictating;
    }

    // Enter confirms the current dictation (stop, transcribe, type).
    void onConfirmKey() {
        mainHandler.post(() -> {
            if (isDictating && !isTranscribing) finishDictation();
        });
    }

    // Escape cancels the current dictation without transcribing.
    void onCancelKey() {
        mainHandler.post(() -> {
            if (isDictating) cancelDictation();
        });
    }

    private void enterDictationMode() {
        sendMessageAfterTranscription = false;
        isDictating = true;
        floatingView.setImageResource(R.drawable.ic_send);
        waveformView.clear();
        waveformView.setVisibility(View.VISIBLE);
        termuxScriptView.setVisibility(View.VISIBLE);
        cancelView.setVisibility(View.VISIBLE);
        repinToEdge();
        startTranscriber();
    }

    private void cancelDictation() {
        Transcriber current = transcriber;
        if (current != null) current.stop();
        restoreNonMicrophoneForegroundType();
        closeTranscriber(current);
        exitDictationMode();
    }

    private void finishDictation() {
        finishDictation(false);
    }

    private void finishDictation(boolean runTermuxScript) {
        Transcriber current = transcriber;
        if (current != null) current.stop();
        restoreNonMicrophoneForegroundType();
        exitDictationMode();
        if (current == null) {
            showTranscriptionError(new Exception("Transcription did not start"), null);
            return;
        }
        finishTranscription(current, runTermuxScript);
    }

    private void finishTranscription(Transcriber current, boolean runTermuxScript) {
        enterTranscribingMode();
        String finishingMessage = current.getFinishingMessage();
        if (finishingMessage != null) {
            Toast.makeText(this, finishingMessage, Toast.LENGTH_LONG).show();
        }
        executor.execute(() -> {
            try {
                String text = current.finish();
                mainHandler.post(() -> {
                    exitTranscribingMode();
                    if (runTermuxScript) {
                        launchTermuxScript(text);
                    } else {
                        deliverTranscription(text, sendMessageAfterTranscription);
                    }
                    sendMessageAfterTranscription = false;
                    closeTranscriber(current);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    exitTranscribingMode();
                    if (!current.canRetry()) closeTranscriber(current);
                    showTranscriptionError(
                            e, current.canRetry() ? current : null, runTermuxScript);
                });
            }
        });
    }

    private void launchTermuxScript(String text) {
        if (checkSelfPermission(TermuxPermissionActivity.RUN_COMMAND_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            Intent permissionIntent = new Intent(this, TermuxPermissionActivity.class);
            permissionIntent.putExtra(TermuxPermissionActivity.EXTRA_TRANSCRIPTION, text);
            permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permissionIntent);
            return;
        }
        runTermuxScript(this, text);
    }

    static void runTermuxScript(Context context, String text) {
        Intent intent = new Intent("com.termux.RUN_COMMAND");
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH_PATH);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                new String[] { TERMUX_SCRIPT_PATH, text });
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR",
                "/data/data/com.termux/files/home/myscripts");
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        // Session actions: 0 switches to the new session and opens Termux; 1 keeps the
        // current session and opens Termux; 2 switches to the new session without
        // opening Termux; 3 keeps the current session without opening Termux.
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            context.startService(intent);
            Toast.makeText(context, "Running command in Termux", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Unable to run Termux command: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showTranscriptionError(Exception error, Transcriber retryableTranscriber) {
        showTranscriptionError(error, retryableTranscriber, false);
    }

    private void showTranscriptionError(
            Exception error, Transcriber retryableTranscriber, boolean runTermuxScript) {
        failedTranscriber = retryableTranscriber;
        failedTranscriptionRunsTermuxScript = runTermuxScript;

        TextView messageView = new TextView(themedContext);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        messageView.setPadding(padding, 0, padding, 0);
        String message = error.getMessage();
        messageView.setText(message == null || message.isEmpty() ? error.toString() : message);
        messageView.setTextIsSelectable(true);

        ScrollView scrollView = new ScrollView(themedContext);
        scrollView.addView(messageView);

        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext)
                .setTitle("Transcription failed")
                .setView(scrollView)
                .setNegativeButton("Close", (d, which) -> closeFailedTranscriber())
                .setOnCancelListener(d -> closeFailedTranscriber());
        if (retryableTranscriber != null) {
            builder.setPositiveButton("Retry", (d, which) -> {
                transcriptionErrorDialog = null;
                failedTranscriber = null;
                finishTranscription(retryableTranscriber, failedTranscriptionRunsTermuxScript);
            });
        }
        AlertDialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        dialog.setOnDismissListener(d -> {
            if (transcriptionErrorDialog == dialog) {
                transcriptionErrorDialog = null;
            }
        });
        transcriptionErrorDialog = dialog;
        dialog.show();
    }

    private void closeFailedTranscriber() {
        if (failedTranscriber != null) {
            closeTranscriber(failedTranscriber);
            failedTranscriber = null;
        }
    }

    private void enterTranscribingMode() {
        isTranscribing = true;
        floatingView.setImageResource(android.R.drawable.ic_popup_sync);
        spinAnimator = ObjectAnimator.ofFloat(floatingView, "alpha", 1f, 0.3f);
        spinAnimator.setDuration(600);
        spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
        spinAnimator.setRepeatMode(ValueAnimator.REVERSE);
        spinAnimator.start();
    }

    private void exitTranscribingMode() {
        isTranscribing = false;
        if (spinAnimator != null) {
            spinAnimator.cancel();
            spinAnimator = null;
        }
        floatingView.setAlpha(1f);
        resetFabToDefault();
    }

    private void resetFabToDefault() {
        floatingView.setImageResource(R.drawable.ic_graphic_eq);
    }

    private void exitDictationMode() {
        waveformView.setVisibility(View.GONE);
        isDictating = false;
        resetFabToDefault();
        termuxScriptView.setVisibility(View.GONE);
        cancelView.setVisibility(View.GONE);
        repinToEdge();
    }

    private void startTranscriber() {
        try {
            startForeground(1, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            Transcriber current = createTranscriber();
            synchronized (this) {
                transcriber = current;
            }
            current.start();
        } catch (Exception e) {
            Toast.makeText(this, "Recording failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            closeTranscriber(transcriber);
            exitDictationMode();
        }
    }

    private Transcriber createTranscriber() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String apiKey = preferences.getString(MainActivity.KEY_OPENAI_API_KEY, "");
        String mode = preferences.getString(
                MainActivity.KEY_TRANSCRIPTION_MODE, MainActivity.TRANSCRIPTION_MODE_REALTIME);
        String transcriptionPrompt = preferences.getString(
                MainActivity.KEY_TRANSCRIPTION_PROMPT,
                getString(R.string.default_transcription_prompt));
        Transcriber[] session = new Transcriber[1];
        Transcriber.Listener listener = new Transcriber.Listener() {
            @Override
            public void onAmplitude(float amplitude) {
                mainHandler.post(() -> {
                    if (isDictating && transcriber == session[0]) {
                        waveformView.addAmplitude(amplitude);
                    }
                });
            }

            @Override
            public void onFailure(String message) {
                mainHandler.post(() -> {
                    if (transcriber == session[0]) handleTranscriberFailure(message);
                });
            }

            @Override
            public void onSendMessageDetected() {
                mainHandler.post(() -> {
                    if (isDictating && !isTranscribing && transcriber == session[0]) {
                        sendMessageAfterTranscription = true;
                        finishDictation();
                    }
                });
            }

            @Override
            public void onStopDetected() {
                mainHandler.post(() -> {
                    if (isDictating && !isTranscribing && transcriber == session[0]) {
                        cancelDictation();
                    }
                });
            }
        };
        if (MainActivity.TRANSCRIPTION_MODE_REALTIME.equals(mode)) {
            session[0] = new RealtimeTranscriber(this, apiKey, transcriptionPrompt, listener);
        } else {
            session[0] = new UploadTranscriber(
                    apiKey,
                    getAudioBitrate(),
                    new File(getCacheDir(), "recording.m4a"),
                    transcriptionPrompt,
                    listener);
        }
        return session[0];
    }

    private int getAudioBitrate() {
        String value = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(MainActivity.KEY_AUDIO_BITRATE, MainActivity.DEFAULT_AUDIO_BITRATE);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return Integer.parseInt(MainActivity.DEFAULT_AUDIO_BITRATE);
        }
    }

    private void restoreNonMicrophoneForegroundType() {
        try {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (Exception ignored) {
        }
    }

    private void deliverTranscription(String text, boolean sendMessage) {
        String deliveredText = sendMessage ? SendMessageCommand.removeFromEnd(text) : text;
        boolean typed = typeViaAccessibility(deliveredText);
        if (!typed) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("transcription", deliveredText));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sendMessage) {
            mainHandler.postDelayed(() -> {
                TypeAccessibilityService service = TypeAccessibilityService.getInstance();
                if (service == null || !service.sendEnterKey()) {
                    Toast.makeText(this, "Message transcribed; press Send", Toast.LENGTH_SHORT).show();
                }
            }, 150);
        }
    }

    private void handleTranscriberFailure(String message) {
        if (!isDictating) return;
        Transcriber current = transcriber;
        if (current != null) current.stop();
        restoreNonMicrophoneForegroundType();
        exitDictationMode();
        closeTranscriber(current);
        showTranscriptionError(new Exception(message), null);
    }

    private void closeTranscriber(Transcriber current) {
        synchronized (this) {
            if (transcriber == current) transcriber = null;
        }
        if (current != null) current.close();
        restoreNonMicrophoneForegroundType();
    }

    private boolean typeViaAccessibility(String text) {
        TypeAccessibilityService service = TypeAccessibilityService.getInstance();
        return service != null && service.typeText(text);
    }

    private int edgeTargetX() {
        return onLeft ? 0 : usableWidth() - container.getWidth();
    }

    // Re-pin the docked edge after the container's width changes (entering or
    // leaving dictation grows/shrinks it). Posted so the new width is measured.
    private void repinToEdge() {
        container.post(() -> {
            params.x = edgeTargetX();
            windowManager.updateViewLayout(container, params);
        });
    }

    private void savePositionToEdge() {
        boolean left = params.x + container.getWidth() / 2 < usableWidth() / 2;
        if (left != onLeft) {
            onLeft = left;
            applyChildOrder();
        }
        container.post(() -> {
            ValueAnimator animator = ValueAnimator.ofInt(params.x, edgeTargetX());
            animator.setDuration(200);
            animator.addUpdateListener(animation -> {
                params.x = (int) animation.getAnimatedValue();
                windowManager.updateViewLayout(container, params);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    savePosition();
                }
            });
            animator.start();
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Floating Service Channel",
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (transcriptionErrorDialog != null) {
            transcriptionErrorDialog.dismiss();
            transcriptionErrorDialog = null;
        }
        closeFailedTranscriber();
        if (spinAnimator != null) spinAnimator.cancel();
        Transcriber current = transcriber;
        if (current != null) current.stop();
        closeTranscriber(current);
        executor.shutdownNow();
        if (container != null) detachOverlay();
    }
}
