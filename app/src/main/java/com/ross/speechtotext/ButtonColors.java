package com.ross.speechtotext;

import android.content.Context;
import android.graphics.Color;
import com.google.android.material.R;
import com.google.android.material.color.MaterialColors;

final class ButtonColors {
    static final String[] VALUES = {"primary", "secondary", "tertiary", "red", "pink",
            "purple", "deep_purple", "indigo", "blue", "light_blue", "cyan", "teal",
            "green", "light_green", "lime", "yellow", "amber", "orange", "deep_orange",
            "brown", "gray", "blue_gray"};

    // Material Design 500 hues, paired with 100 hues for the idle button state.
    private static final int[][] MATERIAL_COLORS = {
            {0xFFF44336, 0xFFFFCDD2}, {0xFFE91E63, 0xFFF8BBD0},
            {0xFF9C27B0, 0xFFE1BEE7}, {0xFF673AB7, 0xFFD1C4E9},
            {0xFF3F51B5, 0xFFC5CAE9}, {0xFF2196F3, 0xFFBBDEFB},
            {0xFF03A9F4, 0xFFB3E5FC}, {0xFF00BCD4, 0xFFB2EBF2},
            {0xFF009688, 0xFFB2DFDB}, {0xFF4CAF50, 0xFFC8E6C9},
            {0xFF8BC34A, 0xFFDCEDC8}, {0xFFCDDC39, 0xFFF0F4C3},
            {0xFFFFEB3B, 0xFFFFF9C4}, {0xFFFFC107, 0xFFFFECB3},
            {0xFFFF9800, 0xFFFFE0B2}, {0xFFFF5722, 0xFFFFCCBC},
            {0xFF795548, 0xFFD7CCC8}, {0xFF9E9E9E, 0xFFE0E0E0},
            {0xFF607D8B, 0xFFCFD8DC}
    };

    static String normalize(String value) {
        if (value != null) {
            for (String candidate : VALUES) {
                if (candidate.equals(value)) return value;
            }
        }
        return "primary";
    }

    static int[] resolve(Context context, String value, boolean active) {
        String normalized = normalize(value);
        for (int i = 3; i < VALUES.length; i++) {
            if (VALUES[i].equals(normalized)) {
                int color = MATERIAL_COLORS[i - 3][active ? 0 : 1];
                return new int[] {color, isLight(color) ? Color.BLACK : Color.WHITE};
            }
        }
        int background;
        int foreground;
        switch (normalized) {
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

    private static boolean isLight(int color) {
        // The crossover where black has greater WCAG contrast than white.
        return Color.luminance(color) > 0.179;
    }
}
