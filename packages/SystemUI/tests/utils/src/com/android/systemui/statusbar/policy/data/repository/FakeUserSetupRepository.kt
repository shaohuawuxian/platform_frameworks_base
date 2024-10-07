/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.statusbar.policy.data.repository

import com.android.systemui.dagger.SysUISingleton
import dagger.Binds
import dagger.Module
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/** Defaults to `true` */
@SysUISingleton
class FakeUserSetupRepository @Inject constructor() : UserSetupRepository {
    private val _isUserSetup: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override val isUserSetUp = _isUserSetup

    fun setUserSetUp(isSetUp: Boolean) {
        _isUserSetup.value = isSetUp
    }
}

@Module
interface FakeUserSetupRepositoryModule {
    @Binds fun bindFake(fake: FakeUserSetupRepository): UserSetupRepository
}
