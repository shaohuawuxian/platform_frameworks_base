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

package com.android.systemui.screenshot.policy

import android.content.ComponentName
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.screenshot.data.model.DisplayContentScenarios.ActivityNames.FILES
import com.android.systemui.screenshot.data.model.DisplayContentScenarios.ActivityNames.YOUTUBE
import com.android.systemui.screenshot.data.model.DisplayContentScenarios.Bounds.FREE_FORM
import com.android.systemui.screenshot.data.model.DisplayContentScenarios.Bounds.FULL_SCREEN
import com.android.systemui.screenshot.data.model.DisplayContentScenarios.Bounds.SPLIT_TOP
import com.android.systemui.screenshot.data.model.DisplayContentScenarios.TaskSpec
import com.android.systemui.screenshot.data.model.DisplayContentScenarios.freeFormApps
import com.android.systemui.screenshot.data.model.DisplayContentScenarios.pictureInPictureApp
import com.android.systemui.screenshot.data.model.DisplayContentScenarios.singleFullScreen
import com.android.systemui.screenshot.data.model.DisplayContentScenarios.splitScreenApps
import com.android.systemui.screenshot.data.repository.profileTypeRepository
import com.android.systemui.screenshot.policy.CapturePolicy.PolicyResult
import com.android.systemui.screenshot.policy.CapturePolicy.PolicyResult.NotMatched
import com.android.systemui.screenshot.policy.CaptureType.IsolatedTask
import com.android.systemui.screenshot.policy.TestUserIds.PERSONAL
import com.android.systemui.screenshot.policy.TestUserIds.WORK
import com.android.systemui.screenshot.policy.WorkProfilePolicy.Companion.DESKTOP_MODE_ENABLED
import com.android.systemui.screenshot.policy.WorkProfilePolicy.Companion.SHADE_EXPANDED
import com.android.systemui.screenshot.policy.WorkProfilePolicy.Companion.WORK_TASK_IS_TOP
import com.android.systemui.screenshot.policy.WorkProfilePolicy.Companion.WORK_TASK_NOT_TOP
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class WorkProfilePolicyTest {
    @JvmField @Rule val setFlagsRule = SetFlagsRule()

    private val kosmos = Kosmos()
    private val policy = WorkProfilePolicy(kosmos.profileTypeRepository)

    @Test
    fun noWorkApp_notMatched() = runTest {
        val result =
            policy.check(
                singleFullScreen(TaskSpec(taskId = 1002, name = YOUTUBE, userId = PERSONAL))
            )

        assertThat(result)
            .isEqualTo(
                NotMatched(
                    WorkProfilePolicy.NAME,
                    WORK_TASK_NOT_TOP,
                )
            )
    }

    @Test
    fun withWorkFullScreen_shadeExpanded_notMatched() = runTest {
        val result =
            policy.check(
                singleFullScreen(
                    TaskSpec(taskId = 1002, name = FILES, userId = WORK),
                    shadeExpanded = true
                )
            )

        assertThat(result)
            .isEqualTo(
                NotMatched(
                    WorkProfilePolicy.NAME,
                    SHADE_EXPANDED,
                )
            )
    }

    @Test
    fun withWorkFullScreen_matched() = runTest {
        val result =
            policy.check(singleFullScreen(TaskSpec(taskId = 1002, name = FILES, userId = WORK)))

        assertThat(result)
            .isEqualTo(
                PolicyResult.Matched(
                    policy = WorkProfilePolicy.NAME,
                    reason = WORK_TASK_IS_TOP,
                    CaptureParameters(
                        type = IsolatedTask(taskId = 1002, taskBounds = FULL_SCREEN),
                        component = ComponentName.unflattenFromString(FILES),
                        owner = UserHandle.of(WORK),
                    )
                )
            )
    }

    @Test
    fun withWorkFocusedInSplitScreen_matched() = runTest {
        val result =
            policy.check(
                splitScreenApps(
                    top = TaskSpec(taskId = 1002, name = FILES, userId = WORK),
                    bottom = TaskSpec(taskId = 1003, name = YOUTUBE, userId = PERSONAL),
                    focusedTaskId = 1002
                )
            )

        assertThat(result)
            .isEqualTo(
                PolicyResult.Matched(
                    policy = WorkProfilePolicy.NAME,
                    reason = WORK_TASK_IS_TOP,
                    CaptureParameters(
                        type = IsolatedTask(taskId = 1002, taskBounds = SPLIT_TOP),
                        component = ComponentName.unflattenFromString(FILES),
                        owner = UserHandle.of(WORK),
                    )
                )
            )
    }

    @Test
    fun withWorkNotFocusedInSplitScreen_notMatched() = runTest {
        val result =
            policy.check(
                splitScreenApps(
                    top = TaskSpec(taskId = 1002, name = FILES, userId = WORK),
                    bottom = TaskSpec(taskId = 1003, name = YOUTUBE, userId = PERSONAL),
                    focusedTaskId = 1003
                )
            )

        assertThat(result)
            .isEqualTo(
                NotMatched(
                    WorkProfilePolicy.NAME,
                    WORK_TASK_NOT_TOP,
                )
            )
    }

    @Test
    fun withWorkBelowPersonalPictureInPicture_matched() = runTest {
        val result =
            policy.check(
                pictureInPictureApp(
                    pip = TaskSpec(taskId = 1002, name = YOUTUBE, userId = PERSONAL),
                    fullScreen = TaskSpec(taskId = 1003, name = FILES, userId = WORK),
                )
            )

        assertThat(result)
            .isEqualTo(
                PolicyResult.Matched(
                    policy = WorkProfilePolicy.NAME,
                    reason = WORK_TASK_IS_TOP,
                    CaptureParameters(
                        type = IsolatedTask(taskId = 1003, taskBounds = FULL_SCREEN),
                        component = ComponentName.unflattenFromString(FILES),
                        owner = UserHandle.of(WORK),
                    )
                )
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    fun withWorkFocusedInFreeForm_matched() = runTest {
        val result =
            policy.check(
                freeFormApps(
                    TaskSpec(taskId = 1002, name = YOUTUBE, userId = PERSONAL),
                    TaskSpec(taskId = 1003, name = FILES, userId = WORK),
                    focusedTaskId = 1003
                )
            )

        assertThat(result)
            .isEqualTo(
                PolicyResult.Matched(
                    policy = WorkProfilePolicy.NAME,
                    reason = WORK_TASK_IS_TOP,
                    CaptureParameters(
                        type = IsolatedTask(taskId = 1003, taskBounds = FREE_FORM),
                        component = ComponentName.unflattenFromString(FILES),
                        owner = UserHandle.of(WORK),
                    )
                )
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    fun withWorkFocusedInFreeForm_desktopModeEnabled_notMatched() = runTest {
        val result =
            policy.check(
                freeFormApps(
                    TaskSpec(taskId = 1002, name = YOUTUBE, userId = PERSONAL),
                    TaskSpec(taskId = 1003, name = FILES, userId = WORK),
                    focusedTaskId = 1003
                )
            )

        assertThat(result)
            .isEqualTo(
                NotMatched(
                    WorkProfilePolicy.NAME,
                    DESKTOP_MODE_ENABLED,
                )
            )
    }
}
