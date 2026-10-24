package com.ross.speechtotext;

import android.content.Context;
import com.google.android.material.R;
import com.google.android.material.color.MaterialColors;

final class ButtonColors {
    static final String[] VALUES = {"primary", "secondary", "tertiary"};

    static String normalize(String value) {
        return "secondary".equals(value) || "tertiary".equals(value) ? value : "primary";
    }

    static int[] resolve(Context context, String value, boolean active) {
        int background;
        int foreground;
        switch (normalize(value)) {
            case "secondary":
                background = active ? R.attr.colorSecondary : R.attr.colorSecondaryContainer;
                foreground = active ? R.attr.colorOnSecondary : R.attr.colorOnSecondaryContainer;
                break;
            case "tertiary":
                background = active ? R.attr.colorTertiary : R.attr.colorTertiaryContainer;
                foreground = active ? R.attr.colorOnTertiary : R.attr.colorOnTertiaryContainer;
                break;
            default:
                background = active ? R.attr.colorPrimary : R.attr.colorPrimaryContainer;
                foreground = active ? R.attr.colorOnPrimary : R.attr.colorOnPrimaryContainer;
                break;
        }
        return new int[] {MaterialColors.getColor(context, background, 0),
                MaterialColors.getColor(context, foreground, 0)};
    }
}
