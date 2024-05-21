/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.servertransaction;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ClientTransactionHandler;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Log;
import android.view.IWindow;
import android.view.InsetsSourceControl;
import android.view.InsetsState;

import java.util.Objects;

/**
 * Message to deliver window insets control change info.
 * @hide
 */
public class WindowStateInsetsControlChangeItem extends ClientTransactionItem {

    private static final String TAG = "WindowStateInsetsControlChangeItem";

    private IWindow mWindow;
    private InsetsState mInsetsState;
    private InsetsSourceControl.Array mActiveControls;

    @Override
    public void execute(@NonNull ClientTransactionHandler client,
            @NonNull PendingTransactionActions pendingActions) {
        Trace.traceBegin(Trace.TRACE_TAG_WINDOW_MANAGER, "windowInsetsControlChanged");
        if (mWindow instanceof InsetsControlChangeListener listener) {
            listener.onExecutingWindowStateInsetsControlChangeItem();
        }
        try {
            mWindow.insetsControlChanged(mInsetsState, mActiveControls);
        } catch (RemoteException e) {
            // Should be a local call.
            // An exception could happen if the process is restarted. It is safe to ignore since
            // the window should no longer exist.
            Log.w(TAG, "The original window no longer exists in the new process", e);
        }
        Trace.traceEnd(Trace.TRACE_TAG_WINDOW_MANAGER);
    }

    // ObjectPoolItem implementation

    private WindowStateInsetsControlChangeItem() {}

    /** Obtains an instance initialized with provided params. */
    public static WindowStateInsetsControlChangeItem obtain(@NonNull IWindow window,
            @NonNull InsetsState insetsState, @NonNull InsetsSourceControl.Array activeControls) {
        WindowStateInsetsControlChangeItem instance =
                ObjectPool.obtain(WindowStateInsetsControlChangeItem.class);
        if (instance == null) {
            instance = new WindowStateInsetsControlChangeItem();
        }
        instance.mWindow = requireNonNull(window);
        instance.mInsetsState = new InsetsState(insetsState, true /* copySources */);
        instance.mActiveControls = new InsetsSourceControl.Array(activeControls);

        return instance;
    }

    @Override
    public void recycle() {
        mWindow = null;
        mInsetsState = null;
        mActiveControls = null;
        ObjectPool.recycle(this);
    }

    // Parcelable implementation

    /** Writes to Parcel. */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStrongBinder(mWindow.asBinder());
        dest.writeTypedObject(mInsetsState, flags);
        dest.writeTypedObject(mActiveControls, flags);
    }

    /** Reads from Parcel. */
    private WindowStateInsetsControlChangeItem(@NonNull Parcel in) {
        mWindow = IWindow.Stub.asInterface(in.readStrongBinder());
        mInsetsState = in.readTypedObject(InsetsState.CREATOR);
        mActiveControls = in.readTypedObject(InsetsSourceControl.Array.CREATOR);

    }

    public static final @NonNull Creator<WindowStateInsetsControlChangeItem> CREATOR =
            new Creator<>() {
                public WindowStateInsetsControlChangeItem createFromParcel(@NonNull Parcel in) {
                    return new WindowStateInsetsControlChangeItem(in);
                }

                public WindowStateInsetsControlChangeItem[] newArray(int size) {
                    return new WindowStateInsetsControlChangeItem[size];
                }
            };

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WindowStateInsetsControlChangeItem other = (WindowStateInsetsControlChangeItem) o;
        return Objects.equals(mWindow, other.mWindow)
                && Objects.equals(mInsetsState, other.mInsetsState)
                && Objects.equals(mActiveControls, other.mActiveControls);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + Objects.hashCode(mWindow);
        result = 31 * result + Objects.hashCode(mInsetsState);
        result = 31 * result + Objects.hashCode(mActiveControls);
        return result;
    }

    @Override
    public String toString() {
        return "WindowStateInsetsControlChangeItem{window=" + mWindow + "}";
    }

    /** The interface for IWindow to perform insets control change directly if possible. */
    public interface InsetsControlChangeListener {
        /**
         * Notifies that IWindow#insetsControlChanged is going to be called from
         * WindowStateInsetsControlChangeItem.
         */
        void onExecutingWindowStateInsetsControlChangeItem();
    }
}
