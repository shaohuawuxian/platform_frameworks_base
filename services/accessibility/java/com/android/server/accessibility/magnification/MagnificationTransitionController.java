/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.accessibility.magnification;

import static android.provider.Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN;
import static android.provider.Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.provider.Settings;
import android.util.Slog;
import android.util.SparseArray;
import android.view.accessibility.MagnificationAnimationCallback;

import com.android.server.accessibility.AccessibilityManagerService;

/**
 * Handles magnification mode transition.
 */
public class MagnificationTransitionController {

    private static final boolean DEBUG = false;
    private static final String TAG = "MagnificationController";
    private final AccessibilityManagerService mAms;
    private final PointF mTempPoint = new PointF();
    private final Object mLock;
    private final SparseArray<DisableMagnificationCallback>
            mMagnificationEndRunnableSparseArray = new SparseArray();


    /**
     * A callback to inform the magnification transition result.
     */
    public interface TransitionCallBack {
        /**
         * Invoked when the transition ends.
         * @param success {@code true} if the transition success.
         */
        void onResult(boolean success);
    }

    public MagnificationTransitionController(AccessibilityManagerService ams, Object lock) {
        mAms = ams;
        mLock = lock;
    }

    /**
     * Transitions to the target Magnification mode with current center of the magnification mode
     * if it is available.
     *
     * @param displayId The logical display
     * @param targetMode The target magnification mode
     * @param transitionCallBack The callback invoked when the transition is finished.
     */
    public void transitionMagnificationModeLocked(int displayId, int targetMode,
            @NonNull TransitionCallBack transitionCallBack) {
        final PointF magnificationCenter = getCurrentMagnificationBoundsCenterLocked(displayId,
                targetMode);
        final DisableMagnificationCallback animationCallback =
                getDisableMagnificationEndRunnableLocked(displayId);
        if (magnificationCenter == null && animationCallback == null) {
            transitionCallBack.onResult(true);
            return;
        }

        if (animationCallback != null) {
            if (animationCallback.mCurrentMode == targetMode) {
                animationCallback.restoreToCurrentMagnificationMode();
                return;
            }
            Slog.w(TAG, "request during transition, abandon current:"
                    + animationCallback.mTargetMode);
            animationCallback.setExpiredAndRemoveFromListLocked();
        }

        if (magnificationCenter == null) {
            Slog.w(TAG, "Invalid center, ignore it");
            transitionCallBack.onResult(true);
            return;
        }
        final FullScreenMagnificationController screenMagnificationController =
                getFullScreenMagnificationController();
        final WindowMagnificationManager windowMagnificationMgr = getWindowMagnificationManager();
        final float scale = windowMagnificationMgr.getPersistedScale();
        final DisableMagnificationCallback animationEndCallback =
                new DisableMagnificationCallback(transitionCallBack, displayId, targetMode,
                        scale, magnificationCenter);
        if (targetMode == ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW) {
            screenMagnificationController.reset(displayId, animationEndCallback);
        } else {
            windowMagnificationMgr.disableWindowMagnification(displayId, false,
                    animationEndCallback);
        }
        setDisableMagnificationCallbackLocked(displayId, animationEndCallback);
    }

    private DisableMagnificationCallback getDisableMagnificationEndRunnableLocked(
            int displayId) {
        return mMagnificationEndRunnableSparseArray.get(displayId);
    }

    private void setDisableMagnificationCallbackLocked(int displayId,
            @Nullable DisableMagnificationCallback callback) {
        mMagnificationEndRunnableSparseArray.put(displayId, callback);
        if (DEBUG) {
            Slog.d(TAG, "setDisableMagnificationCallbackLocked displayId = " + displayId
                    + ", callback = " + callback);
        }
    }

    private FullScreenMagnificationController getFullScreenMagnificationController() {
        return mAms.getFullScreenMagnificationController();
    }

    private WindowMagnificationManager getWindowMagnificationManager() {
        return mAms.getWindowMagnificationMgr();
    }

    private @Nullable
            PointF getCurrentMagnificationBoundsCenterLocked(int displayId, int targetMode) {
        if (targetMode == ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN) {
            final WindowMagnificationManager magnificationManager = getWindowMagnificationManager();
            if (!magnificationManager.isWindowMagnifierEnabled(displayId)) {
                return null;
            }
            mTempPoint.set(magnificationManager.getCenterX(displayId),
                    magnificationManager.getCenterY(displayId));
        } else {
            final FullScreenMagnificationController screenMagnificationController =
                    getFullScreenMagnificationController();
            if (!screenMagnificationController.isMagnifying(displayId)) {
                return null;
            }
            mTempPoint.set(screenMagnificationController.getCenterX(displayId),
                    screenMagnificationController.getCenterY(displayId));
        }
        return mTempPoint;
    }

    private final class DisableMagnificationCallback implements
            MagnificationAnimationCallback {
        private final TransitionCallBack mTransitionCallBack;
        private boolean mExpired = false;
        private final int mDisplayId;
        private final int mTargetMode;
        private final int mCurrentMode;
        private final float mCurrentScale;
        private final PointF mCurrentCenter = new PointF();

        DisableMagnificationCallback(TransitionCallBack transitionCallBack,
                int displayId, int targetMode, float scale, PointF currentCenter) {
            mTransitionCallBack = transitionCallBack;
            mDisplayId = displayId;
            mTargetMode = targetMode;
            mCurrentMode = mTargetMode ^ Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_ALL;
            mCurrentScale = scale;
            mCurrentCenter.set(currentCenter);
        }

        @Override
        public void onResult(boolean success) {
            synchronized (mLock) {
                if (DEBUG) {
                    Slog.d(TAG, "onResult success = " + success);
                }
                if (mExpired) {
                    return;
                }
                setExpiredAndRemoveFromListLocked();
                if (success) {
                    adjustCurrentCenterIfNeededLocked();
                    applyMagnificationModeLocked(mTargetMode);
                }
                mTransitionCallBack.onResult(success);
            }
        }

        private void adjustCurrentCenterIfNeededLocked() {
            if (mTargetMode == ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW) {
                return;
            }
            final Region outRegion = new Region();
            getFullScreenMagnificationController().getMagnificationRegion(mDisplayId, outRegion);
            if (outRegion.contains((int) mCurrentCenter.x, (int) mCurrentCenter.y)) {
                return;
            }
            final Rect bounds = outRegion.getBounds();
            mCurrentCenter.set(bounds.exactCenterX(), bounds.exactCenterY());
        }

        void restoreToCurrentMagnificationMode() {
            synchronized (mLock) {
                if (mExpired) {
                    return;
                }
                setExpiredAndRemoveFromListLocked();
                applyMagnificationModeLocked(mCurrentMode);
                mTransitionCallBack.onResult(true);
            }
        }

        void setExpiredAndRemoveFromListLocked() {
            mExpired = true;
            setDisableMagnificationCallbackLocked(mDisplayId, null);
        }

        private void applyMagnificationModeLocked(int mode) {
            if (mode == ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN) {
                getFullScreenMagnificationController().setScaleAndCenter(mDisplayId,
                        mCurrentScale, mCurrentCenter.x,
                        mCurrentCenter.y, true,
                        AccessibilityManagerService.MAGNIFICATION_GESTURE_HANDLER_ID);
            } else {
                getWindowMagnificationManager().enableWindowMagnification(mDisplayId,
                        mCurrentScale, mCurrentCenter.x,
                        mCurrentCenter.y);
            }
        }
    }
}
