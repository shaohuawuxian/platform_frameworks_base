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
package com.android.systemui.keyguard.data

import com.android.systemui.keyguard.data.repository.FakeCommandQueueModule
import com.android.systemui.keyguard.data.repository.FakeKeyguardClockRepositoryModule
import com.android.systemui.keyguard.data.repository.FakeKeyguardRepositoryModule
import com.android.systemui.keyguard.data.repository.FakeKeyguardSurfaceBehindRepositoryModule
import com.android.systemui.keyguard.data.repository.FakeKeyguardTransitionRepositoryModule
import dagger.Module

@Module(
    includes =
        [
            FakeCommandQueueModule::class,
            FakeKeyguardRepositoryModule::class,
            FakeKeyguardTransitionRepositoryModule::class,
            FakeKeyguardSurfaceBehindRepositoryModule::class,
            FakeKeyguardClockRepositoryModule::class,
        ]
)
object FakeKeyguardDataLayerModule
