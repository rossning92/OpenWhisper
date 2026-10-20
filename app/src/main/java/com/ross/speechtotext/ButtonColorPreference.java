package com.ross.speechtotext;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.google.android.material.button.MaterialButton;

public final class ButtonColorPreference extends Preference {
    private String selected = "primary";

    public ButtonColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_button_color);
        setSelectable(false);
    }

    @Override protected void onSetInitialValue(Object defaultValue) {
        selected = ButtonColors.normalize(getPersistedString("primary"));
        persistString(selected);
    }

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        LinearLayout row = (LinearLayout) holder.findViewById(R.id.color_choices);
        row.removeAllViews();
        float density = getContext().getResources().getDisplayMetrics().density;
        int size = Math.round(48 * density);
        int gap = Math.round(16 * density);
        for (int i = 0; i < ButtonColors.VALUES.length; i++) {
            String value = ButtonColors.VALUES[i];
            boolean checked = value.equals(selected);
            int[] colors = ButtonColors.resolve(getContext(), value, false);
            MaterialButton swatch = new MaterialButton(getContext());
            swatch.setCheckable(true);
            swatch.setChecked(checked);
            swatch.setText("");
            swatch.setAllCaps(false);
            swatch.setContentDescription(i == 0 ? getContext().getString(R.string.default_color)
                    : getContext().getString(R.string.color_option, i + 1));
            swatch.setBackgroundTintList(ColorStateList.valueOf(colors[0]));
            swatch.setTextColor(colors[1]);
            swatch.setIconTint(ColorStateList.valueOf(colors[1]));
            swatch.setIconResource(R.drawable.ic_graphic_eq);
            swatch.setIconSize(Math.round(24 * density));
            swatch.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            swatch.setIconPadding(0);
            swatch.setStrokeWidth(checked ? Math.round(2 * density) : 0);
            swatch.setStrokeColor(ColorStateList.valueOf(colors[1]));
            swatch.setMinimumWidth(0);
            swatch.setMinimumHeight(0);
            swatch.setInsetTop(0);
            swatch.setInsetBottom(0);
            swatch.setPadding(0, 0, 0, 0);
            swatch.setCornerRadius(size / 2);
            swatch.setOnClickListener(view -> {
                selected = value;
                persistString(value);
                notifyChanged();
                FloatingService service = FloatingService.getInstance();
                if (service != null) service.updateControlColors();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            if (i > 0) params.setMarginStart(gap);
            row.addView(swatch, params);
        }
    }
}
