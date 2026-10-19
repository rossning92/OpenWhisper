package com.ross.speechtotext;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
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
            findPreference(OfflineModel.PREFERENCE).setOnPreferenceClickListener(pref -> {
                showModelPicker();
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

        private AlertDialog modelDialog;
        private TextView[] modelStatuses;
        private Button[] modelActions;
        private OfflineModel pendingDownload;

        private void showModelPicker() {
            LinearLayout rows = new LinearLayout(requireContext());
            rows.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (24 * getResources().getDisplayMetrics().density);
            rows.setPadding(padding, padding / 2, padding, padding / 2);
            modelStatuses = new TextView[OfflineModel.ALL.length];
            modelActions = new Button[OfflineModel.ALL.length];
            for (int i = 0; i < OfflineModel.ALL.length; i++) {
                OfflineModel model = OfflineModel.ALL[i];
                TextView title = new TextView(requireContext());
                title.setText(model.label + " - " + model.languages);
                title.setTextSize(16);
                rows.addView(title);
                modelStatuses[i] = new TextView(requireContext());
                rows.addView(modelStatuses[i]);
                Button action = new Button(requireContext());
                action.setAllCaps(false);
                action.setOnClickListener(view -> {
                    if (ModelDownloadService.isDownloading(model)) {
                        requireContext().startService(new Intent(requireContext(), ModelDownloadService.class)
                                .setAction(ModelDownloadService.CANCEL));
                    } else if (model.ready(requireContext())) {
                        model.select(requireContext());
                        modelDialog.dismiss();
                    } else {
                        ModelDownloadService.startDownload(requireContext(), model);
                        pendingDownload = model;
                    }
                    updateOfflineModel();
                });
                modelActions[i] = action;
                rows.addView(action);
            }
            ScrollView scroll = new ScrollView(requireContext());
            scroll.addView(rows);
            modelDialog = new AlertDialog.Builder(requireContext()).setTitle("Offline model")
                    .setView(scroll).setPositiveButton("Done", null).create();
            modelDialog.setOnDismissListener(dialog -> {
                modelDialog = null;
                modelStatuses = null;
                modelActions = null;
            });
            modelDialog.show();
            updateOfflineModel();
        }

        @Override public void onDestroyView() {
            if (modelDialog != null) modelDialog.dismiss();
            super.onDestroyView();
        }

        private void updateOfflineModel() {
            Preference pref = findPreference(OfflineModel.PREFERENCE);
            if (!OfflineEngine.supported()) {
                pref.setEnabled(false);
                pref.setSummary("Offline transcription is not supported by this CPU");
                return;
            }
            OfflineModel selected = OfflineModel.selected(requireContext());
            if (pendingDownload != null && ModelDownloadService.statusFor(pendingDownload) != null) {
                pendingDownload = null;
            }
            pref.setSummary(selected.label + (selected.ready(requireContext())
                    ? " - Ready" : " - Download required"));
            for (OfflineModel model : OfflineModel.ALL) {
                if (ModelDownloadService.isDownloading(model)) {
                    pref.setSummary(selected.label + " - " + ModelDownloadService.statusFor(model));
                    break;
                }
            }
            if (modelDialog == null) return;
            for (int i = 0; i < OfflineModel.ALL.length; i++) {
                OfflineModel model = OfflineModel.ALL[i];
                boolean ready = model.ready(requireContext());
                boolean downloading = ModelDownloadService.isDownloading(model);
                String status = ModelDownloadService.statusFor(model);
                String action;
                if (downloading) {
                    action = "Cancel";
                } else if (ready) {
                    status = model == selected ? "Ready - Selected" : "Ready";
                    action = "Select";
                } else {
                    action = (status == null ? "Download - " : "Retry - ") + model.megabytes() + " MB";
                    if (pendingDownload == model) status = "Starting download...";
                    else if (status == null) status = "Download required";
                }
                modelStatuses[i].setText(status);
                modelActions[i].setText(action);
                modelActions[i].setEnabled(pendingDownload == null
                        && (downloading || !ModelDownloadService.downloading));
            }
        }

        private void updateAccessibilityStatus() {
            SwitchPreferenceCompat pref = findPreference("accessibility_status");
            if (pref == null) return;
            pref.setChecked(TypeAccessibilityService.isEnabled(requireContext()));
        }

    }
}
