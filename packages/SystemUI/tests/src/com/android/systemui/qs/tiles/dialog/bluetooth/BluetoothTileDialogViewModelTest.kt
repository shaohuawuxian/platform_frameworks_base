/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.qs.tiles.dialog.bluetooth

import android.os.Handler
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.widget.LinearLayout
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.DialogLaunchAnimator
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.nullable
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@SmallTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class BluetoothTileDialogViewModelTest : SysuiTestCase() {

    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
    private val fakeSystemClock = FakeSystemClock()
    private val backgroundExecutor = FakeExecutor(fakeSystemClock)

    private lateinit var bluetoothTileDialogViewModel: BluetoothTileDialogViewModel

    @Mock private lateinit var deviceItemInteractor: DeviceItemInteractor

    @Mock private lateinit var dialogLaunchAnimator: DialogLaunchAnimator

    private lateinit var testableLooper: TestableLooper

    @Before
    fun setUp() {
        testableLooper = TestableLooper.get(this)
        `when`(deviceItemInteractor.getDeviceItems(any())).thenReturn(emptyList())
        bluetoothTileDialogViewModel =
            BluetoothTileDialogViewModel(
                deviceItemInteractor,
                dialogLaunchAnimator,
                Handler(testableLooper.looper)
            )
    }

    @After
    fun tearDown() {
        testableLooper.processAllMessages()
    }

    @Test
    fun testShowDialog_noAnimation() {
        bluetoothTileDialogViewModel.showDialog(context, null)
        testableLooper.processAllMessages()

        assertThat(bluetoothTileDialogViewModel.dialog).isNotNull()
        verify(dialogLaunchAnimator, never()).showFromView(any(), any(), any(), any())
        assertThat(bluetoothTileDialogViewModel.dialog?.isShowing).isTrue()
    }

    @Test
    fun testShowDialog_animated() {
        bluetoothTileDialogViewModel.showDialog(mContext, LinearLayout(mContext))
        testableLooper.processAllMessages()

        assertThat(bluetoothTileDialogViewModel.dialog).isNotNull()
        verify(dialogLaunchAnimator).showFromView(any(), any(), nullable(), anyBoolean())
    }

    @Test
    fun testShowDialog_animated_callInBackgroundThread() {
        backgroundExecutor.execute {
            bluetoothTileDialogViewModel.showDialog(mContext, LinearLayout(mContext))
            testableLooper.processAllMessages()

            assertThat(bluetoothTileDialogViewModel.dialog).isNotNull()
            verify(dialogLaunchAnimator).showFromView(any(), any(), nullable(), anyBoolean())
        }
    }
}
