package com.ross.speechtotext;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

final class TermuxButtonIcon {
    private TermuxButtonIcon() {}

    static Drawable numbered(Resources resources, int position) {
        int size = Math.round(24 * resources.getDisplayMetrics().density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(size / 24f, size / 24f);

        String number = Integer.toString(position);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(11);
        if (paint.measureText(number) > 14) {
            paint.setTextSize(11 * 14 / paint.measureText(number));
        }
        float left = 24 - paint.measureText(number);
        // Make room for the number without painting over the button's background.
        int save = canvas.save();
        canvas.clipOutRect(left - 1, 13, 24, 24);
        Drawable terminal = resources.getDrawable(R.drawable.ic_terminal, null);
        terminal.setBounds(0, 0, 24, 24);
        terminal.draw(canvas);
        canvas.restoreToCount(save);
        canvas.drawText(number, left, 23, paint);
        return new BitmapDrawable(resources, bitmap);
    }
}
