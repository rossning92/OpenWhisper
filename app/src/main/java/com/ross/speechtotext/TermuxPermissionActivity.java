package com.ross.speechtotext;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

public class TermuxPermissionActivity extends Activity {
    static final String RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND";
    static final String EXTRA_TRANSCRIPTION = "transcription";
    private static final int REQUEST_RUN_COMMAND = 1;

    private String transcription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        transcription = getIntent().getStringExtra(EXTRA_TRANSCRIPTION);
        if (transcription == null) {
            finish();
            return;
        }
        if (checkSelfPermission(RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            runTermuxScriptAndFinish();
        } else {
            requestPermissions(new String[] { RUN_COMMAND_PERMISSION }, REQUEST_RUN_COMMAND);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RUN_COMMAND) return;
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            runTermuxScriptAndFinish();
        } else {
            Toast.makeText(this, R.string.termux_permission_denied, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void runTermuxScriptAndFinish() {
        FloatingService.runTermuxScript(this, transcription);
        finish();
    }
}
