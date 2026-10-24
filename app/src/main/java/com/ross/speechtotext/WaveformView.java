package com.ross.speechtotext;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import com.google.android.material.color.MaterialColors;

import java.util.Arrays;

public class WaveformView extends View {
    private static final int BAR_COUNT = 14;
    private final float[] amplitudes = new float[BAR_COUNT];
    private final float[] displayed = new float[BAR_COUNT];
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final int surfaceColor;
    private float barWidth;
    private float barGap;
    private float cornerRadius;
    private float minBarHeight;

    public WaveformView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        barPaint.setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0));
        surfaceColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0);
        setScale(1f);
        setClipToOutline(true);
    }

    public void setScale(float scale) {
        barWidth = 3f * density * scale;
        barGap = 1.5f * density * scale;
        cornerRadius = 1.5f * density * scale;
        minBarHeight = 2f * density * scale;

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(surfaceColor);
        bg.setCornerRadius(20f * density * scale);
        setBackground(bg);
        invalidate();
    }

    public void addAmplitude(float amplitude) {
        System.arraycopy(amplitudes, 1, amplitudes, 0, BAR_COUNT - 1);
        amplitudes[BAR_COUNT - 1] = amplitude;
        invalidate();
    }

    public void clear() {
        Arrays.fill(amplitudes, 0);
        Arrays.fill(displayed, 0);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float step = barWidth + barGap;
        float total = BAR_COUNT * step - barGap;
        float x0 = (getWidth() - total) / 2f;
        float cy = getHeight() / 2f;
        float maxH = getHeight() * 0.65f;

        for (int i = 0; i < BAR_COUNT; i++) {
            displayed[i] += (amplitudes[i] - displayed[i]) * 0.35f;
            float h = minBarHeight + displayed[i] * maxH;
            float left = x0 + i * step;
            rect.set(left, cy - h / 2f, left + barWidth, cy + h / 2f);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, barPaint);
        }
        postInvalidateOnAnimation();
    }
}
