package com.ross.speechtotext;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class ButtonColorPreference extends Preference {
    private static final int COLUMN_COUNT = 5;
    private static final String[] CHOICES = {"primary", "red", "pink", "purple", "blue",
            "cyan", "teal", "green", "orange", "gray"};
    private String selected = "primary";

    public ButtonColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_widget_color);
    }

    @Override protected void onSetInitialValue(Object defaultValue) {
        selected = ButtonColors.normalize(getPersistedString("primary"));
        persistString(selected);
    }

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        ImageView swatch = (ImageView) holder.findViewById(R.id.selected_color);
        swatch.setBackground(makeSwatch());
    }

    @Override protected void onClick() {
        Context context = getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(48 * density);
        int gap = Math.round(6 * density);
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        grid.setPadding(gap, gap * 2, gap, gap * 2);
        int rowCount = (CHOICES.length + COLUMN_COUNT - 1) / COLUMN_COUNT;
        for (int i = 0; i < rowCount; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, size + gap * 2));
        }
        String[] labels = context.getResources().getStringArray(
                R.array.floating_button_color_entries);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(getTitle())
                .setView(grid)
                .create();
        for (int i = 0; i < CHOICES.length; i++) {
            String value = CHOICES[i];
            boolean checked = value.equals(selected);
            int[] colors = ButtonColors.resolve(context, value, false);
            MaterialButton swatch = new MaterialButton(context);
            swatch.setContentDescription(labels[i]);
            swatch.setCheckable(true);
            swatch.setChecked(checked);
            swatch.setBackgroundTintList(ColorStateList.valueOf(colors[0]));
            swatch.setIconTint(ColorStateList.valueOf(colors[1]));
            if (checked) swatch.setIconResource(R.drawable.ic_check);
            swatch.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            swatch.setIconPadding(0);
            swatch.setMinimumWidth(0);
            swatch.setMinimumHeight(0);
            swatch.setInsetTop(0);
            swatch.setInsetBottom(0);
            swatch.setPadding(0, 0, 0, 0);
            swatch.setCornerRadius(size / 2);
            swatch.setOnClickListener(view -> {
                if (callChangeListener(value)) {
                    selected = value;
                    persistString(value);
                    notifyChanged();
                    dialog.dismiss();
                }
            });
            FrameLayout cell = new FrameLayout(context);
            FrameLayout.LayoutParams swatchParams = new FrameLayout.LayoutParams(
                    size, size, Gravity.CENTER);
            cell.addView(swatch, swatchParams);
            LinearLayout row = (LinearLayout) grid.getChildAt(i / COLUMN_COUNT);
            row.addView(cell, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        dialog.show();
    }

    private GradientDrawable makeSwatch() {
        int color = ButtonColors.resolve(getContext(), selected, false)[0];
        GradientDrawable swatch = new GradientDrawable();
        swatch.setShape(GradientDrawable.OVAL);
        swatch.setColor(color);
        swatch.setSize(dp(24), dp(24));
        return swatch;
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }
}
