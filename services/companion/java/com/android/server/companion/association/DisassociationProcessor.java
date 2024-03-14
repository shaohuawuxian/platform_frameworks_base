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

package com.android.server.companion.association;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
import static android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION;

import static com.android.internal.util.CollectionUtils.any;
import static com.android.server.companion.utils.RolesUtils.removeRoleHolderForAssociation;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.companion.AssociationInfo;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Slog;

import com.android.server.companion.CompanionApplicationController;
import com.android.server.companion.datatransfer.SystemDataTransferRequestStore;
import com.android.server.companion.presence.CompanionDevicePresenceMonitor;
import com.android.server.companion.transport.CompanionTransportManager;

/**
 * A class response for Association removal.
 */
@SuppressLint("LongLogTag")
public class DisassociationProcessor {

    private static final String TAG = "CDM_DisassociationProcessor";
    @NonNull
    private final Context mContext;
    @NonNull
    private final AssociationStore mAssociationStore;
    @NonNull
    private final PackageManagerInternal mPackageManagerInternal;
    @NonNull
    private final CompanionDevicePresenceMonitor mDevicePresenceMonitor;
    @NonNull
    private final SystemDataTransferRequestStore mSystemDataTransferRequestStore;
    @NonNull
    private final CompanionApplicationController mCompanionAppController;
    @NonNull
    private final CompanionTransportManager mTransportManager;
    private final OnPackageVisibilityChangeListener mOnPackageVisibilityChangeListener;
    private final ActivityManager mActivityManager;

    public DisassociationProcessor(@NonNull Context context,
            @NonNull ActivityManager activityManager,
            @NonNull AssociationStore associationStore,
            @NonNull PackageManagerInternal packageManager,
            @NonNull CompanionDevicePresenceMonitor devicePresenceMonitor,
            @NonNull CompanionApplicationController applicationController,
            @NonNull SystemDataTransferRequestStore systemDataTransferRequestStore,
            @NonNull CompanionTransportManager companionTransportManager) {
        mContext = context;
        mActivityManager = activityManager;
        mAssociationStore = associationStore;
        mPackageManagerInternal = packageManager;
        mOnPackageVisibilityChangeListener =
                new OnPackageVisibilityChangeListener();
        mDevicePresenceMonitor = devicePresenceMonitor;
        mCompanionAppController = applicationController;
        mSystemDataTransferRequestStore = systemDataTransferRequestStore;
        mTransportManager = companionTransportManager;
    }

    /**
     * Disassociate an association by id.
     */
    // TODO: also revoke notification access
    public void disassociate(int id) {
        Slog.i(TAG, "Disassociating id=[" + id + "]...");

        final AssociationInfo association = mAssociationStore.getAssociationById(id);
        if (association == null) {
            Slog.e(TAG, "Can't disassociate id=[" + id + "]. It doesn't exist.");
            return;
        }

        final int userId = association.getUserId();
        final String packageName = association.getPackageName();
        final String deviceProfile = association.getDeviceProfile();

        final boolean isRoleInUseByOtherAssociations = deviceProfile != null
                && any(mAssociationStore.getActiveAssociationsByPackage(userId, packageName),
                    it -> deviceProfile.equals(it.getDeviceProfile()) && id != it.getId());

        final int packageProcessImportance = getPackageProcessImportance(userId, packageName);
        if (packageProcessImportance <= IMPORTANCE_VISIBLE && deviceProfile != null
                && !isRoleInUseByOtherAssociations) {
            // Need to remove the app from the list of role holders, but the process is visible
            // to the user at the moment, so we'll need to do it later.
            Slog.i(TAG, "Cannot disassociate id=[" + id + "] now - process is visible. "
                    + "Start listening to package importance...");

            AssociationInfo revokedAssociation = (new AssociationInfo.Builder(
                    association)).setRevoked(true).build();
            mAssociationStore.updateAssociation(revokedAssociation);
            startListening();
            return;
        }

        // Association cleanup.
        mAssociationStore.removeAssociation(association.getId());
        mSystemDataTransferRequestStore.removeRequestsByAssociationId(userId, id);

        // Detach transport if exists
        mTransportManager.detachSystemDataTransport(packageName, userId, id);

        // If role is not in use by other associations, revoke the role.
        // Do not need to remove the system role since it was pre-granted by the system.
        if (!isRoleInUseByOtherAssociations && deviceProfile != null && !deviceProfile.equals(
                DEVICE_PROFILE_AUTOMOTIVE_PROJECTION)) {
            removeRoleHolderForAssociation(mContext, association.getUserId(),
                    association.getPackageName(), association.getDeviceProfile());
        }

        // Unbind the app if needed.
        final boolean wasPresent = mDevicePresenceMonitor.isDevicePresent(id);
        if (!wasPresent || !association.isNotifyOnDeviceNearby()) {
            return;
        }
        final boolean shouldStayBound = any(
                mAssociationStore.getActiveAssociationsByPackage(userId, packageName),
                it -> it.isNotifyOnDeviceNearby()
                        && mDevicePresenceMonitor.isDevicePresent(it.getId()));
        if (!shouldStayBound) {
            mCompanionAppController.unbindCompanionApplication(userId, packageName);
        }
    }

    @SuppressLint("MissingPermission")
    private int getPackageProcessImportance(@UserIdInt int userId, @NonNull String packageName) {
        return Binder.withCleanCallingIdentity(() -> {
            final int uid =
                    mPackageManagerInternal.getPackageUid(packageName, /* flags */0, userId);
            return mActivityManager.getUidImportance(uid);
        });
    }

    private void startListening() {
        Slog.i(TAG, "Start listening to uid importance changes...");
        try {
            Binder.withCleanCallingIdentity(
                    () -> mActivityManager.addOnUidImportanceListener(
                            mOnPackageVisibilityChangeListener,
                            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE));
        }  catch (IllegalArgumentException e) {
            Slog.e(TAG, "Failed to start listening to uid importance changes.");
        }
    }

    private void stopListening() {
        Slog.i(TAG, "Stop listening to uid importance changes.");
        try {
            Binder.withCleanCallingIdentity(() -> mActivityManager.removeOnUidImportanceListener(
                    mOnPackageVisibilityChangeListener));
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Failed to stop listening to uid importance changes.");
        }
    }

    /**
     * An OnUidImportanceListener class which watches the importance of the packages.
     * In this class, we ONLY interested in the importance of the running process is greater than
     * {@link ActivityManager.RunningAppProcessInfo#IMPORTANCE_VISIBLE}.
     *
     * Lastly remove the role holder for the revoked associations for the same packages.
     *
     * @see #disassociate(int)
     */
    private class OnPackageVisibilityChangeListener implements
            ActivityManager.OnUidImportanceListener {

        @Override
        public void onUidImportance(int uid, int importance) {
            if (importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                // The lower the importance value the more "important" the process is.
                // We are only interested when the process ceases to be visible.
                return;
            }

            final String packageName = mPackageManagerInternal.getNameForUid(uid);
            if (packageName == null) {
                // Not interested in this uid.
                return;
            }

            int userId = UserHandle.getUserId(uid);
            for (AssociationInfo association : mAssociationStore.getRevokedAssociations(userId,
                    packageName)) {
                disassociate(association.getId());
            }

            if (mAssociationStore.getRevokedAssociations().isEmpty()) {
                stopListening();
            }
        }
    }
}
