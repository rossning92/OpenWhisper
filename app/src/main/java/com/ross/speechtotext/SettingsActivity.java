package com.ross.speechtotext;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.ListPreference;
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
            getSupportActionBar().setTitle(getApplicationInfo().loadLabel(getPackageManager()));
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
            ListPreference modelPref = findPreference(OfflineModel.PREFERENCE);
            if (modelPref != null) {
                CharSequence[] entries = new CharSequence[OfflineModel.ALL.length];
                CharSequence[] values = new CharSequence[OfflineModel.ALL.length];
                for (int i = 0; i < OfflineModel.ALL.length; i++) {
                    OfflineModel model = OfflineModel.ALL[i];
                    entries[i] = model.downloadLabel() + " - " + model.languages;
                    values[i] = model.key;
                }
                modelPref.setEntries(entries);
                modelPref.setEntryValues(values);
                modelPref.setOnPreferenceChangeListener((pref, newValue) -> {
                    OfflineEngine.releaseAsync();
                    requireActivity().getWindow().getDecorView().post(this::updateOfflineModel);
                    return true;
                });
            }
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
            ListPreference modelPref = findPreference(OfflineModel.PREFERENCE);
            if (!OfflineEngine.supported()) {
                pref.setEnabled(false);
                if (modelPref != null) modelPref.setEnabled(false);
                pref.setSummary("Offline transcription is not supported by this CPU");
                return;
            }
            OfflineModel model = OfflineModel.selected(requireContext());
            boolean ready = model.ready(requireContext());
            if (modelPref != null) modelPref.setEnabled(!ModelDownloadService.downloading);
            pref.setEnabled(!ready && !ModelDownloadService.downloading);
            String status = ModelDownloadService.statusFor(model);
            pref.setSummary(ready
                    ? "Ready. " + model.languages + "; audio stays on this device."
                    : (status != null ? status : "Download " + model.downloadLabel()));
        }

        private void updateAccessibilityStatus() {
            SwitchPreferenceCompat pref = findPreference("accessibility_status");
            if (pref == null) return;
            pref.setChecked(TypeAccessibilityService.isEnabled(requireContext()));
        }

    }
}
