/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codeaurora.snaplauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.android.launcher3.FolderPagedView;
import com.android.launcher3.Hotseat;
import com.android.launcher3.AppInfo;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DragLayer;
import com.android.launcher3.DragView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Workspace;
import com.android.launcher3.util.Thunk;

import org.codeaurora.snaplauncher.R;

public class BatchArrangeDragView extends DragView {
    private final DragLayer mDragLayer;
    private final int mRegistrationX, mRegistrationY;
    private View mView;
    @Thunk
    Paint mPaint;
    private Bitmap mBitmap;

    ValueAnimator mAnim;
    final int[] mStartPointXY = new int[2];
    private Launcher mLauncher;
    private float mProgress = 1.0f;
    private int mRadius;
    private Paint mBitmapPaint;
    BubbleTextViewType mViewType = BubbleTextViewType.WORKSPACE;

    private final TimeInterpolator mCubicEaseOutInterpolator
            = new DecelerateInterpolator(1.5f);

    private FolderPagedView mFolderParent;

    public enum BubbleTextViewType {
        HOTSEAT,
        WORKSPACE,
        FOLDER
    }

    public BatchArrangeDragView(Launcher launcher, View view, int registrationX,
            int registrationY) {
        super(launcher);
        mView = view;
        mDragLayer = launcher.getDragLayer();

        mLauncher = launcher;
        // The point in our scaled bitmap that the touch events are located
        mRegistrationX = registrationX;
        mRegistrationY = registrationY;

        Bitmap bitmap = getViewBitmap(view);

        mBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight());

        updateViewType(view);

        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        mRadius = getResources().
                getDimensionPixelSize(R.dimen.default_arrange_select_circle_radius);
        mBitmapPaint = new Paint();
        mBitmapPaint.setAntiAlias(true);
        mBitmapPaint.setStyle(Paint.Style.FILL);
        mBitmapPaint.setColor(Color.WHITE);

        float scale;
        if (mViewType == BubbleTextViewType.FOLDER) {
            ShortcutInfo info = (ShortcutInfo) view.getTag();
            mFolderParent = (FolderPagedView) view.getParent().getParent().getParent();
            mFolderParent.getFolder().getInfo().remove(info);
            scale = mDragLayer.getLocationInDragLayer(mFolderParent.getFolder().getFolderIcon(),
                    mStartPointXY);
        } else {
            scale = mDragLayer.getLocationInDragLayer(view, mStartPointXY);
        }
        mStartPointXY[0] = Math.round(mStartPointXY[0] - (bitmap.getWidth() -
                scale * view.getWidth()) / 2 - mRadius);
        mStartPointXY[1] = Math.round(mStartPointXY[1] - (bitmap.getHeight() -
                scale * bitmap.getHeight()) / 2 + view.getPaddingTop() - mRadius);

        // Force a measure, because Workspace uses getMeasuredHeight() before the layout pass
        int ms = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        measure(ms, ms);
    }

    private void updateViewType(View view) {
        if (view.getParent() instanceof Hotseat) {
            mViewType = BubbleTextViewType.HOTSEAT;
        } else if (view.getParent().getParent().getParent() instanceof FolderPagedView) {
            mViewType = BubbleTextViewType.FOLDER;
        } else if (view.getParent().getParent().getParent() instanceof Workspace) {
            mViewType = BubbleTextViewType.WORKSPACE;
        }
    }

    public BubbleTextViewType getType() {
        return mViewType;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mBitmap.getWidth() + 2 * mRadius, mBitmap.getHeight()
                + 2 * mRadius);
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        mPaint.setAlpha((int) (255 * alpha));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(mBitmap, mRadius, mRadius, mPaint);

        canvas.drawCircle(mRadius, mRadius, mRadius * mProgress, mBitmapPaint);
        Bitmap bitmap = mLauncher.mIconCache.getArrangSelectBitmap();
        Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        int bitmpSize = (int) (2 * mRadius * mProgress);
        Rect destRect = new Rect(0, 0, bitmpSize, bitmpSize);
        canvas.drawBitmap(bitmap, srcRect, destRect, mBitmapPaint);
    }

    public Animator getAnim() {
        return mAnim;
    }

    /**
     * Create a window containing this view and show it.
     *
     * @param windowToken obtained from v.getWindowToken() from one of your views
     */
    public void show() {
        mView.setVisibility(View.GONE);
        if (mViewType == BubbleTextViewType.WORKSPACE) {
            CellLayout layout = (CellLayout) mView.getParent().getParent();
            layout.prepareChildForDrag(mView);
        }
        mDragLayer.addView(this);

        // Start the pick-up animation
        DragLayer.LayoutParams lp = new DragLayer.LayoutParams(0, 0);
        lp.width = mBitmap.getWidth() + 2 * mRadius;
        lp.height = mBitmap.getHeight() + 2 * mRadius;
        lp.customPosition = true;
        setLayoutParams(lp);
        setTranslationX(mStartPointXY[0]);
        setTranslationY(mStartPointXY[1]);
    }

    public void prepareStartAnimation(int touchX, int touchY) {
        prepareAnimation(mStartPointXY[0], mStartPointXY[1],
                touchX - mRegistrationX - mRadius, touchY - mRegistrationY - mRadius);
    }

    private int calculateDuration(final int fromX, final int fromY, int toX, int toY) {
        // Calculate the duration of the animation based on the object's distance
        final float dist = (float) Math.hypot(toX - fromX, toY - fromY);
        final Resources res = getResources();
        final float maxDist = (float) res.getInteger(R.integer.config_dropAnimMaxDist);

        int duration = res.getInteger(R.integer.config_dropAnimMaxDuration);
        if (dist < maxDist) {
            duration *= mCubicEaseOutInterpolator.getInterpolation(dist / maxDist);
        }
        duration = Math.max(duration, res.getInteger(R.integer.config_dropAnimMinDuration));
        return duration;
    }

    private void prepareAnimation(final int fromX, final int fromY, int toX, int toY) {
        final int offsetX = fromX - toX;
        final int offsetY = fromY - toY;
        // Animate the view into the correct position
        mAnim = LauncherAnimUtils.ofFloat(this, 0f, 1f);
        mAnim.setInterpolator(mCubicEaseOutInterpolator);
        mAnim.setDuration(calculateDuration(fromX, fromY, toX, toY));
        mAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float value = (Float) animation.getAnimatedValue();

                final int deltaX = (int) (value * offsetX);
                final int deltaY = (int) (value * offsetY);

                if (getParent() == null) {
                    animation.cancel();
                } else {
                    setTranslationX(fromX - deltaX);
                    setTranslationY(fromY - deltaY);
                }
                mProgress = 1 - value;
                invalidate();
            }
        });

        mAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mMoveRunnable != null) {
                    mMoveRunnable.run();
                }
                mMoveRunnable = null;
            }
        });
    }

    public void remove() {
        if (getParent() != null) {
            mDragLayer.removeView(this);
        }
    }

    private Runnable mMoveRunnable;

    public void move(final int touchX, final int touchY) {
        if (mAnim != null && mAnim.isRunning()) {
            mMoveRunnable = new Runnable() {
                @Override
                public void run() {
                    move(touchX, touchY);
                }
            };
            return;
        }
        int translationX = touchX - mRegistrationX - mRadius;
        int translationY = touchY - mRegistrationY - mRadius;
        setTranslationX(translationX);
        setTranslationY(translationY);
    }

    private Bitmap getViewBitmap(View view) {
        Launcher launcher = (Launcher) getContext();
        if (view.getTag() instanceof ShortcutInfo) {
            ShortcutInfo info = (ShortcutInfo) view.getTag();
            return info.getIcon(launcher.mIconCache);
        }
        return null;
    }

    public View getView() {
        return mView;
    }

    public void setCoorView(View cell) {
        mView = cell;
        updateViewType(mView);
    }

    public ShortcutInfo getShortcutInfo() {
        Object dragInfo = mView.getTag();
        ShortcutInfo item;
        if (dragInfo instanceof AppInfo) {
            // Came from all apps -- make a copy
            item = ((AppInfo) dragInfo).makeShortcut();
        } else {
            item = (ShortcutInfo) dragInfo;
        }
        return item;
    }

    public static void resetCellXY(ShortcutInfo item) {
        item.cellX = -1;
        item.cellY = -1;
    }

    public int getLeftCornerRadius() {
        return mRadius;
    }
}
