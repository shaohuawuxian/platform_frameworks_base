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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.systemui.keyguard.ui.viewmodel

import android.content.applicationContext
import com.android.systemui.biometrics.domain.interactor.biometricStatusInteractor
import com.android.systemui.biometrics.domain.interactor.displayStateInteractor
import com.android.systemui.biometrics.domain.interactor.sideFpsSensorInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceEntryFingerprintAuthInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.statusbar.phone.dozeServiceHost
import kotlinx.coroutines.ExperimentalCoroutinesApi

val Kosmos.sideFpsProgressBarViewModel by
    Kosmos.Fixture {
        SideFpsProgressBarViewModel(
            context = applicationContext,
            biometricStatusInteractor = biometricStatusInteractor,
            deviceEntryFingerprintAuthInteractor = deviceEntryFingerprintAuthInteractor,
            sfpsSensorInteractor = sideFpsSensorInteractor,
            dozeServiceHost = dozeServiceHost,
            keyguardInteractor = keyguardInteractor,
            displayStateInteractor = displayStateInteractor,
            mainDispatcher = testDispatcher,
            applicationScope = testScope.backgroundScope,
            powerInteractor = powerInteractor
        )
    }
