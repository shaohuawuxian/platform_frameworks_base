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

package com.android.server.inputmethod.multisessiontest;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class ConcurrentMultiUserTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Before
    public void doBeforeEachTest() {
        // No op
    }

    @Test
    public void behaviorBeingTested_expectedResult() {
        // Sample test
        Context context =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThat(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)).isTrue();
        assertThat(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS)).isTrue();
    }
}
