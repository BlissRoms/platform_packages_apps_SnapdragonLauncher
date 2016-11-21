/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.animation.FloatArrayEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.android.launcher3.util.Thunk;

import java.util.Arrays;

import org.codeaurora.snaplauncher.R;

public class DragView extends View {
    public static int COLOR_CHANGE_DURATION = 120;

    @Thunk static float sDragAlpha = 1f;

    private Bitmap mBitmap;
    private Bitmap mCrossFadeBitmap;
    @Thunk Paint mPaint;
    private int mRegistrationX;
    private int mRegistrationY;

    private Point mDragVisualizeOffset = null;
    private Rect mDragRegion = null;
    private DragLayer mDragLayer = null;
    private boolean mHasDrawn = false;
    @Thunk float mCrossFadeProgress = 0f;

    ValueAnimator mAnim;
    @Thunk float mOffsetX = 0.0f;
    @Thunk float mOffsetY = 0.0f;
    private float mInitialScale = 1f;
    // The intrinsic icon scale factor is the scale factor for a drag icon over the workspace
    // size.  This is ignored for non-icons.
    private float mIntrinsicIconScale = 1f;

    @Thunk float[] mCurrentFilter;
    private ValueAnimator mFilterAnimator;

    private Launcher mLauncher;
    private int mRadius;
    private Paint mCirclePaint;
    private Paint mSelectBitmapPaint;
    private Paint mNumberPaint;
    private int mNumberTextSize;
    private float mCornerProgress = 0.0f;
    private int mNumber;
    private ValueAnimator mSelectAnim;

    /**
     * Construct the drag view.
     * <p>
     * The registration point is the point inside our view that the touch events should
     * be centered upon.
     *
     * @param launcher The Launcher instance
     * @param bitmap The view that we're dragging around.  We scale it up when we draw it.
     * @param registrationX The x coordinate of the registration point.
     * @param registrationY The y coordinate of the registration point.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public DragView(Launcher launcher, Bitmap bitmap, int registrationX, int registrationY,
            int left, int top, int width, int height, final float initialScale) {
        super(launcher);
        mDragLayer = launcher.getDragLayer();
        mInitialScale = initialScale;

        mLauncher = launcher;

        final Resources res = getResources();
        final float scaleDps = res.getDimensionPixelSize(R.dimen.dragViewScale);
        final float scale = (width + scaleDps) / width;

        // Set the initial scale to avoid any jumps
        setScaleX(initialScale);
        setScaleY(initialScale);

        // Animate the view into the correct position
        mAnim = LauncherAnimUtils.ofFloat(this, 0f, 1f);
        mAnim.setDuration(150);
        mAnim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float value = (Float) animation.getAnimatedValue();

                final int deltaX = (int) (-mOffsetX);
                final int deltaY = (int) (-mOffsetY);

                mOffsetX += deltaX;
                mOffsetY += deltaY;
                setScaleX(initialScale + (value * (scale - initialScale)));
                setScaleY(initialScale + (value * (scale - initialScale)));
                if (sDragAlpha != 1f) {
                    setAlpha(sDragAlpha * value + (1f - value));
                }

                if (getParent() == null) {
                    animation.cancel();
                } else {
                    setTranslationX(getTranslationX() + deltaX);
                    setTranslationY(getTranslationY() + deltaY);
                }
            }
        });

        mBitmap = Bitmap.createBitmap(bitmap, left, top, width, height);
        setDragRegion(new Rect(0, 0, width, height));

        // The point in our scaled bitmap that the touch events are located
        mRegistrationX = registrationX;
        mRegistrationY = registrationY;

        // Force a measure, because Workspace uses getMeasuredHeight() before the layout pass
        int ms = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        measure(ms, ms);
        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

        if (Utilities.ATLEAST_LOLLIPOP) {
            setElevation(getResources().getDimension(R.dimen.drag_elevation));
        }

        if (mLauncher.mWorkspace.getState() == Workspace.State.ARRANGE){
            mRadius = res.getDimensionPixelSize(R.dimen.default_arrange_select_circle_radius);
        }else {
            mRadius = 0;
        }

        mCirclePaint = new Paint();
        mCirclePaint.setAntiAlias(true);
        mCirclePaint.setStyle(Paint.Style.FILL);
        mCirclePaint.setColor(Color.WHITE);

        mSelectBitmapPaint = new Paint();
        mSelectBitmapPaint.setAntiAlias(true);

        mNumberPaint = new Paint();
        mNumberPaint.setAntiAlias(true);
        mNumberPaint.setColor(res.getColor(R.color.default_arrange_select_number_color));
        Typeface typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        mNumberPaint.setTypeface(typeface);
        mNumberTextSize = res.getDimensionPixelSize(R.dimen.default_arrange_select_number_textsize);
        mNumberPaint.setTextSize(mNumberTextSize);

        mNumber = mLauncher.getBatchArrangeAppsAll().size();
        mSelectAnim = LauncherAnimUtils.ofFloat(this, 0f, 1f);
        mSelectAnim.setDuration(300);
        mSelectAnim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float value = (Float) animation.getAnimatedValue();
                mCornerProgress = value;
                invalidate();
            }
        });
    }

    protected DragView(Launcher launcher){
        super(launcher);
    }

    /** Sets the scale of the view over the normal workspace icon size. */
    public void setIntrinsicIconScaleFactor(float scale) {
        mIntrinsicIconScale = scale;
    }

    public float getIntrinsicIconScaleFactor() {
        return mIntrinsicIconScale;
    }

    public float getOffsetY() {
        return mOffsetY;
    }

    public int getDragRegionLeft() {
        return mDragRegion.left;
    }

    public int getDragRegionTop() {
        return mDragRegion.top;
    }

    public int getDragRegionWidth() {
        return mDragRegion.width();
    }

    public int getDragRegionHeight() {
        return mDragRegion.height();
    }

    public void setDragVisualizeOffset(Point p) {
        mDragVisualizeOffset = p;
    }

    public Point getDragVisualizeOffset() {
        return mDragVisualizeOffset;
    }

    public void setDragRegion(Rect r) {
        mDragRegion = r;
    }

    public Rect getDragRegion() {
        return mDragRegion;
    }

    public float getInitialScale() {
        return mInitialScale;
    }

    public void updateInitialScaleToCurrentScale() {
        mInitialScale = getScaleX();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(2 * mRadius + mBitmap.getWidth(),
                2 * mRadius + mBitmap.getHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean debug = false;
        if (debug) {
            Paint p = new Paint();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66ffffff);
            canvas.drawRect(0, 0, getWidth(), getHeight(), p);
        }

        mHasDrawn = true;
        boolean crossFade = mCrossFadeProgress > 0 && mCrossFadeBitmap != null;
        if (crossFade) {
            int alpha = crossFade ? (int) (255 * (1 - mCrossFadeProgress)) : 255;
            mPaint.setAlpha(alpha);
        }
        canvas.drawBitmap(mBitmap, mRadius, mRadius, mPaint);

        //draw number icon
        if(mLauncher.mWorkspace.getState() == Workspace.State.ARRANGE
                && mNumber > 0){
            canvas.drawCircle(mRadius, mRadius, mRadius, mCirclePaint);
            if(mCornerProgress == 1){
                String text = String.valueOf(mNumber);
                int textWidth = (int)(mNumberPaint.measureText(text));
                Rect textBounds = new Rect();
                mNumberPaint.getTextBounds(text, 0, text.length(), textBounds);
                int textHeight = textBounds.height();
                canvas.drawText(text, mRadius - textWidth / 2, mRadius + textHeight / 2,
                        mNumberPaint);
            }else{
                Bitmap bitmap = mLauncher.mIconCache.getArrangSelectBitmap();
                mSelectBitmapPaint.setAlpha((int)(255 * (1 - mCornerProgress)));

                Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                Rect destRect = new Rect(0, 0, 2 * mRadius, 2 * mRadius);
                canvas.drawBitmap(bitmap, srcRect, destRect, mSelectBitmapPaint);
            }
        }

        if (crossFade) {
            mPaint.setAlpha((int) (255 * mCrossFadeProgress));
            canvas.save();
            float sX = (mBitmap.getWidth() * 1.0f) / mCrossFadeBitmap.getWidth();
            float sY = (mBitmap.getHeight() * 1.0f) / mCrossFadeBitmap.getHeight();
            canvas.scale(sX, sY);
            canvas.drawBitmap(mCrossFadeBitmap, 0.0f, 0.0f, mPaint);
            canvas.restore();
        }
    }

    public void setCrossFadeBitmap(Bitmap crossFadeBitmap) {
        mCrossFadeBitmap = crossFadeBitmap;
    }

    public void crossFade(int duration) {
        ValueAnimator va = LauncherAnimUtils.ofFloat(this, 0f, 1f);
        va.setDuration(duration);
        va.setInterpolator(new DecelerateInterpolator(1.5f));
        va.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCrossFadeProgress = animation.getAnimatedFraction();
            }
        });
        va.start();
    }

    public void setColor(int color) {
        if (mPaint == null) {
            mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        }
        if (color != 0) {
            ColorMatrix m1 = new ColorMatrix();
            m1.setSaturation(0);

            ColorMatrix m2 = new ColorMatrix();
            setColorScale(color, m2);
            m1.postConcat(m2);

            if (Utilities.ATLEAST_LOLLIPOP) {
                animateFilterTo(m1.getArray());
            } else {
                mPaint.setColorFilter(new ColorMatrixColorFilter(m1));
                invalidate();
            }
        } else {
            if (!Utilities.ATLEAST_LOLLIPOP || mCurrentFilter == null) {
                mPaint.setColorFilter(null);
                invalidate();
            } else {
                animateFilterTo(new ColorMatrix().getArray());
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void animateFilterTo(float[] targetFilter) {
        float[] oldFilter = mCurrentFilter == null ? new ColorMatrix().getArray()
                : mCurrentFilter;
        mCurrentFilter = Arrays.copyOf(oldFilter, oldFilter.length);

        if (mFilterAnimator != null) {
            mFilterAnimator.cancel();
        }
        mFilterAnimator = ValueAnimator.ofObject(new FloatArrayEvaluator(mCurrentFilter),
                oldFilter, targetFilter);
        mFilterAnimator.setDuration(COLOR_CHANGE_DURATION);
        mFilterAnimator.addUpdateListener(new AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mPaint.setColorFilter(new ColorMatrixColorFilter(mCurrentFilter));
                invalidate();
            }
        });
        mFilterAnimator.start();
    }

    public boolean hasDrawn() {
        return mHasDrawn;
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (mPaint != null){
            mPaint.setAlpha((int) (255 * alpha));
            invalidate();
        }
    }

    /**
     * Create a window containing this view and show it.
     *
     * @param windowToken obtained from v.getWindowToken() from one of your views
     * @param touchX the x coordinate the user touched in DragLayer coordinates
     * @param touchY the y coordinate the user touched in DragLayer coordinates
     */
    public void show(int touchX, int touchY) {
        mDragLayer.addView(this);

        // Start the pick-up animation
        DragLayer.LayoutParams lp = new DragLayer.LayoutParams(0, 0);
        lp.width = mBitmap.getWidth() + 2 * mRadius;
        lp.height = mBitmap.getHeight() +2 *  mRadius;
        lp.customPosition = true;
        setLayoutParams(lp);

        setTranslationX(touchX - mRegistrationX - mRadius);
        setTranslationY(touchY - mRegistrationY - mRadius);

        // Post the animation to skip other expensive work happening on the first frame
        post(new Runnable() {
                public void run() {
                    mAnim.start();
                }
            });
        mSelectAnim.start();
    }

    public void cancelAnimation() {
        if (mAnim != null && mAnim.isRunning()) {
            mAnim.cancel();
        }
    }

    public void resetLayoutParams() {
        mOffsetX = mOffsetY = 0;
        requestLayout();
    }

    /**
     * Move the window containing this view.
     *
     * @param touchX the x coordinate the user touched in DragLayer coordinates
     * @param touchY the y coordinate the user touched in DragLayer coordinates
     */
    void move(int touchX, int touchY) {
        setTranslationX(touchX - mRegistrationX + (int) mOffsetX - mRadius);
        setTranslationY(touchY - mRegistrationY + (int) mOffsetY - mRadius);
    }

    public void remove() {
        if (getParent() != null) {
            mDragLayer.removeView(DragView.this);
        }
    }

    public static void setColorScale(int color, ColorMatrix target) {
        target.setScale(Color.red(color) / 255f, Color.green(color) / 255f,
                Color.blue(color) / 255f, Color.alpha(color) / 255f);
    }

    public int getLeftCornerRadius(){
        return mRadius;
    }

}
