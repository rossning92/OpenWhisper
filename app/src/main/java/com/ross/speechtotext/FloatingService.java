package com.ross.speechtotext;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import androidx.preference.PreferenceManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;

import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
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
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.RelativeCornerSize;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingService extends Service {
    static boolean isFeatureEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(MainActivity.KEY_FEATURE_ENABLED, true);
    }

    static void startIfReady(Context context) {
        boolean canShowOverlay = Settings.canDrawOverlays(context)
                || TypeAccessibilityService.getInstance() != null;
        if (isFeatureEnabled(context) && canShowOverlay) {
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

    private volatile Transcriber transcriber;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String CHANNEL_ID = "FloatingServiceChannel";
    private static final String TERMUX_BASH_PATH =
            "/data/data/com.termux/files/usr/bin/bash";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private Context themedContext;
    private Notification notification;
    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mainHandler.post(() -> {
                    if (isDictating) cancelDictation();
                });
            }
        }
    };

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
        registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        themedContext = com.google.android.material.color.DynamicColors.wrapContextIfAvailable(
                new ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar));

        useSmallControls = readUseSmallControls();
        updateWaveformDimensions();

        floatingView = createFab(R.drawable.ic_graphic_eq);
        floatingView.setContentDescription("Start dictation");
        floatingView.setHapticFeedbackEnabled(true);
        floatingView.setOnClickListener(v -> toggleDictation());

        cancelView = createFab(R.drawable.ic_close);
        cancelView.setContentDescription("Cancel dictation");
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
        GradientDrawable controlGap = new GradientDrawable();
        controlGap.setColor(android.graphics.Color.TRANSPARENT);
        controlGap.setSize(Math.round(4 * getResources().getDisplayMetrics().density), 1);
        container.setDividerDrawable(controlGap);
        container.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

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
        fab.setShapeAppearanceModel(fab.getShapeAppearanceModel().toBuilder()
                .setAllCornerSizes(new RelativeCornerSize(0.5f)).build());
        fab.setEnsureMinTouchTargetSize(true);
        applyFabSize(fab);
        fab.setUseCompatPadding(true);
        fab.setCompatElevation(0f);
        fab.setCompatHoveredFocusedTranslationZ(0f);
        fab.setCompatPressedTranslationZ(0f);
        applyFabColors(fab, false);
        return fab;
    }

    private void applyFabColors(FloatingActionButton fab, boolean active) {
        String color = PreferenceManager.getDefaultSharedPreferences(this).getString(
                MainActivity.KEY_FLOATING_BUTTON_COLOR, "primary");
        int[] colors = ButtonColors.resolve(fab.getContext(), color, active);
        fab.setBackgroundTintList(ColorStateList.valueOf(colors[0]));
        fab.setImageTintList(ColorStateList.valueOf(colors[1]));
    }

    public void updateControlColors() {
        applyFabColors(floatingView, isDictating);
        applyFabColors(termuxScriptView, false);
        applyFabColors(cancelView, false);
    }

    private boolean readUseSmallControls() {
        String buttonSize = PreferenceManager.getDefaultSharedPreferences(this).getString(
                MainActivity.KEY_FLOATING_BUTTON_SIZE,
                MainActivity.FLOATING_BUTTON_SIZE_NORMAL);
        return MainActivity.FLOATING_BUTTON_SIZE_SMALL.equals(buttonSize);
    }

    private float getControlScale() {
        return useSmallControls ? 1f : 1.2f;
    }

    private void applyFabSize(FloatingActionButton fab) {
        float density = getResources().getDisplayMetrics().density;
        fab.setCustomSize(Math.round((useSmallControls ? 40 : 48) * density));
        fab.setMaxImageSize(Math.round((useSmallControls ? 22 : 24) * density));
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
            PowerManager powerManager = getSystemService(PowerManager.class);
            if (powerManager == null || !powerManager.isInteractive()) return;
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
        isDictating = true;
        applyFabColors(floatingView, true);
        floatingView.setImageResource(R.drawable.ic_send);
        floatingView.setContentDescription("Stop and transcribe");
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
                        runTermuxScript(this, text);
                    } else {
                        deliverTranscription(text);
                    }
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

    static void runTermuxScript(Context context, String text) {
        String scriptPath = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("termux_script_path", "").trim();
        if (scriptPath.startsWith("~/")) {
            scriptPath = TERMUX_HOME + scriptPath.substring(1);
        }
        if (!scriptPath.startsWith("/") || scriptPath.endsWith("/")) {
            Toast.makeText(context,
                    "Set a valid Termux script path in Settings (absolute path or ~/ path).",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (context.checkSelfPermission(TermuxPermissionActivity.RUN_COMMAND_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            Intent permissionIntent = new Intent(context, TermuxPermissionActivity.class);
            permissionIntent.putExtra(TermuxPermissionActivity.EXTRA_TRANSCRIPTION, text);
            permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(permissionIntent);
            return;
        }
        Intent intent = new Intent("com.termux.RUN_COMMAND");
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH_PATH);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                new String[] { "--", scriptPath, text });
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        // Open Termux and switch to the new session.
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            if (context.startService(intent) == null) {
                Toast.makeText(context, "Unable to start Termux command", Toast.LENGTH_LONG).show();
            }
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
        floatingView.setContentDescription("Transcribing");
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
        floatingView.setContentDescription("Start dictation");
        applyFabColors(floatingView, false);
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
            public void onSpeechEnded() {
                mainHandler.post(() -> {
                    if (isDictating && !isTranscribing && transcriber == session[0]) {
                        finishDictation();
                    }
                });
            }

            @Override
            public void onFailure(String message) {
                mainHandler.post(() -> {
                    if (transcriber == session[0]) handleTranscriberFailure(message);
                });
            }
        };
        session[0] = new LocalTranscriber(this, listener);
        return session[0];
    }

    private void restoreNonMicrophoneForegroundType() {
        try {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (Exception ignored) {
        }
    }

    private void deliverTranscription(String text) {
        boolean typed = typeViaAccessibility(text);
        if (!typed) {
            String action = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("no_input_action", "clipboard");
            if ("termux".equals(action)) {
                runTermuxScript(this, text);
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("transcription", text));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
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
        unregisterReceiver(screenOffReceiver);
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
        OfflineEngine.releaseAsync();
        if (container != null) detachOverlay();
    }
}
