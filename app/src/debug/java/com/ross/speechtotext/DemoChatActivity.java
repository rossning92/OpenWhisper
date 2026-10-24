package com.ross.speechtotext;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

/** Local screenshot fixture. No network, contacts, or message storage. Debug builds only. */
public class DemoChatActivity extends AppCompatActivity {
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int color(int attr) { return MaterialColors.getColor(this, attr, "DemoChat"); }

    private TextView text(String value, int size, int color) {
        TextView view = new MaterialTextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void bubble(LinearLayout chat, String message, boolean outgoing) {
        TextView view = text(message, 18, color(outgoing
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
        view.setPadding(dp(18), dp(12), dp(18), dp(12));
        view.setMaxWidth(dp(290));
        if (outgoing) view.setContentDescription("Sent message");
        GradientDrawable background = new GradientDrawable();
        background.setColor(color(outgoing ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceVariant));
        background.setCornerRadius(dp(20));
        view.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.gravity = outgoing ? Gravity.END : Gravity.START;
        params.topMargin = dp(16);
        chat.addView(view, params);
    }

    @Override public void onCreate(Bundle state) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(state);
        int surface = color(com.google.android.material.R.attr.colorSurface);
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(surface);
        WindowInsetsControllerCompat bars = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(MaterialColors.isColorLight(surface));
        bars.setAppearanceLightNavigationBars(MaterialColors.isColorLight(surface));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(12));
        root.setBackgroundColor(surface);
        TextView title = text("Alex", 26, color(com.google.android.material.R.attr.colorOnSurface));
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);
        LinearLayout chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        root.addView(chat, new LinearLayout.LayoutParams(-1, 0, 1));
        bubble(chat, "Hey! When will you be here?", false);
        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        TextInputLayout field = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        field.setHint("Message Alex");
        field.setBoxCornerRadii(dp(24), dp(24), dp(24), dp(24));
        TextInputEditText input = new TextInputEditText(field.getContext());
        input.setContentDescription("Message Alex");
        input.setTextSize(18);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.addView(input, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, -2, 1);
        fieldParams.setMarginEnd(dp(12));
        composer.addView(field, fieldParams);
        MaterialButton send = new MaterialButton(this);
        send.setText("Send");
        send.setContentDescription("Send message");
        composer.addView(send);
        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) return;
            bubble(chat, message, true);
            input.setText("");
        });
        root.addView(composer);
        setContentView(root);
    }
}
