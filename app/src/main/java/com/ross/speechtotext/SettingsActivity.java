package com.ross.speechtotext;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings");
        }
        FloatingService.startIfReady(this);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable refresh = new Runnable() {
            @Override public void run() {
                updateOfflineModel();
                handler.postDelayed(this, 1000);
            }
        };
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            findPreference("offline_model").setOnPreferenceClickListener(pref -> {
                requireContext().startForegroundService(new Intent(requireContext(), ModelDownloadService.class));
                return true;
            });
            SwitchPreferenceCompat featurePref = findPreference(MainActivity.KEY_FEATURE_ENABLED);
            if (featurePref != null) {
                featurePref.setOnPreferenceChangeListener((pref, newValue) -> {
                    if ((Boolean) newValue) {
                        // Preference listeners run before the new value is persisted, so
                        // defer the readiness check until the switch has been saved.
                        requireActivity().getWindow().getDecorView().post(
                                () -> FloatingService.startIfReady(requireContext()));
                    } else {
                        FloatingService.stop(requireContext());
                    }
                    return true;
                });
            }
            Preference buttonSizePref = findPreference(MainActivity.KEY_FLOATING_BUTTON_SIZE);
            if (buttonSizePref != null) {
                buttonSizePref.setOnPreferenceChangeListener((pref, newValue) -> {
                    requireActivity().getWindow().getDecorView().post(() -> {
                        FloatingService service = FloatingService.getInstance();
                        if (service != null) service.updateControlSize();
                    });
                    return true;
                });
            }
            SwitchPreferenceCompat accessibilityPref = findPreference("accessibility_status");
            if (accessibilityPref != null) {
                accessibilityPref.setOnPreferenceChangeListener((pref, newValue) -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    return false;
                });
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            updateAccessibilityStatus();
            handler.post(refresh);
        }

        @Override public void onPause() {
            handler.removeCallbacks(refresh);
            super.onPause();
        }

        private void updateOfflineModel() {
            Preference pref = findPreference("offline_model");
            if (!OfflineEngine.supported()) {
                pref.setEnabled(false);
                pref.setSummary("Offline English is not supported by this CPU");
                return;
            }
            boolean ready = OfflineModel.ready(OfflineModel.file(requireContext()));
            pref.setEnabled(!ready && !ModelDownloadService.downloading);
            pref.setSummary(ready ? "Ready. English only; audio stays on this device." : ModelDownloadService.status);
        }

        private void updateAccessibilityStatus() {
            SwitchPreferenceCompat pref = findPreference("accessibility_status");
            if (pref == null) return;
            pref.setChecked(TypeAccessibilityService.isEnabled(requireContext()));
        }

    }
}
