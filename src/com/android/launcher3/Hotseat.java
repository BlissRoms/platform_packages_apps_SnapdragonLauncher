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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.graphics.PointF;
import com.android.launcher3.DragController.DragListener;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;
import org.codeaurora.snaplauncher.R;

public class Hotseat extends LinearLayout implements DragSource, DropTarget,
    OnLongClickListener, DragListener {

    private static final int MAX_HOTSEAT = 5;
    private  Hotseat mContentHotSeat;
    private static final int HOTSEAT_SCREEN = -1;

    private Launcher mLauncher;
    private int mItemWidth;
    private int mItemHeight;
    private IconCache mIconCache;
    private static View mDragView;
    private ItemInfo mDragInfo;
    private int mDragPos = -1;
    private static boolean isSwap = false;
    private static boolean bSuccess = true;
    private static boolean bExchange = false;
    private static boolean bUninstall = false;

    private static final int DRAG_MODE_NONE = 0;
    private static final int DRAG_MODE_ADD_TO_FOLDER = 1;
    private int mDragMode = DRAG_MODE_NONE;

    private final boolean mHasVerticalHotseat;

    public static boolean mDragFromWorkspace = false;
    private  boolean dropTargetIsFolder = false;
    private  boolean mDropTargetIsInfoDrop = false;

    int mTargetCell = -1;
    float[] mDragViewVisualCenter = new float[2];
    float[] mDropViewVisualCenter = new float[2];
    private  boolean mAddToExistingFolderOnDrop = false;
    private static  View mCurrentDropLayout = null;
    private FolderIcon mDragOverFolderIcon = null;
    private int mDragOverX = -1;

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    @Thunk final int[] mTempLocation = new int[2];
    private ArrayList<FolderIcon.FolderRingAnimator> mFolderOuterRings =
            new ArrayList<FolderIcon.FolderRingAnimator>();

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
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
        final int hStartPadding = getPaddingLeft();
        final int vStartPadding = getPaddingTop();
        result[0] = hStartPadding + cellX * mItemWidth;
        result[1] = (int) (vStartPadding + cellY * (getResources().
                getDimension(R.dimen.hotseat_height)));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (isLand()){
            setOrientation(VERTICAL);
            updateItemValidHeight();
        }else{
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
                int centerX = mTempLocation[0] + mItemWidth / 2;
                int centerY = mTempLocation[1] + previewOffset / 2 +
                        child.getPaddingTop() + grid.folderBackgroundOffset;

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

    public float getChildrenScale() {
        return  1.0f;
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

    public boolean isLand(){
        Configuration configuration = getResources().getConfiguration();
        int  ori = configuration.orientation;
        boolean isLand = false;
        if(ori == configuration.ORIENTATION_LANDSCAPE){
            isLand = true;
        }
        if(ori == configuration.ORIENTATION_PORTRAIT){
            isLand = false;
        }
        return isLand;
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
        if(item.itemType ==  LauncherSettings.Favorites.ITEM_TYPE_FOLDER){

            FolderIcon mFolderView;
            FolderInfo info = (FolderInfo) item;
            mFolderView= createFolderSeat(view, info);
            mFolderView.setTag(info);
            mFolderView.setOnLongClickListener(this);
            if(visible) {
                mFolderView.setVisibility(View.VISIBLE);
            }else{
                mFolderView.setVisibility(View.INVISIBLE);
            }
            return  mFolderView;
        }else {
            BubbleTextView hotseatV;

            if(view instanceof  BubbleTextView){
                hotseatV = (BubbleTextView) view;
            }else {
                hotseatV = createBubbleTextSeat(view,item);
            }

            hotseatV.setIsHotseat(true);
            ShortcutInfo info = (ShortcutInfo) item;
            mLauncher.createHotseatShortcut(hotseatV, info);
            hotseatV.setTag(info);
            hotseatV.setOnLongClickListener(this);
            if(visible){
                hotseatV.setVisibility(View.VISIBLE);
            }else {
                hotseatV.setVisibility(View.INVISIBLE);
            }
            return  hotseatV;
        }
    }

    private BubbleTextView createBubbleTextSeat(View view, ItemInfo item) {
        removeView(view);
        BubbleTextView favorite = (BubbleTextView) mLauncher.getLayoutInflater().
                inflate(R.layout.app_icon,mContentHotSeat, false);
        if(isLand()){
            favorite.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.FILL_PARENT, 0,1.0f));
        }else{
            favorite.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LayoutParams.FILL_PARENT, 1.0f));
        }

        addView(favorite,item.cellX);
        return  favorite;
    }

    private FolderIcon createFolderSeat(View view,FolderInfo info) {
        removeView(view);
        FolderIcon folderView = FolderIcon.fromXml(R.layout.folder_icon, mLauncher,
                mContentHotSeat,info, mLauncher.mIconCache);
        if(isLand()) {
            folderView.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.FILL_PARENT, 0, 1.0f));
        }else{
            folderView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LayoutParams.FILL_PARENT, 1.0f));
        }

        addView(folderView, info.cellX);
        return  folderView;
    }

    boolean addToExistingFolderIfNecessary( Hotseat target, int targetCell,
                                            float distance, DragObject d) {
        if (distance > mLauncher.getWorkspace().mMaxDistanceForFolderCreation) return false;
        View dropOverView = target.getChildAt(targetCell);
        if (!mAddToExistingFolderOnDrop) return false;
        mAddToExistingFolderOnDrop = false;

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(d.dragInfo)) {
                fi.onDrop(d);
                return true;
            }
        }
        return false;
    }

    void setCurrentDropOverCell(int x) {
        if (x != mDragOverX) {
            mDragOverX = x;
            setDragMode(DRAG_MODE_NONE);
        }
    }

    public void setCurrentDropLayout(LinearLayout layout) {
        setCurrentDropOverCell(-1);
    }

    @Override
    public void onDrop(DragObject d) {
        int pos ;
        if(isLand()){
            pos = d.y / mItemHeight;
        }else {
            pos = d.x / mItemWidth;
        }
        if (pos <= 0 ) {
            pos = 0;
        } else if (pos > getVisibleCnt() - 1){
            pos =  getVisibleCnt() - 1;
        }
        int cellx = getCellXByPos(pos);

        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        mDropViewVisualCenter = getVisualCenter(cellx,mCurrentDropLayout.getWidth(),
                mCurrentDropLayout.getHeight());
        float targetCellDistance = (float) Math.hypot(
                mDragViewVisualCenter[0]- mDropViewVisualCenter[0],
                mDragViewVisualCenter[1] - mDropViewVisualCenter[1]);

        if(addToExistingFolderIfNecessary(mContentHotSeat,cellx,targetCellDistance,d)){
            return ;
        }

        ItemInfo dragInfo = (ItemInfo) d.dragInfo;
        if (dragInfo == null || dragInfo.container == LauncherSettings.
                Favorites.CONTAINER_HOTSEAT) {
            isSwap = true;
            return;
        }
        ItemInfo seatinfo = (ItemInfo) getChildAt(cellx).getTag();   // hotseat item
        mDragInfo = seatinfo;
        isSwap = true;
        if (seatinfo != null) { // exchange item
            Workspace workspace = mLauncher.getWorkspace();
                if (d.dragSource instanceof Workspace) {
                    bExchange = true;
                    View child;
                    if(seatinfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
                        child = FolderIcon.fromXml(R.layout.folder_icon, mLauncher,
                                mContentHotSeat, (FolderInfo) seatinfo, mLauncher.mIconCache);
                    }else{
                        child = mLauncher.createShortcut((ShortcutInfo) seatinfo);
                    }
                    workspace.addInScreen(child, dragInfo.container, dragInfo.screenId,
                            dragInfo.cellX, dragInfo.cellY, dragInfo.spanX, dragInfo.spanY);
                    LauncherModel.moveItemInDatabase(mLauncher, seatinfo, dragInfo.container,
                            dragInfo.screenId, dragInfo.cellX, dragInfo.cellY);
                }

            isSwap = false;
            mDragView = null;
            mDragPos = -1;
        }
        LauncherModel.moveItemInDatabase(mLauncher, dragInfo,
                LauncherSettings.Favorites.CONTAINER_HOTSEAT, HOTSEAT_SCREEN, cellx, 0);
        setSeat(dragInfo, true);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = (Launcher) context;
        mHasVerticalHotseat = mLauncher.getDeviceProfile().isVerticalBarLayout();
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

        LayoutTransition transition = new LayoutTransition();
        transition.setAnimator(LayoutTransition.APPEARING,null);
        transition.setAnimator(LayoutTransition.DISAPPEARING, null);
        transition.setStagger(LayoutTransition.CHANGE_APPEARING, 0);
        transition.setStagger(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transition.setAnimateParentHierarchy(true);
        transition.setDuration(400);
        mContentHotSeat.setLayoutTransition(transition);
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
        mAddToExistingFolderOnDrop = false;
        setCurrentDropTarget(null);
        LinearLayout layout = getLayout();
        setCurrentDropLayout(layout);
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(this, outRect);
    }

    public void prepareAccessibilityDrop() {}

    @Override
    public void onDragExit(DragObject dragObject) {
        if (mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
            mAddToExistingFolderOnDrop = true;
        }
        setCurrentDropLayout(null);
    }

    public static void  setDragViewVisibility(boolean visible){
        if(mDragView != null) {
            if (visible) {
                mDragView.setVisibility(View.VISIBLE);
            } else {
                mDragView.setVisibility(View.GONE);
            }
        }
    }

    boolean willAddToExistingUserFolder(Object dragInfo, Hotseat target, int targetCell,
                                        float distance) {
        if (distance > mLauncher.getWorkspace().mMaxDistanceForFolderCreation) return false;
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
                                      View dragOverView, boolean accessibleDrag) {
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

        return;
    }

    public final float[] getVisualCenter(int cellX,float toViewWidth,float toViewHeight) {
        final float res[] = new float[2];
        WindowManager wm = (WindowManager) getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        // In order to find the visual center, we shift by half the dragRect
        res[0] = getPaddingLeft() + toViewWidth * cellX +toViewWidth/2;
        res[1] = toViewHeight/2;

        return res;
    }

    private  void  setCurrentDropTarget(View dropTargetLayout){
        mCurrentDropLayout = dropTargetLayout;
    }

    @Override
    public void onDragOver(final DragObject d) {
        int pos ;
        if(isLand()){
            pos = d.y / mItemHeight;
        }else {
            pos = d.x / mItemWidth;
        }
        int cellX = getCellXByPos(pos);
        View toView = getChildAt(cellX);
        setCurrentDropTarget(toView);

        if(toView != null && toView.getVisibility() == View.VISIBLE) {
            ItemInfo itemInfo = (ItemInfo) d.dragInfo;
            Hotseat mDragTargetLayout = mContentHotSeat;
            mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
            mDropViewVisualCenter = getVisualCenter(cellX, toView.getWidth(), toView.getHeight());
            float targetCellDistance = (float) Math.hypot(
                    mDragViewVisualCenter[0] - mDropViewVisualCenter[0],
                    mDragViewVisualCenter[1] - mDropViewVisualCenter[1]);
            mLauncher.getWorkspace().mapPointFromSelfToHotseatLayout(mLauncher.getHotseat(),
                    mDragViewVisualCenter);
            mTargetCell = cellX;
            setCurrentDropOverCell(mTargetCell);
            manageFolderFeedback(itemInfo, mDragTargetLayout, mTargetCell,
                    targetCellDistance, toView, d.accessibleDrag);
        }
        if(toView instanceof FolderIcon){
            dropTargetIsFolder = true;
        }

        final int emptyPos = getEmptySeatPos();
        if (mDragView == null && emptyPos == -1){
            return;
        }
        if(emptyPos != -1 && mDragFromWorkspace){
            View emptyView = null;
            emptyView = getChildAt(emptyPos);
            emptyView.setVisibility(View.INVISIBLE);
            if (isLand()) {
                updateItemValidHeight();
                mDragPos = d.y / mItemHeight;
            } else {
                updateItemValidWidth();
                mDragPos = d.x / mItemWidth;
            }

            int vericellx = getCellXByPos(mDragPos);
            if (vericellx < getChildCount()) {
                removeView(emptyView);
                addView(emptyView, vericellx);
            }
            if(mDragView != null) {
                ItemInfo info = (ItemInfo) mDragView.getTag();
                if(null != info){
                    info.cellX = vericellx;
                    View view = setSeat(info, false);
                    mDragView = view;
                    mDragFromWorkspace = false;
                    return;
                }
            }

            mDragView = emptyView;
            mDragFromWorkspace = false;
            return;
        }

        int dragPos = mDragPos;
        if (dragPos == pos || cellX > MAX_HOTSEAT - 1) {
            return;
        }
        View dragView = mDragView;
        int idx = getCellXByPos(dragPos);
        if (idx > getChildCount() - 1) {
            return;
        }
        removeViewAt(idx);
        if (dragView.getParent() != null) {
            removeView(dragView);
        }
        addView(dragView, cellX);
        mDragPos = pos;
    }

    public boolean isDropEnabled() {
        return true;
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        ItemInfo iteminfo = (ItemInfo)d.dragInfo;
        d.deferDragViewCleanupPostAnimation = false;
        int pos ;
        if(isLand()){
            pos = d.y / mItemHeight;
        }else {
            pos = d.x / mItemWidth;
        }
        int cellX = getCellXByPos(pos);
        View dropTargetLayout = mCurrentDropLayout;
        if(dropTargetLayout == null){
            return  false;
        }
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        mDropViewVisualCenter = getVisualCenter(cellX,dropTargetLayout.getWidth(),
                dropTargetLayout.getHeight());
        float targetCellDistance = (float) Math.hypot(mDragViewVisualCenter[0]
                - mDropViewVisualCenter[0],
                mDragViewVisualCenter[1] - mDropViewVisualCenter[1]);
        if(mAddToExistingFolderOnDrop && willAddToExistingUserFolder(iteminfo,
                mContentHotSeat,cellX,targetCellDistance)){
            return true;
        }

        if(iteminfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER &&
                getShortcutAdded() < MAX_HOTSEAT){
            return  true;
        }

        if (iteminfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                && iteminfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_FOLDER
                && getShortcutAdded() < MAX_HOTSEAT) {
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

    @Override
    public void onDropCompleted(View target, DragObject d, boolean isFlingToDelete,
                boolean success) {
        // if swap items in hotseat, dragview should be visible
        bSuccess = success;
        if (target instanceof DeleteDropTarget
                && ((ItemInfo) d.dragInfo).itemType == LauncherSettings.
                Favorites.ITEM_TYPE_APPLICATION) {
            bUninstall = true;
        }
        if(target instanceof InfoDropTarget){
            mDropTargetIsInfoDrop = true;
        }
        if (isSwap || !success || bUninstall || mDropTargetIsInfoDrop) {
            if (mDragView != null) {
                mDragView.setVisibility(View.VISIBLE);
            }
        } else {
            if (mDragView != null) {
                mDragView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (mLauncher.isWorkspaceLocked()) {
            return true;
        }

        ItemInfo info = (ItemInfo) view.getTag();
        mDragView = view;
        mDragView.setPressed(false);
        mDragPos = getDragPosBycellX(info.cellX);
        mLauncher.getWorkspace().beginDragShared(view, this, false);
        view.setVisibility(View.INVISIBLE);
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getPointerCount() > 1) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public void onFlingToDeleteCompleted() {
    }

    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        mLauncher.getWorkspace().onDragStart(source,info,dragAction);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    @Override
    public void onFlingToDelete(DragObject dragObject, PointF vec) {}

    @Override
    public float getIntrinsicIconScaleFactor() {
        return 1f;
    }

    @Override
    public void onDragEnd() {
        if (mDragView == null) {
            return;
        }
        ItemInfo fromInfo = (ItemInfo) mDragInfo;
        if (fromInfo == null && !isSwap && bSuccess && !bUninstall && !mDropTargetIsInfoDrop) {
            mDragView.setVisibility(View.GONE);
            mDragView.setTag(null);
        }
        bSuccess = true;
        isSwap = false;
        mDropTargetIsInfoDrop = false;

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
        bExchange = false;
        bUninstall = false;
        mDragPos = -1;
        mDragInfo = null;
        mDragView = null;
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

    private int getEmptySeatPos() {
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

    private int getVisibleCnt() {
        int result = 0;
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            View v = getChildAt(i);
            if(v == null){
            }
            if (v != null && v.getVisibility() != GONE) {
                result++;
            }
        }
        return result;
    }

    private int getShortcutAdded(){
        int result = 0;
        for (int i = 0; i < MAX_HOTSEAT; i++) {
            View v = getChildAt(i);
                if (v != null && v.getVisibility() != GONE && v.getTag() != null) {
                result++;
            }
        }
        return result;
    }

    private int getDragPosBycellX(int cellX) {
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

    private void updateItemValidWidth() {
        int cnt = getVisibleCnt();
        if (cnt != 0) {
            mItemWidth = (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / cnt;
        } else {
            mItemWidth = (getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
        }
    }

    private void updateItemValidHeight() {
        int cnt = getVisibleCnt();
        if (cnt != 0) {
            mItemHeight = (getMeasuredHeight() - getPaddingTop() - getPaddingBottom()) / cnt;
        } else {
            mItemHeight = (getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
        }
    }

}
