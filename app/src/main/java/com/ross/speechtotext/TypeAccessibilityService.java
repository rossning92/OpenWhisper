package com.ross.speechtotext;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.InputMethod;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

public class TypeAccessibilityService extends AccessibilityService {
    private static TypeAccessibilityService instance;

    public static TypeAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isEnabled(Context context) {
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        for (AccessibilityServiceInfo service : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (context.getPackageName().equals(service.getResolveInfo().serviceInfo.packageName)
                    && TypeAccessibilityService.class.getName().equals(
                            service.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    // Holding Space past this delay toggles dictation instead of typing a space.
    private static final long LONG_PRESS_MS = 300;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean longPressFired = false;
    private final Runnable longPress = () -> {
        FloatingService fs = FloatingService.getInstance();
        if (fs == null) return; // No dictation target; release still types a space.
        longPressFired = true;
        fs.onHotkey();
    };

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // While dictating, Enter confirms and Escape cancels. Swallow both edges
        // so the key never leaks into the field, and act on the release.
        FloatingService fs = FloatingService.getInstance();
        if (fs != null && fs.isDictating() && event.hasNoModifiers()
                && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE)) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    fs.onConfirmKey();
                } else {
                    fs.onCancelKey();
                }
            }
            return true;
        }

        // Only plain Space is a hotkey; leave modified combos (Ctrl+Space, etc.)
        // for the focused app.
        if (event.getKeyCode() != KeyEvent.KEYCODE_SPACE || !event.hasNoModifiers()) {
            return false;
        }
        // Swallow Space and re-inject it on a quick tap. A long-press must not
        // leak a space into the field, so we cannot let the key through on down.
        switch (event.getAction()) {
            case KeyEvent.ACTION_DOWN:
                if (event.getRepeatCount() == 0) {
                    longPressFired = false;
                    handler.postDelayed(longPress, LONG_PRESS_MS);
                }
                return true;
            case KeyEvent.ACTION_UP:
                handler.removeCallbacks(longPress);
                if (!longPressFired) typeText(" ");
                longPressFired = false;
                return true;
        }
        return false;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        FloatingService.startIfReady(this);
        FloatingService floatingService = FloatingService.getInstance();
        if (floatingService != null) floatingService.onAccessibilityServiceConnected(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        FloatingService floatingService = FloatingService.getInstance();
        if (floatingService != null) floatingService.onAccessibilityServiceDisconnected();
        super.onDestroy();
        instance = null;
    }

    public boolean typeText(String text) {
        InputMethod.AccessibilityInputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        ic.commitText(text, 1, null);
        return true;
    }

    public boolean sendEnterKey() {
        InputMethod.AccessibilityInputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        long time = android.os.SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0));
        ic.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0));
        return true;
    }

    private InputMethod.AccessibilityInputConnection getCurrentInputConnection() {
        InputMethod inputMethod = getInputMethod();
        return inputMethod == null ? null : inputMethod.getCurrentInputConnection();
    }
}
