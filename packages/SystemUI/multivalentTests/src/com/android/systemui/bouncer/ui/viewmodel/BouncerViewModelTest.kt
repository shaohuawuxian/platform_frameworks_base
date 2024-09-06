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

package com.android.systemui.bouncer.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.Back
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.SwipeDirection
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.None
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Password
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pattern
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pin
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Sim
import com.android.systemui.bouncer.domain.interactor.bouncerInteractor
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.kosmos.testScope
import com.android.systemui.scene.domain.interactor.sceneContainerStartable
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.fakeSceneDataSource
import com.android.systemui.testKosmos
import com.android.systemui.truth.containsEntriesExactly
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class BouncerViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    private lateinit var underTest: BouncerViewModel

    @Before
    fun setUp() {
        kosmos.sceneContainerStartable.start()
        underTest = kosmos.bouncerViewModel
    }

    @Test
    fun authMethod_nonNullForSecureMethods_nullForNotSecureMethods() =
        testScope.runTest {
            var authMethodViewModel: AuthMethodBouncerViewModel? = null

            authMethodsToTest().forEach { authMethod ->
                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                val job =
                    underTest.authMethodViewModel.onEach { authMethodViewModel = it }.launchIn(this)
                runCurrent()

                if (authMethod.isSecure) {
                    assertWithMessage("View-model unexpectedly null for auth method $authMethod")
                        .that(authMethodViewModel)
                        .isNotNull()
                } else {
                    assertWithMessage(
                            "View-model unexpectedly non-null for auth method $authMethod"
                        )
                        .that(authMethodViewModel)
                        .isNull()
                }

                job.cancel()
            }
        }

    @Test
    fun authMethodChanged_doesNotReuseInstances() =
        testScope.runTest {
            val seen = mutableMapOf<AuthenticationMethodModel, AuthMethodBouncerViewModel>()
            val authMethodViewModel: AuthMethodBouncerViewModel? by
                collectLastValue(underTest.authMethodViewModel)

            // First pass, populate our "seen" map:
            authMethodsToTest().forEach { authMethod ->
                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                authMethodViewModel?.let { seen[authMethod] = it }
            }

            // Second pass, assert same instances are not reused:
            authMethodsToTest().forEach { authMethod ->
                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                authMethodViewModel?.let {
                    assertThat(it.authenticationMethod).isEqualTo(authMethod)
                    assertThat(it).isNotSameInstanceAs(seen[authMethod])
                }
            }
        }

    @Test
    fun authMethodUnchanged_reusesInstances() =
        testScope.runTest {
            authMethodsToTest().forEach { authMethod ->
                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                val firstInstance: AuthMethodBouncerViewModel? =
                    collectLastValue(underTest.authMethodViewModel).invoke()

                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                val secondInstance: AuthMethodBouncerViewModel? =
                    collectLastValue(underTest.authMethodViewModel).invoke()

                firstInstance?.let { assertThat(it.authenticationMethod).isEqualTo(authMethod) }
                assertThat(secondInstance).isSameInstanceAs(firstInstance)
            }
        }

    @Test
    fun authMethodsToTest_returnsCompleteSampleOfAllAuthMethodTypes() {
        assertThat(authMethodsToTest().map { it::class }.toSet())
            .isEqualTo(AuthenticationMethodModel::class.sealedSubclasses.toSet())
    }

    @Test
    fun isInputEnabled() =
        testScope.runTest {
            val isInputEnabled by
                collectLastValue(
                    underTest.authMethodViewModel.flatMapLatest { authViewModel ->
                        authViewModel?.isInputEnabled ?: emptyFlow()
                    }
                )
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(isInputEnabled).isTrue()

            repeat(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT) {
                kosmos.bouncerInteractor.authenticate(WRONG_PIN)
            }
            assertThat(isInputEnabled).isFalse()

            val lockoutEndMs = kosmos.authenticationInteractor.lockoutEndTimestamp ?: 0
            advanceTimeBy(lockoutEndMs - testScope.currentTime)
            assertThat(isInputEnabled).isTrue()
        }

    @Test
    fun isSideBySideSupported() =
        testScope.runTest {
            val isSideBySideSupported by collectLastValue(underTest.isSideBySideSupported)
            kosmos.fakeFeatureFlagsClassic.set(Flags.FULL_SCREEN_USER_SWITCHER, true)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(isSideBySideSupported).isTrue()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            assertThat(isSideBySideSupported).isTrue()

            kosmos.fakeFeatureFlagsClassic.set(Flags.FULL_SCREEN_USER_SWITCHER, false)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(isSideBySideSupported).isTrue()

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            assertThat(isSideBySideSupported).isFalse()
        }

    @Test
    fun isFoldSplitRequired() =
        testScope.runTest {
            val isFoldSplitRequired by collectLastValue(underTest.isFoldSplitRequired)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(isFoldSplitRequired).isTrue()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            assertThat(isFoldSplitRequired).isFalse()

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            assertThat(isFoldSplitRequired).isTrue()
        }

    @Test
    fun destinationScenes() =
        testScope.runTest {
            val destinationScenes by collectLastValue(underTest.destinationScenes)
            kosmos.fakeSceneDataSource.changeScene(Scenes.QuickSettings)
            runCurrent()

            kosmos.fakeSceneDataSource.changeScene(Scenes.Bouncer)
            runCurrent()

            assertThat(destinationScenes)
                .containsEntriesExactly(
                    Back to UserActionResult(Scenes.QuickSettings),
                    Swipe(SwipeDirection.Down) to UserActionResult(Scenes.QuickSettings),
                )
        }

    private fun authMethodsToTest(): List<AuthenticationMethodModel> {
        return listOf(None, Pin, Password, Pattern, Sim)
    }

    companion object {
        private val WRONG_PIN = FakeAuthenticationRepository.DEFAULT_PIN.map { it + 1 }
    }
}
