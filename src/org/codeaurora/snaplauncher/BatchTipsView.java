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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.launcher3.DragController;
import com.android.launcher3.DragSource;
import com.android.launcher3.Launcher;

import org.codeaurora.snaplauncher.R;

public class BatchTipsView extends TextView implements DragController.DragListener {

    private CharSequence mDisplayStr;
    private CharSequence INITIAL_GUIDE_STR;
    private CharSequence DRAG_START_GUIDE_STR;
    private CharSequence APPS_SELECTED_GUIDE_STR;

    public BatchTipsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatchTipsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initParams(context);
    }

    private void initParams(Context context) {
        INITIAL_GUIDE_STR = context.getResources().
                getText(R.string.batch_tips_target_label_initial);
        DRAG_START_GUIDE_STR = context.getResources().
                getText(R.string.batch_tips_target_drag_start_label);
        APPS_SELECTED_GUIDE_STR = context.getResources().
                getText(R.string.batch_tips_target_drag_label_selected);
    }

    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        setText(DRAG_START_GUIDE_STR);
    }

    @Override
    public void onDragEnd() {
        Launcher launcher = (Launcher) getContext();
        updateText(launcher.getBatchArrangeAppsAll().size());
    }

    public void reset() {
        setText(INITIAL_GUIDE_STR);
    }

    public void updateText(int size) {
        if (size == 0) {
            mDisplayStr = INITIAL_GUIDE_STR;
        } else {
            mDisplayStr = size + " " + APPS_SELECTED_GUIDE_STR;
        }

        setText(mDisplayStr);
    }
}
