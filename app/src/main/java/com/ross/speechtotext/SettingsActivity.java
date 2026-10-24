package com.ross.speechtotext;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
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
            EditTextPreference scriptPath = findPreference("termux_script_path");
            scriptPath.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            scriptPath.setOnBindEditTextListener(edit -> {
                edit.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                edit.setSingleLine(true);
            });
            EditTextPreference dictionary = findPreference("custom_dictionary");
            dictionary.setOnBindEditTextListener(edit -> {
                edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                edit.setMinLines(1);
                edit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            });
            ListPreference modelPref = findPreference(OfflineModel.PREFERENCE);
            CharSequence[] entries = new CharSequence[OfflineModel.ALL.length];
            CharSequence[] values = new CharSequence[OfflineModel.ALL.length];
            for (int i = 0; i < OfflineModel.ALL.length; i++) {
                OfflineModel model = OfflineModel.ALL[i];
                int language = model == OfflineModel.PARAKEET
                        ? R.string.language_english : R.string.language_multilingual;
                entries[i] = getString(R.string.model_title, model.label, getString(language));
                values[i] = model.key;
            }
            modelPref.setEntries(entries);
            modelPref.setEntryValues(values);
            modelPref.setOnPreferenceChangeListener((pref, newValue) -> {
                OfflineEngine.releaseAsync();
                requireActivity().getWindow().getDecorView().post(() -> {
                    OfflineModel model = OfflineModel.selected(requireContext());
                    updateOfflineModel();
                    if (!model.ready(requireContext())) {
                        startActivity(new Intent(requireContext(), ModelDownloadActivity.class));
                    }
                });
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
            SwitchPreferenceCompat termuxButtonPref = findPreference(
                    MainActivity.KEY_SHOW_TERMUX_BUTTON);
            if (termuxButtonPref != null) {
                termuxButtonPref.setOnPreferenceChangeListener((pref, newValue) -> {
                    requireActivity().getWindow().getDecorView().post(() -> {
                        FloatingService service = FloatingService.getInstance();
                        if (service != null) service.updateTermuxButtonVisibility();
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
            ListPreference modelPref = findPreference(OfflineModel.PREFERENCE);
            if (!OfflineEngine.supported()) {
                modelPref.setEnabled(false);
                modelPref.setSummaryProvider(pref -> getString(R.string.offline_unsupported));
                return;
            }
            OfflineModel model = OfflineModel.selected(requireContext());
            String status = ModelDownloadService.statusFor(model);
            modelPref.setEnabled(!ModelDownloadService.downloading);
            String summary = status != null ? status : getString(R.string.model_summary, model.label,
                    getString(model.ready(requireContext()) ? R.string.ready : R.string.download_required));
            modelPref.setSummaryProvider(pref -> summary);
        }

        private void updateAccessibilityStatus() {
            SwitchPreferenceCompat pref = findPreference("accessibility_status");
            if (pref == null) return;
            pref.setChecked(TypeAccessibilityService.isEnabled(requireContext()));
        }

    }
}
