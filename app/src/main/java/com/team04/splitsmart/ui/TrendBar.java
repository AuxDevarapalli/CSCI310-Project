package com.team04.splitsmart.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.google.android.material.color.MaterialColors;

/** Draws one column on the chart's shared scale. The parent supplies the touch target and label. */
public final class TrendBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float corner;
    private final int primary;
    private final int muted;
    private float fraction;
    private float targetFraction = -1;
    private int targetColor;
    private final ArgbEvaluator colors = new ArgbEvaluator();
    private ValueAnimator animation;

    public TrendBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        corner = 7 * getResources().getDisplayMetrics().density;
        primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        muted = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);
    }

    void setValue(float target, boolean selected, boolean animate) {
        int color = selected ? primary : muted;
        if (target == targetFraction && color == targetColor) {
            return;
        }
        targetFraction = target;
        targetColor = color;
        if (animation != null) {
            animation.cancel();
        }
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            fraction = target;
            paint.setColor(color);
            invalidate();
            return;
        }
        float startFraction = fraction;
        int startColor = paint.getColor();
        animation = ValueAnimator.ofFloat(0, 1);
        animation.setDuration(330);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(value -> {
            float progress = (float) value.getAnimatedValue();
            fraction = startFraction + (target - startFraction) * progress;
            paint.setColor((int) colors.evaluate(progress, startColor, color));
            invalidate();
        });
        animation.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float bottom = getHeight();
        float left = getWidth() * .23f;
        float right = getWidth() - left;
        if (fraction > 0) {
            canvas.drawRoundRect(left, bottom * (1 - fraction), right, bottom,
                    corner, corner, paint);
        } else {
            float radius = Math.min(corner * .42f, (right - left) / 2);
            canvas.drawCircle(getWidth() / 2f, bottom - radius, radius, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animation != null) {
            animation.end();
        }
        super.onDetachedFromWindow();
    }
}
