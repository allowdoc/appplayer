package org.headless.runner.core;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class SyntheticDrawable extends Drawable {
    private final int width;
    private final int height;

    public SyntheticDrawable() {
        this(48, 48);
    }

    public SyntheticDrawable(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void draw(Canvas canvas) {}

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    public int getMinimumWidth() {
        return width;
    }

    @Override
    public int getMinimumHeight() {
        return height;
    }

    @Override
    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override
            public Drawable newDrawable() {
                return new SyntheticDrawable(width, height);
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        };
    }

    @Override
    public Drawable mutate() {
        return this;
    }
}
