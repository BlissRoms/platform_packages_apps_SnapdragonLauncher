/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.graphics.PointF;
import com.android.launcher3.DragController.DragListener;
import com.android.launcher3.UninstallDropTarget.UninstallSource;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;
import java.util.List;

import org.codeaurora.snaplauncher.BatchArrangeDragView;
import org.codeaurora.snaplauncher.R;

public class Hotseat extends LinearLayout implements DragSource, DropTarget,
        DragListener, UninstallSource {

    private static final int MAX_HOTSEAT = 5;
    private Hotseat mContentHotSeat;
    private static final int HOTSEAT_SCREEN = -1;

    private Launcher mLauncher;
    private int mItemWidth;
    private int mItemHeight;
    private View mDragView;
    private int mDragPos = -1;

    private static final int DRAG_MODE_NONE = 0;
    private static final int DRAG_MODE_CREATE_FOLDER = 1;
    private static final int DRAG_MODE_ADD_TO_FOLDER = 2;
    private int mDragMode = DRAG_MODE_NONE;

    private final boolean mHasVerticalHotseat;

    private boolean mDragFromExternal = false;
    private boolean mWillSetDragViewVisible = false;

    private static final int FOLDER_CREATION_TIMEOUT = 0;
    private static final int FOLDER_CREATE_ANIMATION_INNER = 1;
    private static final int FOLDER_CREATE_ANIMATION_OUTER = 2;
    private final Alarm mFolderCreationAlarm = new Alarm();
    @Thunk FolderIcon.FolderRingAnimator mDragFolderRingAnimator = null;

    int mTargetCell = -1;
    float[] mDragViewVisualCenter = new float[2];
    float[] mDropViewVisualCenter = new float[2];
    private boolean mCreateUserFolderOnDrop = false;
    private  boolean mAddToExistingFolderOnDrop = false;
    private FolderIcon mDragOverFolderIcon = null;
    private int mDragOverX = -1;

    private final float mMinDistanceForSwapPosition;
    private final Alarm mReorderAlarm = new Alarm();

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    @Thunk final int[] mTempLocation = new int[2];
    private ArrayList<FolderIcon.FolderRingAnimator> mFolderOuterRings =
            new ArrayList<FolderIcon.FolderRingAnimator>();
    private OnLongClickListener mLongClickListener;

    @Thunk Runnable mDeferredAction;
    private boolean mDeferDropAfterUninstall;
    private boolean mUninstallSuccessful;

    private static final int REORDER_TIMEOUT = 50;
    private ReorderAlarmListener listener;
    private float mMaxDistanceForFolderCreation;
    private boolean mLastDropInTargetArea;
    private boolean mNewFolderCreated = false;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (isLand()) {
            setOrientation(VERTICAL);
            updateItemValidHeight();
        } else {
            setOrientation(HORIZONTAL);
            updateItemValidWidth();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int previewOffset = FolderIcon.FolderRingAnimator.sPreviewSize;
        // The folder outer / inner ring image(s)
        DeviceProfile grid = mLauncher.getDeviceProfile();
        for (int i = 0; i < mFolderOuterRings.size(); i++) {
            FolderIcon.FolderRingAnimator fra = mFolderOuterRings.get(i);

            Drawable d;
            int width, height;
            cellToPoint(fra.mCellX, fra.mCellY, mTempLocation);
            View child = getChildAt(fra.mCellX);
            if (child != null) {

                int centerX = mTempLocation[0];
                int centerY = getPaddingTop()+ mTempLocation[1] * child.getHeight() +
                        previewOffset / 2 + child.getPaddingTop() + grid.folderBackgroundOffset;

                // Draw outer ring, if it exists
                if (FolderIcon.HAS_OUTER_RING) {
                    d = FolderIcon.FolderRingAnimator.sSharedOuterRingDrawable;
                    width = (int) (fra.getOuterRingSize() * getChildrenScale());
                    height = width;
                    canvas.save();
                    canvas.translate(centerX - width / 2, centerY - height / 2);
                    d.setBounds(0, 0, width, height);
                    d.draw(canvas);
                    canvas.restore();
                }

                // Draw inner ring
                d = FolderIcon.FolderRingAnimator.sSharedInnerRingDrawable;
                width = (int) (fra.getInnerRingSize() * getChildrenScale());
                height = width;
                canvas.save();
                canvas.translate(centerX - width / 2, centerY - width / 2);
                d.setBounds(0, 0, width, height);
                d.draw(canvas);
                canvas.restore();
            }
        }
    }

    /**
     * Given a cell coordinate, return the point that represents the upper left corner of that cell
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void cellToPoint(int cellX, int cellY, int[] result) {
        result[0] = (int)mDropViewVisualCenter[0];
        result[1] = isLand() ? getDragPosByCellX(cellX): 0;
    }

    public float getChildrenScale() {
        return 1.0f;
    }

    public void showFolderAccept(FolderIcon.FolderRingAnimator fra) {
        mFolderOuterRings.add(fra);
    }

    public void hideFolderAccept(FolderIcon.FolderRingAnimator fra) {
        if (mFolderOuterRings.contains(fra)) {
            mFolderOuterRings.remove(fra);
        }
        invalidate();
    }

    private boolean isLand() {
        Configuration configuration = getResources().getConfiguration();
        int ori = configuration.orientation;
        boolean isLand = false;
        if (ori == configuration.ORIENTATION_LANDSCAPE) {
            isLand = true;
        }
        if (ori == configuration.ORIENTATION_PORTRAIT) {
            isLand = false;
        }
        return isLand;
    }

    private void cleanupFolderCreation() {
        if (mDragFolderRingAnimator != null) {
            mDragFolderRingAnimator.animateToNaturalState(false);
            mDragFolderRingAnimator = null;
        }
        mFolderCreationAlarm.setOnAlarmListener(null);
        mFolderCreationAlarm.cancelAlarm();
    }

    private void cleanupAddToFolder() {
        if (mDragOverFolderIcon != null) {
            mDragOverFolderIcon.onDragExit(null);
            mDragOverFolderIcon = null;
        }
    }

    void setDragMode(int dragMode) {
        if (dragMode != mDragMode) {
            if (dragMode == DRAG_MODE_NONE) {
                cleanupAddToFolder();
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_ADD_TO_FOLDER) {
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_CREATE_FOLDER) {
                cleanupAddToFolder();
            }
            mDragMode = dragMode;
        }
    }

    public View setSeat(ItemInfo item, boolean visible) {
        // A ViewGroup usually does not draw, but Hotseat needs to draw a rectangle to show
        // the user where a dragged item will land when dropped.
        setWillNotDraw(false);
        setClipToPadding(false);

        View view = getChildAt(item.cellX);
        if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {

            FolderIcon mFolderView;
            FolderInfo info = (FolderInfo) item;
            mFolderView = createFolderSeat(view, info);
            mFolderView.setTag(info);
            mFolderView.setOnLongClickListener(mLongClickListener);
            if (visible) {
                mFolderView.setVisibility(View.VISIBLE);
            } else {
                mFolderView.setVisibility(View.INVISIBLE);
            }
            return mFolderView;
        } else {
            BubbleTextView hotseatV;
            hotseatV = createBubbleTextSeat(view, item);

            hotseatV.setIsHotseat(true);
            ShortcutInfo info = (ShortcutInfo) item;
            mLauncher.createHotseatShortcut(hotseatV, info);
            hotseatV.setTag(info);
            hotseatV.setOnLongClickListener(mLongClickListener);
            if (visible) {
                hotseatV.setVisibility(View.VISIBLE);
            } else {
                hotseatV.setVisibility(View.INVISIBLE);
            }
            return hotseatV;
        }
    }

    private BubbleTextView createBubbleTextSeat(View view, ItemInfo item) {
        removeView(view);
        BubbleTextView favorite = (BubbleTextView) mLauncher.getLayoutInflater().
                inflate(R.layout.app_icon, mContentHotSeat, false);
        if (isLand()) {
            favorite.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.FILL_PARENT, 0, 1.0f));
        } else {
            favorite.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LayoutParams.FILL_PARENT, 1.0f));
        }

        addView(favorite, item.cellX);
        return favorite;
    }

    public FolderIcon createNewFolderSeat(FolderIcon newFolder, int cellX) {
        if(isLand()) {
            newFolder.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.FILL_PARENT, 0, 1.0f));
        }else{
            newFolder.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LayoutParams.FILL_PARENT, 1.0f));
        }

        newFolder.mFolderName.setTextVisibility(false);

        LayoutTransition layoutTransition = createLayoutTransition();
        newFolder.setLayoutTransition(layoutTransition);
        addView(newFolder, cellX);
        newFolder.setOnLongClickListener(mLongClickListener);
        return newFolder;
    }

    private FolderIcon createFolderSeat(View view,FolderInfo info) {
        removeView(view);
        FolderIcon folderView = FolderIcon.fromXml(R.layout.folder_icon, mLauncher,
                mContentHotSeat, info, mLauncher.mIconCache);
        if (isLand()) {
            folderView.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.FILL_PARENT, 0, 1.0f));
        } else {
            folderView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LayoutParams.FILL_PARENT, 1.0f));
        }

        folderView.mFolderName.setTextVisibility(false);

        LayoutTransition layoutTransition = createLayoutTransition();
        folderView.setLayoutTransition(layoutTransition);
        addView(folderView, info.cellX);
        return  folderView;
    }

    private void removeBatchArrangeViews(DragObject d){
        for(BatchArrangeDragView dragView:d.snapDragViews){
            if (dragView.getType() == BatchArrangeDragView.BubbleTextViewType.WORKSPACE){
                mLauncher.mWorkspace.getParentCellLayoutForView(dragView.getView()).
                        removeView(dragView.getView());
            }
            dragView.remove();
        }
    }

    boolean createUserFolderIfNecessary(long container, Hotseat target,
                                        int targetCell, float distance, boolean external,
                                        DragObject d, Runnable postAnimationRunnable) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View v = target.getChildAt(targetCell);

        if (v == null || !mCreateUserFolderOnDrop) return false;
        mCreateUserFolderOnDrop = false;
        final long screenId = HOTSEAT_SCREEN;
        boolean aboveShortcut = (v.getTag() instanceof ShortcutInfo);
        if (aboveShortcut) {
            ShortcutInfo sourceInfo = (ShortcutInfo) d.dragInfo;
            ShortcutInfo destInfo = (ShortcutInfo) v.getTag();
            removeBatchArrangeViews(d);

            Rect folderLocation = new Rect();
            float scale = mLauncher.getDragLayer().getDescendantRectRelativeToSelf(v,
                    folderLocation);
            target.removeView(v);
            FolderIcon fi =
                    mLauncher.addFolder(target, container, screenId, targetCell, 0);
            destInfo.cellX = -1;
            destInfo.cellY = -1;
            sourceInfo.cellX = -1;
            sourceInfo.cellY = -1;
            for (BatchArrangeDragView dragView: d.snapDragViews){
                BatchArrangeDragView.resetCellXY(dragView.getShortcutInfo());
            }

            // If the dragView is null, we can't animate
            boolean animate = d.dragView != null;
            if (animate) {
                fi.performCreateAnimation(destInfo, v, sourceInfo, d, folderLocation, scale,
                        postAnimationRunnable);
            } else {
                fi.addItem(destInfo);
                fi.addItem(sourceInfo);
                fi.addSnapViews(d);
            }
            mLauncher.clearBatchArrangeApps();
            return true;
        }
        return false;
    }

    boolean addToExistingFolderIfNecessary(Hotseat target, int targetCell,
                                           float distance, boolean external,
                                           DragObject d) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View dropOverView = target.getChildAt(targetCell);
        if (!mAddToExistingFolderOnDrop) return false;
        mAddToExistingFolderOnDrop = false;

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(d.dragInfo)) {
                fi.onDrop(d);
                removeBatchArrangeViews(d);
                mLauncher.clearBatchArrangeApps();
                return true;
            }
        }
        return false;
    }

    private void setCurrentDropOverCell(int x) {
        if (x != mDragOverX) {
            mDragOverX = x;
            setDragMode(DRAG_MODE_NONE);
        }
    }

    @Override
    public void onDrop(DragObject d) {
        if (mReorderAlarm.alarmPending()){
            mReorderAlarm.cancelAlarm();
            listener.onAlarm(mReorderAlarm);
        }
        final List<ItemInfo> batchInfos = new ArrayList<>();
        for (BatchArrangeDragView dragView:d.snapDragViews){
            batchInfos.add((ItemInfo)dragView.getView().getTag());
        }
        int pos = isLand() ? d.y / mItemHeight : d.x / mItemWidth;
        if (pos <= 0) {
            pos = 0;
        } else if (pos > getVisibleCount() - 1) {
            pos = getVisibleCount() - 1;
        }
        int cellX = getCellXByPos(pos);
        View dropTargetLayout = getChildAt(cellX);

        long container = LauncherSettings.Favorites.CONTAINER_HOTSEAT;
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        mDropViewVisualCenter = getVisualCenter(pos, dropTargetLayout.getWidth(),
                dropTargetLayout.getHeight());
        float targetCellDistance = (float) Math.hypot(
                mDragViewVisualCenter[0] - mDropViewVisualCenter[0],
                mDragViewVisualCenter[1] - mDropViewVisualCenter[1]);

        if (createUserFolderIfNecessary(container, mContentHotSeat,
                mTargetCell, targetCellDistance,
                d.dragSource instanceof  Workspace, d, null)) {
            mNewFolderCreated = true;
            if(!(d.dragSource instanceof Hotseat) && mDragView != null
                    && mDragView.getVisibility() == INVISIBLE){
                Message msg = myHandler.obtainMessage(FOLDER_CREATE_ANIMATION_OUTER, null);
                myHandler.sendMessageDelayed(msg, 100);
            }
            return ;
        }
        if(addToExistingFolderIfNecessary(mContentHotSeat,cellX,targetCellDistance,
                d.dragSource instanceof  Workspace, d)){
            if(!(d.dragSource instanceof Hotseat) && mDragView != null
                    && mDragView.getVisibility() == INVISIBLE){
                animateSetViewVisibility(mDragView, GONE);
            }
            return ;
        }

        mWillSetDragViewVisible = true;

        ItemInfo dragInfo = (ItemInfo) d.dragInfo;

        if (dropTargetLayout != null && dropTargetLayout.getVisibility() == VISIBLE){
            dragInfo.cellX = getInVisibleViewPosition();
        }else {
            dragInfo.cellX = cellX;
        }

        mDragView = setSeat(dragInfo, true);
        if (mLauncher.mWorkspace.getState() == Workspace.State.ARRANGE
                && mDragView instanceof BubbleTextView) {
            mLauncher.updateBatchArrangeApps(mDragView);
        }
        final int basic = dragInfo.cellX;

        for (int i=0; i< batchInfos.size(); i++){
            View dragView = d.snapDragViews.get(i).getView();
            ItemInfo info = batchInfos.get(i);
            if ((basic +i+1) >= getChildCount()){
                info.cellX = (basic + i +1) % 5;
            }else {
                info.cellX = (basic +i+1);
            }

            final int emptyPos = getEmptySeatPosition();
            if (emptyPos != -1){
                View emptyView = getChildAt(emptyPos);
                emptyView.setVisibility(View.INVISIBLE);
                removeView(emptyView);
                addView(emptyView, info.cellX);
                if (d.snapDragViews.get(i).getType()
                        == BatchArrangeDragView.BubbleTextViewType.WORKSPACE){
                    mLauncher.mWorkspace.getParentCellLayoutForView(dragView)
                            .removeView(dragView);
                }
            }
            View cell = setSeat(info, true);
            mLauncher.updateBatchArrangeApps(cell);
            d.snapDragViews.get(i).setCoorView(cell);
        }
        mLauncher.clearBatchArrangeApps();
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = (Launcher) context;
        mHasVerticalHotseat = mLauncher.getDeviceProfile().isVerticalBarLayout();
        mMinDistanceForSwapPosition = (float) mLauncher.getDeviceProfile().iconSizePx * 5/6;
        mMaxDistanceForFolderCreation = (0.55f * mLauncher.getDeviceProfile().iconSizePx);
    }

    LinearLayout getLayout() {
        return mContentHotSeat;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContentHotSeat = (Hotseat) findViewById(R.id.hotseat);
        mContentHotSeat.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        LayoutTransition layoutTransition = createLayoutTransition();
        mContentHotSeat.setLayoutTransition(layoutTransition);
    }

    /**
     * Returns whether there are other icons than the all apps button in the hotseat.
     */
    public boolean hasIcons() {
        return mContentHotSeat.getChildCount() > 0;
    }

    /* Get the orientation invariant order of the item in the hotseat for persistence. */
    public int getOrderInHotseat(int x, int y) {
        return mHasVerticalHotseat ? (mContentHotSeat.getChildCount() - y - 1) : x;
    }

    /* Get the orientation specific coordinates given an invariant order in the hotseat. */
    int getCellXFromOrder(int rank) {
        return mHasVerticalHotseat ? 0 : rank;
    }

    int getCellYFromOrder(int rank) {
        return mHasVerticalHotseat ? (mContentHotSeat.getChildCount() - (rank + 1)) : 0;
    }

    public boolean isAllAppsButtonRank(int rank) {
        return false;
    }

    @Override
    public void onDragEnter(DragObject dragObject) {
        mNewFolderCreated = false;
        mCreateUserFolderOnDrop = false;
        mAddToExistingFolderOnDrop = false;
        cleanupFolderCreation();
        setCurrentDropOverCell(-1);
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(this, outRect);
    }

    @Override
    public void prepareAccessibilityDrop() {
    }

    @Override
    public void onDragExit(DragObject dragObject) {
        if (mDragMode == DRAG_MODE_CREATE_FOLDER) {
            mCreateUserFolderOnDrop = true;
        } else if (mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
            mAddToExistingFolderOnDrop = true;
        }
        cleanupFolderCreation();
        setCurrentDropOverCell(-1);
    }

    private LayoutTransition createLayoutTransition() {
        LayoutTransition transition = new LayoutTransition();
        transition.setAnimator(LayoutTransition.APPEARING, null);
        transition.setAnimator(LayoutTransition.DISAPPEARING, null);

        transition.setStagger(LayoutTransition.CHANGE_APPEARING, 0);
        transition.setStagger(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transition.setAnimateParentHierarchy(true);
        transition.setDuration(400);
        return transition;
    }

    private void startHotseatLayoutTransition(LayoutTransition oriLayoutTransition) {
        setLayoutTransition(oriLayoutTransition);
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View v = getChildAt(i);

            if (v.getVisibility() != GONE) {
                getLayoutTransition().addChild(this, v);
            }
            if (v instanceof FolderIcon) {
                int childCount = ((FolderIcon) v).getChildCount();
                for (int j = 0; j < childCount; j++)
                    ((FolderIcon) v).getLayoutTransition().addChild(((FolderIcon) v),
                            ((FolderIcon) v).getChildAt(j));
            }
        }
        invalidate();
    }

    public void setDragViewVisibility(boolean visible) {
        animateSetViewVisibility(mDragView, visible ? VISIBLE : GONE);
    }

    boolean willCreateUserFolder(ItemInfo info, Hotseat target, int targetCell, float
            distance, boolean considerTimeout, DragObject d) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View dropOverView = target.getChildAt(targetCell);

        boolean hasntMoved = false;
        if (d.dragInfo != null) {
            hasntMoved = dropOverView.getTag() == d.dragInfo;
        }

        if (dropOverView == null || hasntMoved || (considerTimeout && !mCreateUserFolderOnDrop)) {
            return false;
        }
        boolean aboveShortcut = (dropOverView.getTag() instanceof ShortcutInfo);
        boolean willBecomeShortcut =
                (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                        info.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT);

        return (aboveShortcut && willBecomeShortcut);
    }

    boolean willAddToExistingUserFolder(Object dragInfo, Hotseat target, int targetCell,
                                        float distance) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View dropOverView = target.getChildAt(targetCell);

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(dragInfo)) {
                return true;
            }
        }
        return false;
    }

    private void manageFolderFeedback(ItemInfo info, Hotseat targetLayout,
                                      int targetCell, float distance,
                                      View dragOverView, boolean accessibleDrag,
                                      DragObject d) {

        boolean userFolderPending = willCreateUserFolder(info, targetLayout, targetCell,
                distance, false, d);
        if (mDragMode == DRAG_MODE_NONE && userFolderPending &&
                !mFolderCreationAlarm.alarmPending()) {
            FolderCreationAlarmListener listener = new
                    FolderCreationAlarmListener(targetLayout, targetCell, 0);

            if (!accessibleDrag) {
                mFolderCreationAlarm.setOnAlarmListener(listener);
                mFolderCreationAlarm.setAlarm(FOLDER_CREATION_TIMEOUT);
            } else {
                listener.onAlarm(mFolderCreationAlarm);
            }
            return;
        }


        boolean willAddToFolder =
                willAddToExistingUserFolder(info, targetLayout, targetCell, distance);

        if (willAddToFolder && mDragMode == DRAG_MODE_NONE) {
            mDragOverFolderIcon = ((FolderIcon) dragOverView);
            mDragOverFolderIcon.onDragEnter(info);

            mAddToExistingFolderOnDrop = true;
            setDragMode(DRAG_MODE_ADD_TO_FOLDER);
            return;
        }

        if (mDragMode == DRAG_MODE_ADD_TO_FOLDER && !willAddToFolder) {
            setDragMode(DRAG_MODE_NONE);
        }
        if (mDragMode == DRAG_MODE_CREATE_FOLDER && !userFolderPending) {
            setDragMode(DRAG_MODE_NONE);
        }

        return;
    }

    @Override
    public void onUninstallActivityReturned(boolean success) {
        mDeferDropAfterUninstall = false;
        mUninstallSuccessful = success;
        if (mDeferredAction != null) {
            mDeferredAction.run();
        }
    }

    @Override
    public void deferCompleteDropAfterUninstallActivity() {
        mDeferDropAfterUninstall = true;
    }

    class FolderCreationAlarmListener implements OnAlarmListener {
        Hotseat layout;
        int cellX;
        int cellY;

        public FolderCreationAlarmListener(Hotseat layout, int cellX, int cellY) {
            this.layout = layout;
            this.cellX = cellX;
            this.cellY = cellY;
        }

        public void onAlarm(Alarm alarm) {
            if (mDragFolderRingAnimator != null) {
                // This shouldn't happen ever, but just in case, make sure we clean up the mess.
                mDragFolderRingAnimator.animateToNaturalState(false);
            }
            mDragFolderRingAnimator = new FolderIcon.FolderRingAnimator(mLauncher, null);
            mDragFolderRingAnimator.setCell(cellX, cellY);
            mDragFolderRingAnimator.setHotseat(layout);
            mDragFolderRingAnimator.animateToAcceptState(true);
            layout.showFolderAccept(mDragFolderRingAnimator);
            setDragMode(DRAG_MODE_CREATE_FOLDER);
        }
    }


    private final float[] getVisualCenter(int cellX,float toViewWidth,float toViewHeight) {
        final float res[] = new float[2];
        WindowManager wm = (WindowManager) getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        // In order to find the visual center, we shift by half the dragRect
        if (isLand()){
            res[0] = getPaddingLeft() + toViewWidth / 2;
            res[1] = getPaddingTop() + toViewHeight * cellX + toViewHeight / 2;
        }else {
            res[0] = getPaddingLeft() + toViewWidth * cellX + toViewWidth / 2;
            res[1] = getPaddingTop() + toViewHeight / 2;
        }
        return res;
    }

    /**
     * Registers the specified listener on each page contained in this Hotseat.
     *
     * @param l The listener used to respond to long clicks.
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mLongClickListener = l;
        super.setOnLongClickListener(l);
    }

    @Override
    public void onDragOver(final DragObject d) {
        int pos = isLand() ? d.y / mItemHeight : d.x / mItemWidth;
        int cellX = getCellXByPos(pos);
        View toView = getChildAt(cellX);

        if (toView != null && toView.getVisibility() == View.VISIBLE ) {
            ItemInfo itemInfo = (ItemInfo) d.dragInfo;
            Hotseat mDragTargetLayout = mContentHotSeat;
            mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
            mDropViewVisualCenter = getVisualCenter(pos, toView.getWidth(), toView.getHeight());
            float targetCellDistance = (float) Math.hypot(
                    mDragViewVisualCenter[0] - mDropViewVisualCenter[0],
                    mDragViewVisualCenter[1] - mDropViewVisualCenter[1]);
            mTargetCell = cellX;
            setCurrentDropOverCell(mTargetCell);
            manageFolderFeedback(itemInfo, mDragTargetLayout, mTargetCell,
                    targetCellDistance, toView, d.accessibleDrag, d);
            boolean dropInTargetArea = targetCellDistance < mMinDistanceForSwapPosition;
            if (mDragMode == DRAG_MODE_NONE && haveEnoughSpace(d)){
                if (!dropInTargetArea && !mDragFromExternal){
                    mReorderAlarm.cancelAlarm();
                }else {
                    // Otherwise, if we aren't adding to or creating a folder and there's no pending
                    // reorder, then we schedule a reorder
                    if (!mReorderAlarm.alarmPending() && !mLastDropInTargetArea){
                        listener = new ReorderAlarmListener(d);
                        mReorderAlarm.setOnAlarmListener(listener);
                        mReorderAlarm.setAlarm(REORDER_TIMEOUT);
                    }
                }
            }
            mLastDropInTargetArea = dropInTargetArea;
        }else {
            mLastDropInTargetArea = false;
            if (mDragMode != DRAG_MODE_NONE && haveEnoughSpace(d)){
                listener = new ReorderAlarmListener(d);
                mReorderAlarm.setOnAlarmListener(listener);
                mReorderAlarm.setAlarm(0);
                setDragMode(DRAG_MODE_NONE);
            }
        }

        if ( mDragMode == DRAG_MODE_ADD_TO_FOLDER || mDragMode == DRAG_MODE_CREATE_FOLDER){
            mReorderAlarm.cancelAlarm();
        }
    }

    public void updateDragFromExternal(boolean dragFromExternal){
        mDragFromExternal =  dragFromExternal;
    }

    public boolean isDropEnabled() {
        return true;
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        ItemInfo iteminfo = (ItemInfo) d.dragInfo;
        d.deferDragViewCleanupPostAnimation = false;
        int pos = isLand() ? d.y / mItemHeight : d.x / mItemWidth;
        int cellX = getCellXByPos(pos);
        View dropTargetLayout = getChildAt(cellX);
        if (dropTargetLayout == null){
            return false;
        }
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        mDropViewVisualCenter = getVisualCenter(pos, dropTargetLayout.getWidth(),
                dropTargetLayout.getHeight());
        float targetCellDistance = (float) Math.hypot(mDragViewVisualCenter[0]
                        - mDropViewVisualCenter[0],
                mDragViewVisualCenter[1] - mDropViewVisualCenter[1]);
        if (mCreateUserFolderOnDrop && willCreateUserFolder((ItemInfo) d.dragInfo,
                mContentHotSeat, mTargetCell, targetCellDistance, true, d)) {
            return true;
        }
        if(mAddToExistingFolderOnDrop && willAddToExistingUserFolder(iteminfo,
                mContentHotSeat,cellX,targetCellDistance)){
            return true;
        }

        if (iteminfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER
                && haveEnoughSpace(d)) {
            return true;
        }

        if (iteminfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                && iteminfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_FOLDER
                && haveEnoughSpace(d)) {
            return true;
        } else {
            d.cancelled = true;
            return false;
        }
    }

    @Override
    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }

    private void handleDropCompleted(DropParams param) {
        if (mWillSetDragViewVisible || !param.success || param.target instanceof InfoDropTarget) {
            if (mDragView != null) {
                mDragView.setVisibility(View.VISIBLE);
            }
        } else {
            if (mDragView != null && mDragView.getVisibility() != GONE) {
                animateSetViewVisibility(mDragView, GONE);
            }
        }

        boolean beingCalledAfterUninstall = mDeferredAction != null;
        if ((param.d.cancelled || (beingCalledAfterUninstall && !mUninstallSuccessful))
                && mDragView != null) {
            mDragView.setVisibility(VISIBLE);
        }

        mLauncher.mWorkspace.onDropChilds(param.d, param.success);
        if (beingCalledAfterUninstall || mNewFolderCreated) {
            updateDockItems();
            resetState();
        }
    }

    private class DropParams {
        View target;
        DragObject d;
        boolean isFlingToDelete;
        boolean success;
        View dragView;
    }

    private Handler myHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FOLDER_CREATE_ANIMATION_INNER:
                    handleDropCompleted((DropParams) msg.obj);
                    mNewFolderCreated = false;
                    break;
                case FOLDER_CREATE_ANIMATION_OUTER:
                    animateSetViewVisibility(mDragView, GONE);
                    mNewFolderCreated = false;
                    break;
            }
            super.handleMessage(msg);
        }
    };

    /**
     * Called at the end of a drag which originated on the hotseat.
     */
    public void onDropCompleted(final View target, final DragObject d,
            final boolean isFlingToDelete, final boolean success) {
        if (mDeferDropAfterUninstall) {
            mDeferredAction = new Runnable() {
                public void run() {
                    onDropCompleted(target, d, isFlingToDelete, success);
                    mDeferredAction = null;
                }
            };
            return;
        }

        DropParams param = new DropParams();
        param.d = d;
        param.target = target;
        param.isFlingToDelete = isFlingToDelete;
        param.success = success;
        if (mNewFolderCreated) {
            Message msg = myHandler.obtainMessage(FOLDER_CREATE_ANIMATION_INNER, param);
            myHandler.sendMessageDelayed(msg, 100);
        } else {
            handleDropCompleted(param);
        }
    }

    public void startDrag(CellLayout.CellInfo cellInfo) {
        mDragView = cellInfo.cell;

        // Make sure the drag was started by a long press as opposed to a long click.
        if (!mDragView.isInTouchMode()) {
            return;
        }
        mDragView.setVisibility(View.INVISIBLE);
        mDragView.clearFocus();
        mDragView.setPressed(false);
        mDragPos = getDragPosByCellX(cellInfo.cellX);
        mLauncher.getWorkspace().beginDragShared(mDragView, this, false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return ev.getPointerCount() > 1 || super.onInterceptTouchEvent(ev);
    }

    @Override
    public void onFlingToDeleteCompleted() {
    }

    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        mLauncher.getWorkspace().onDragStart(source, info, dragAction);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    @Override
    public void onFlingToDelete(DragObject dragObject, PointF vec) {
    }

    @Override
    public float getIntrinsicIconScaleFactor() {
        return 1f;
    }

    @Override
    public void onDragEnd() {
        if (mDragView != null && !mDeferDropAfterUninstall && !mNewFolderCreated) {
            updateDockItems();
            resetState();
        }
    }

    private void resetState(){
        mDragView = null;
        mLastDropInTargetArea = false;
        mWillSetDragViewVisible = false;
        mDragPos = -1;
    }

    private void updateDockItems(){
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            View child = getChildAt(i);
            if (child == null || child.getTag() == null) {
                continue;
            }
            if (child.getVisibility() == GONE) {
                child.setTag(null);
                continue;
            }
            ItemInfo info = (ItemInfo) child.getTag();
            info.cellX = i;
            LauncherModel.addOrMoveItemInDatabase(mLauncher, info, LauncherSettings.
                    Favorites.CONTAINER_HOTSEAT, HOTSEAT_SCREEN, i, 0);
        }
    }

    @Override
    public boolean supportsDeleteDropTarget() {
        return false;
    }

    @Override
    public boolean supportsAppInfoDropTarget() {
        return false;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return false;
    }

    private int getEmptySeatPosition() {
        int result = -1;
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            View v = getChildAt(i);
            if (v != null && v.getVisibility() == GONE) {
                result = i;
                break;
            }
        }
        return result;
    }

    private boolean haveEnoughSpace(DragObject d){
        int count = d.snapDragViews.size() + 1 ;
        return count <= MAX_HOTSEAT - getShortcutAdded();
    }

    private int getInVisibleViewPosition(){
        for (int i=0;i<MAX_HOTSEAT;i++){
            View v = getChildAt(i);
            if (v.getVisibility() == INVISIBLE){
                return i;
            }
        }
        return -1;
    }

    private int getVisibleCount() {
        int result = 0;
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            View v = getChildAt(i);
            if (v == null) {
            }
            if (v != null && v.getVisibility() != GONE) {
                result++;
            }
        }
        return result;
    }

    private int getShortcutAdded() {
        int result = 0;
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            View v = getChildAt(i);
            if (v != null && v.getVisibility() == VISIBLE && v.getTag() != null) {
                result++;
            }
        }
        return result;
    }

    private int getDragPosByCellX(int cellX) {
        int result = 0;
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            if (i == cellX) {
                break;
            }
            View v = getChildAt(i);
            if (v != null && v.getVisibility() != GONE) {
                result++;
            }
        }
        return result;
    }

    public int getCellXByPos(int pos) {
        int result = 0;
        int i = 0;
        for (; i < MAX_HOTSEAT; i++) {
            View v = getChildAt(i);
            if (v != null && v.getVisibility() == View.GONE) {
                continue;
            }
            if (result == pos) {
                break;
            }
            result++;
        }
        if (i > MAX_HOTSEAT - 1 && result == 0) {
            i = 0;
        }
        return i;
    }

    private boolean haveInVisibleViews(){
        for (int i=0;i<MAX_HOTSEAT;i++){
            View v = getChildAt(i);
            if (v.getVisibility() == INVISIBLE){
                return true;
            }
        }
        return false;
    }

    private void updateItemValidWidth() {
        int cnt = getVisibleCount();
        if (cnt != 0) {
            mItemWidth = (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / cnt;
        } else {
            mItemWidth = (getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
        }
    }

    private void updateItemValidHeight() {
        int cnt = getVisibleCount();
        if (cnt != 0) {
            mItemHeight = (getMeasuredHeight() - getPaddingTop() - getPaddingBottom()) / cnt;
        } else {
            mItemHeight = (getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
        }
    }

    class ReorderAlarmListener implements OnAlarmListener {
        final DragObject d;

        public ReorderAlarmListener(DragObject d) {
            this.d = d;
        }

        public void onAlarm(Alarm alarm) {
            final int emptyPos = getEmptySeatPosition();
            if (emptyPos != -1 && mDragFromExternal && !haveInVisibleViews()) {
                final View emptyView = getChildAt(emptyPos);
                emptyView.setVisibility(View.INVISIBLE);
                if (isLand()) {
                    updateItemValidHeight();
                    mDragPos = d.y / mItemHeight;
                } else {
                    updateItemValidWidth();
                    mDragPos = d.x / mItemWidth;
                }
                final int vericellx = getCellXByPos(mDragPos);
                if (vericellx < getChildCount()) {
                    animateView(new Runnable() {
                        @Override
                        public void run() {
                            removeView(emptyView);
                            addView(emptyView, vericellx);
                        }
                    });
                }
                mDragView = emptyView;
                updateDragFromExternal(false);
            }else {
                final int pos = isLand() ? d.y / mItemHeight : d.x / mItemWidth;
                final int cellX = getCellXByPos(pos);
                if (mDragPos != pos && cellX < getChildCount()) {
                    final int idx = getCellXByPos(mDragPos);
                    if (idx > getChildCount() - 1) {
                        return;
                    }
                    final View dragView = mDragView;
                    animateView(new Runnable() {
                        @Override
                        public void run() {
                            removeViewAt(idx);
                            if (dragView.getParent() != null) {
                                removeView(dragView);
                            }
                            addView(dragView, cellX);
                            mDragPos = pos;
                        }
                    });
                }
            }
        }
    }

    private void animateSetViewVisibility(final View view, final int visibility){
        animateView(new Runnable() {
            @Override
            public void run() {
                if (view != null)
                    view.setVisibility(visibility);
            }
        });
    }

    private void animateView(Runnable r){
        LayoutTransition oriLayoutTransition = getLayoutTransition();
        setLayoutTransition(null);
        r.run();
        startHotseatLayoutTransition(oriLayoutTransition);
    }
}
