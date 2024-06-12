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

package com.android.systemui.statusbar.chips.mediaprojection.ui.view

import android.content.ComponentName
import android.content.Intent
import android.content.packageManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testCase
import com.android.systemui.mediaprojection.data.model.MediaProjectionState
import com.android.systemui.mediaprojection.taskswitcher.FakeActivityTaskManager.Companion.createTask
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.mockSystemUIDialogFactory
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
class EndMediaProjectionDialogHelperTest : SysuiTestCase() {
    private val kosmos = Kosmos().also { it.testCase = this }

    private val underTest = kosmos.endMediaProjectionDialogHelper

    @Test
    fun createDialog_usesDelegateAndFactory() {
        val dialog = mock<SystemUIDialog>()
        val delegate = SystemUIDialog.Delegate { dialog }
        whenever(kosmos.mockSystemUIDialogFactory.create(eq(delegate))).thenReturn(dialog)

        underTest.createDialog(delegate)

        verify(kosmos.mockSystemUIDialogFactory).create(delegate)
    }

    @Test
    fun getDialogMessage_notProjecting_isGenericMessage() {
        val result =
            underTest.getDialogMessage(
                MediaProjectionState.NotProjecting,
                R.string.accessibility_home,
                R.string.cast_to_other_device_stop_dialog_message_specific_app,
            )

        assertThat(result).isEqualTo(context.getString(R.string.accessibility_home))
    }

    @Test
    fun getDialogMessage_entireScreen_isGenericMessage() {
        val result =
            underTest.getDialogMessage(
                MediaProjectionState.Projecting.EntireScreen("host.package"),
                R.string.accessibility_home,
                R.string.cast_to_other_device_stop_dialog_message_specific_app
            )

        assertThat(result).isEqualTo(context.getString(R.string.accessibility_home))
    }

    @Test
    fun getDialogMessage_singleTask_cannotFindPackage_isGenericMessage() {
        val baseIntent =
            Intent().apply { this.component = ComponentName("fake.task.package", "cls") }
        whenever(kosmos.packageManager.getApplicationInfo(eq("fake.task.package"), any<Int>()))
            .thenThrow(PackageManager.NameNotFoundException())

        val projectionState =
            MediaProjectionState.Projecting.SingleTask(
                "host.package",
                createTask(taskId = 1, baseIntent = baseIntent)
            )

        val result =
            underTest.getDialogMessage(
                projectionState,
                R.string.accessibility_home,
                R.string.cast_to_other_device_stop_dialog_message_specific_app
            )

        assertThat(result).isEqualTo(context.getString(R.string.accessibility_home))
    }

    @Test
    fun getDialogMessage_singleTask_findsPackage_isSpecificMessageWithAppLabel() {
        val baseIntent =
            Intent().apply { this.component = ComponentName("fake.task.package", "cls") }
        val appInfo = mock<ApplicationInfo>()
        whenever(appInfo.loadLabel(kosmos.packageManager)).thenReturn("Fake Package")
        whenever(kosmos.packageManager.getApplicationInfo(eq("fake.task.package"), any<Int>()))
            .thenReturn(appInfo)

        val projectionState =
            MediaProjectionState.Projecting.SingleTask(
                "host.package",
                createTask(taskId = 1, baseIntent = baseIntent)
            )

        val result =
            underTest.getDialogMessage(
                projectionState,
                R.string.accessibility_home,
                R.string.cast_to_other_device_stop_dialog_message_specific_app
            )

        // It'd be nice to use the R.string resources directly, but they include the <b> tags which
        // aren't in the returned string.
        assertThat(result.toString()).isEqualTo("You will stop casting Fake Package")
    }

    @Test
    fun getDialogMessage_appLabelHasSpecialCharacters_isEscaped() {
        val baseIntent =
            Intent().apply { this.component = ComponentName("fake.task.package", "cls") }
        val appInfo = mock<ApplicationInfo>()
        whenever(appInfo.loadLabel(kosmos.packageManager)).thenReturn("Fake & Package <Here>")
        whenever(kosmos.packageManager.getApplicationInfo(eq("fake.task.package"), any<Int>()))
            .thenReturn(appInfo)

        val projectionState =
            MediaProjectionState.Projecting.SingleTask(
                "host.package",
                createTask(taskId = 1, baseIntent = baseIntent)
            )

        val result =
            underTest.getDialogMessage(
                projectionState,
                R.string.accessibility_home,
                R.string.cast_to_other_device_stop_dialog_message_specific_app
            )

        assertThat(result.toString()).isEqualTo("You will stop casting Fake & Package <Here>")
    }
}
