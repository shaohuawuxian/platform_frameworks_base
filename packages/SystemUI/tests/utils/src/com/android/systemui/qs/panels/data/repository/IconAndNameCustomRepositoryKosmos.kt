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

package com.android.systemui.qs.panels.data.repository

import android.content.packageManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundCoroutineContext
import com.android.systemui.qs.pipeline.data.repository.installedTilesRepository
import com.android.systemui.settings.userTracker
import com.android.systemui.util.mockito.whenever

val Kosmos.iconAndNameCustomRepository by
    Kosmos.Fixture {
        whenever(userTracker.userContext.packageManager).thenReturn(packageManager)
        IconAndNameCustomRepository(
            installedTilesRepository,
            userTracker,
            backgroundCoroutineContext,
        )
    }
