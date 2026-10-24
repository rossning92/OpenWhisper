package com.ross.speechtotext;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Toast;

public final class ModelDownloadActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OfflineModel model = OfflineModel.selected(this);
        if (model.ready(this)) {
            finish();
            return;
        }
        if (ModelDownloadService.downloading) {
            Toast.makeText(this, R.string.download_in_progress, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_model_title)
                .setMessage(getString(R.string.download_model_message, model.label, model.megabytes()))
                .setPositiveButton(R.string.download, (dialog, which) -> {
                    ModelDownloadService.startDownload(this, model);
                    finish();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }
}
