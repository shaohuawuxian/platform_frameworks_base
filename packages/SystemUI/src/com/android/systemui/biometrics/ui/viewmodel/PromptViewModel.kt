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

package com.android.systemui.biometrics.ui.viewmodel

import android.app.ActivityTaskManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.biometrics.BiometricFingerprintConstants
import android.hardware.biometrics.BiometricPrompt
import android.hardware.biometrics.Flags.customBiometricPrompt
import android.hardware.biometrics.PromptContentView
import android.os.UserHandle
import android.util.Log
import android.util.RotationUtils
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import com.android.launcher3.icons.IconProvider
import com.android.systemui.Flags.bpTalkback
import com.android.systemui.Flags.constraintBp
import com.android.systemui.biometrics.UdfpsUtils
import com.android.systemui.biometrics.Utils
import com.android.systemui.biometrics.Utils.isSystem
import com.android.systemui.biometrics.domain.interactor.BiometricStatusInteractor
import com.android.systemui.biometrics.domain.interactor.DisplayStateInteractor
import com.android.systemui.biometrics.domain.interactor.PromptSelectorInteractor
import com.android.systemui.biometrics.domain.interactor.UdfpsOverlayInteractor
import com.android.systemui.biometrics.domain.model.BiometricPromptRequest
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.biometrics.shared.model.BiometricModality
import com.android.systemui.biometrics.shared.model.DisplayRotation
import com.android.systemui.biometrics.shared.model.PromptKind
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.shared.model.AcquiredFingerprintAuthenticationStatus
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ViewModel for BiometricPrompt. */
class PromptViewModel
@Inject
constructor(
    displayStateInteractor: DisplayStateInteractor,
    private val promptSelectorInteractor: PromptSelectorInteractor,
    @Application private val context: Context,
    private val udfpsOverlayInteractor: UdfpsOverlayInteractor,
    private val biometricStatusInteractor: BiometricStatusInteractor,
    private val udfpsUtils: UdfpsUtils,
    private val iconProvider: IconProvider,
    private val activityTaskManager: ActivityTaskManager,
) {
    /** The set of modalities available for this prompt */
    val modalities: Flow<BiometricModalities> =
        promptSelectorInteractor.prompt
            .map { it?.modalities ?: BiometricModalities() }
            .distinctUntilChanged()

    /** Layout params for fingerprint iconView */
    val fingerprintIconWidth: Int =
        context.resources.getDimensionPixelSize(R.dimen.biometric_dialog_fingerprint_icon_width)
    val fingerprintIconHeight: Int =
        context.resources.getDimensionPixelSize(R.dimen.biometric_dialog_fingerprint_icon_height)

    /** Layout params for face iconView */
    val faceIconWidth: Int =
        context.resources.getDimensionPixelSize(R.dimen.biometric_dialog_face_icon_size)
    val faceIconHeight: Int =
        context.resources.getDimensionPixelSize(R.dimen.biometric_dialog_face_icon_size)

    /** Padding for placing icons */
    val portraitSmallBottomPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_portrait_small_bottom_padding
        )
    val portraitMediumBottomPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_portrait_medium_bottom_padding
        )
    val portraitLargeScreenBottomPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_portrait_large_screen_bottom_padding
        )
    val landscapeSmallBottomPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_landscape_small_bottom_padding
        )
    val landscapeSmallHorizontalPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_landscape_small_horizontal_padding
        )
    val landscapeMediumBottomPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_landscape_medium_bottom_padding
        )
    val landscapeMediumHorizontalPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_landscape_medium_horizontal_padding
        )

    private val udfpsSensorBounds: Flow<Rect> =
        combine(
                udfpsOverlayInteractor.udfpsOverlayParams,
                displayStateInteractor.currentRotation
            ) { params, rotation ->
                val rotatedBounds = Rect(params.sensorBounds)
                RotationUtils.rotateBounds(
                    rotatedBounds,
                    params.naturalDisplayWidth,
                    params.naturalDisplayHeight,
                    rotation.ordinal
                )
                Rect(
                    rotatedBounds.left,
                    rotatedBounds.top,
                    params.logicalDisplayWidth - rotatedBounds.right,
                    params.logicalDisplayHeight - rotatedBounds.bottom
                )
            }
            .distinctUntilChanged()

    val legacyFingerprintSensorWidth: Flow<Int> =
        combine(modalities, udfpsOverlayInteractor.udfpsOverlayParams) { modalities, overlayParams
            ->
            if (modalities.hasUdfps) {
                overlayParams.sensorBounds.width()
            } else {
                fingerprintIconWidth
            }
        }

    val legacyFingerprintSensorHeight: Flow<Int> =
        combine(modalities, udfpsOverlayInteractor.udfpsOverlayParams) { modalities, overlayParams
            ->
            if (modalities.hasUdfps) {
                overlayParams.sensorBounds.height()
            } else {
                fingerprintIconHeight
            }
        }

    val fingerprintSensorWidth: Int =
        udfpsOverlayInteractor.udfpsOverlayParams.value.sensorBounds.width()

    val fingerprintSensorHeight: Int =
        udfpsOverlayInteractor.udfpsOverlayParams.value.sensorBounds.height()

    private val _accessibilityHint = MutableSharedFlow<String>()

    /** Hint for talkback directional guidance */
    val accessibilityHint: Flow<String> = _accessibilityHint.asSharedFlow()

    private val _isAuthenticating: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** If the user is currently authenticating (i.e. at least one biometric is scanning). */
    val isAuthenticating: Flow<Boolean> = _isAuthenticating.asStateFlow()

    private val _isAuthenticated: MutableStateFlow<PromptAuthState> =
        MutableStateFlow(PromptAuthState(false))

    /** If the user has successfully authenticated and confirmed (when explicitly required). */
    val isAuthenticated: Flow<PromptAuthState> = _isAuthenticated.asStateFlow()

    /** If the auth is pending confirmation. */
    val isPendingConfirmation: Flow<Boolean> =
        isAuthenticated.map { authState ->
            authState.isAuthenticated && authState.needsUserConfirmation
        }

    private val _isOverlayTouched: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** The kind of credential the user has. */
    val credentialKind: Flow<PromptKind> = promptSelectorInteractor.credentialKind

    /** The kind of prompt to use (biometric, pin, pattern, etc.). */
    val promptKind: StateFlow<PromptKind> = promptSelectorInteractor.promptKind

    /** Whether the sensor icon on biometric prompt ui should be hidden. */
    val hideSensorIcon: Flow<Boolean> = modalities.map { it.isEmpty }.distinctUntilChanged()

    /** The label to use for the cancel button. */
    val negativeButtonText: Flow<String> =
        promptSelectorInteractor.prompt.map { it?.negativeButtonText ?: "" }

    private val _message: MutableStateFlow<PromptMessage> = MutableStateFlow(PromptMessage.Empty)

    /** A message to show the user, if there is an error, hint, or help to show. */
    val message: Flow<PromptMessage> = _message.asStateFlow()

    /** Whether an error message is currently being shown. */
    val showingError: Flow<Boolean> = message.map { it.isError }.distinctUntilChanged()

    private val isRetrySupported: Flow<Boolean> = modalities.map { it.hasFace }

    private val _fingerprintStartMode = MutableStateFlow(FingerprintStartMode.Pending)

    /** Fingerprint sensor state. */
    val fingerprintStartMode: Flow<FingerprintStartMode> = _fingerprintStartMode.asStateFlow()

    /** Whether a finger has been acquired by the sensor */
    // TODO(b/331948073): Add support for detecting SFPS finger without authentication running
    val hasFingerBeenAcquired: Flow<Boolean> =
        combine(biometricStatusInteractor.fingerprintAcquiredStatus, modalities) {
                status,
                modalities ->
                modalities.hasSfps &&
                    status is AcquiredFingerprintAuthenticationStatus &&
                    status.acquiredInfo == BiometricFingerprintConstants.FINGERPRINT_ACQUIRED_START
            }
            .distinctUntilChanged()

    /** Whether there is currently a finger on the sensor */
    val hasFingerOnSensor: Flow<Boolean> =
        combine(hasFingerBeenAcquired, _isOverlayTouched) { hasFingerBeenAcquired, overlayTouched ->
            hasFingerBeenAcquired || overlayTouched
        }

    private val _forceLargeSize = MutableStateFlow(false)
    private val _forceMediumSize = MutableStateFlow(false)

    private val _hapticsToPlay =
        MutableStateFlow(HapticsToPlay(HapticFeedbackConstants.NO_HAPTICS, /* flag= */ null))

    /** Event fired to the view indicating a [HapticsToPlay] */
    val hapticsToPlay = _hapticsToPlay.asStateFlow()

    /** The current position of the prompt */
    val position: Flow<PromptPosition> =
        combine(
                _forceLargeSize,
                displayStateInteractor.isLargeScreen,
                displayStateInteractor.currentRotation,
            ) { forceLarge, isLargeScreen, rotation ->
                when {
                    forceLarge ||
                        isLargeScreen ||
                        promptKind.value.isOnePaneNoSensorLandscapeBiometric() ->
                        PromptPosition.Bottom
                    rotation == DisplayRotation.ROTATION_90 -> PromptPosition.Right
                    rotation == DisplayRotation.ROTATION_270 -> PromptPosition.Left
                    rotation == DisplayRotation.ROTATION_180 -> PromptPosition.Top
                    else -> PromptPosition.Bottom
                }
            }
            .distinctUntilChanged()

    /** The size of the prompt. */
    val size: Flow<PromptSize> =
        combine(
                _forceLargeSize,
                _forceMediumSize,
                modalities,
                promptSelectorInteractor.isConfirmationRequired,
                fingerprintStartMode,
            ) { forceLarge, forceMedium, modalities, confirmationRequired, fpStartMode ->
                when {
                    forceLarge -> PromptSize.LARGE
                    forceMedium -> PromptSize.MEDIUM
                    modalities.hasFaceOnly && !confirmationRequired -> PromptSize.SMALL
                    modalities.hasFaceAndFingerprint &&
                        !confirmationRequired &&
                        fpStartMode == FingerprintStartMode.Pending -> PromptSize.SMALL
                    else -> PromptSize.MEDIUM
                }
            }
            .distinctUntilChanged()

    /** Prompt panel size padding */
    private val smallHorizontalGuidelinePadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_land_small_horizontal_guideline_padding
        )
    private val udfpsHorizontalGuidelinePadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_two_pane_udfps_horizontal_guideline_padding
        )
    private val mediumTopGuidelinePadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_one_pane_medium_top_guideline_padding
        )
    private val mediumHorizontalGuidelinePadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_two_pane_medium_horizontal_guideline_padding
        )

    /** Rect for positioning biometric icon */
    val iconPosition: Flow<Rect> =
        combine(udfpsSensorBounds, size, position, modalities) {
                sensorBounds,
                size,
                position,
                modalities ->
                when (position) {
                    PromptPosition.Bottom ->
                        if (size.isSmall) {
                            Rect(0, 0, 0, portraitSmallBottomPadding)
                        } else if (size.isMedium && modalities.hasUdfps) {
                            Rect(0, 0, 0, sensorBounds.bottom)
                        } else if (size.isMedium) {
                            Rect(0, 0, 0, portraitMediumBottomPadding)
                        } else {
                            // Large screen
                            Rect(0, 0, 0, portraitLargeScreenBottomPadding)
                        }
                    PromptPosition.Right ->
                        if (size.isSmall || modalities.hasFaceOnly) {
                            Rect(0, 0, landscapeSmallHorizontalPadding, landscapeSmallBottomPadding)
                        } else if (size.isMedium && modalities.hasUdfps) {
                            Rect(0, 0, sensorBounds.right, sensorBounds.bottom)
                        } else {
                            // SFPS
                            Rect(
                                0,
                                0,
                                landscapeMediumHorizontalPadding,
                                landscapeMediumBottomPadding
                            )
                        }
                    PromptPosition.Left ->
                        if (size.isSmall || modalities.hasFaceOnly) {
                            Rect(landscapeSmallHorizontalPadding, 0, 0, landscapeSmallBottomPadding)
                        } else if (size.isMedium && modalities.hasUdfps) {
                            Rect(sensorBounds.left, 0, 0, sensorBounds.bottom)
                        } else {
                            // SFPS
                            Rect(
                                landscapeMediumHorizontalPadding,
                                0,
                                0,
                                landscapeMediumBottomPadding
                            )
                        }
                    PromptPosition.Top -> Rect()
                }
            }
            .distinctUntilChanged()

    /**
     * If the API caller or the user's personal preferences require explicit confirmation after
     * successful authentication. Confirmation always required when in explicit flow.
     */
    val isConfirmationRequired: Flow<Boolean> =
        combine(_isOverlayTouched, size) { isOverlayTouched, size ->
            !isOverlayTouched && size.isNotSmall
        }

    /**
     * When fingerprint and face modalities are enrolled, indicates whether only face auth has
     * started.
     *
     * True when fingerprint and face modalities are enrolled and implicit flow is active. This
     * occurs in co-ex auth when confirmation is not required and only face auth is started, then
     * becomes false when device transitions to explicit flow after a first error, when the
     * fingerprint sensor is started.
     *
     * False when the dialog opens in explicit flow (fingerprint and face modalities enrolled but
     * confirmation is required), or if user has only fingerprint enrolled, or only face enrolled.
     */
    val faceMode: Flow<Boolean> =
        combine(modalities, isConfirmationRequired, fingerprintStartMode) {
                modalities,
                isConfirmationRequired,
                fingerprintStartMode ->
                modalities.hasFaceAndFingerprint &&
                    !isConfirmationRequired &&
                    fingerprintStartMode == FingerprintStartMode.Pending
            }
            .distinctUntilChanged()

    val iconViewModel: PromptIconViewModel =
        PromptIconViewModel(
            this,
            displayStateInteractor,
            promptSelectorInteractor,
            udfpsOverlayInteractor
        )

    private val _isIconViewLoaded = MutableStateFlow(false)

    /**
     * For prompts with an iconView, false until the prompt's iconView animation has been loaded in
     * the view, otherwise true by default. Used for BiometricViewSizeBinder to wait for the icon
     * asset to be loaded before determining the prompt size.
     */
    val isIconViewLoaded: Flow<Boolean> =
        combine(hideSensorIcon, _isIconViewLoaded.asStateFlow()) { hideSensorIcon, isIconViewLoaded
                ->
                hideSensorIcon || isIconViewLoaded
            }
            .distinctUntilChanged()

    // Sets whether the prompt's iconView animation has been loaded in the view yet.
    fun setIsIconViewLoaded(iconViewLoaded: Boolean) {
        _isIconViewLoaded.value = iconViewLoaded
    }

    /** The size of the biometric icon */
    val iconSize: Flow<Pair<Int, Int>> =
        combine(iconViewModel.activeAuthType, modalities) { activeAuthType, modalities ->
            if (activeAuthType == PromptIconViewModel.AuthType.Face) {
                Pair(faceIconWidth, faceIconHeight)
            } else {
                if (modalities.hasUdfps) {
                    Pair(fingerprintSensorWidth, fingerprintSensorHeight)
                } else {
                    Pair(fingerprintIconWidth, fingerprintIconHeight)
                }
            }
        }

    /**
     * Rect for positioning prompt guidelines (left, top, right, unused)
     *
     * Negative values are used to signify that guideline measuring should be flipped, measuring
     * from opposite side of the screen
     */
    val guidelineBounds: Flow<Rect> =
        combine(iconPosition, promptKind, size, position, modalities) {
                _,
                promptKind,
                size,
                position,
                modalities ->
                when (position) {
                    PromptPosition.Bottom ->
                        if (promptKind.isOnePaneNoSensorLandscapeBiometric()) {
                            Rect(0, 0, 0, 0)
                        } else {
                            Rect(0, mediumTopGuidelinePadding, 0, 0)
                        }
                    PromptPosition.Right ->
                        if (size.isSmall) {
                            Rect(-smallHorizontalGuidelinePadding, 0, 0, 0)
                        } else if (modalities.hasUdfps) {
                            Rect(udfpsHorizontalGuidelinePadding, 0, 0, 0)
                        } else {
                            Rect(-mediumHorizontalGuidelinePadding, 0, 0, 0)
                        }
                    PromptPosition.Left ->
                        if (size.isSmall) {
                            Rect(0, 0, -smallHorizontalGuidelinePadding, 0)
                        } else if (modalities.hasUdfps) {
                            Rect(0, 0, udfpsHorizontalGuidelinePadding, 0)
                        } else {
                            Rect(0, 0, -mediumHorizontalGuidelinePadding, 0)
                        }
                    PromptPosition.Top -> Rect()
                }
            }
            .distinctUntilChanged()

    /** Padding for prompt UI elements */
    val promptPadding: Flow<Rect> =
        combine(size, displayStateInteractor.currentRotation) { size, rotation ->
            if (size != PromptSize.LARGE) {
                val navBarInsets = Utils.getNavbarInsets(context)
                if (rotation == DisplayRotation.ROTATION_90) {
                    Rect(0, 0, navBarInsets.right, 0)
                } else if (rotation == DisplayRotation.ROTATION_270) {
                    Rect(navBarInsets.left, 0, 0, 0)
                } else {
                    Rect(0, 0, 0, navBarInsets.bottom)
                }
            } else {
                Rect(0, 0, 0, 0)
            }
        }

    /** Logo for the prompt. */
    val logo: Flow<Drawable?> =
        promptSelectorInteractor.prompt
            .map {
                when {
                    !(customBiometricPrompt() && constraintBp()) || it == null -> null
                    it.logoBitmap != null -> BitmapDrawable(context.resources, it.logoBitmap)
                    else -> context.getUserBadgedIcon(it, iconProvider, activityTaskManager)
                }
            }
            .distinctUntilChanged()

    /** Logo description for the prompt. */
    val logoDescription: Flow<String> =
        promptSelectorInteractor.prompt
            .map {
                when {
                    !(customBiometricPrompt() && constraintBp()) || it == null -> ""
                    !it.logoDescription.isNullOrEmpty() -> it.logoDescription
                    else -> context.getUserBadgedLabel(it, activityTaskManager)
                }
            }
            .distinctUntilChanged()

    /** Title for the prompt. */
    val title: Flow<String> =
        promptSelectorInteractor.prompt.map { it?.title ?: "" }.distinctUntilChanged()

    /** Subtitle for the prompt. */
    val subtitle: Flow<String> =
        promptSelectorInteractor.prompt.map { it?.subtitle ?: "" }.distinctUntilChanged()

    /** Custom content view for the prompt. */
    val contentView: Flow<PromptContentView?> =
        promptSelectorInteractor.prompt
            .map { if (customBiometricPrompt() && constraintBp()) it?.contentView else null }
            .distinctUntilChanged()

    private val originalDescription =
        promptSelectorInteractor.prompt.map { it?.description ?: "" }.distinctUntilChanged()
    /**
     * Description for the prompt. Description view and contentView is mutually exclusive. Pass
     * description down only when contentView is null.
     */
    val description: Flow<String> =
        combine(contentView, originalDescription) { contentView, description ->
            if (contentView == null) description else ""
        }

    /** If the indicator (help, error) message should be shown. */
    val isIndicatorMessageVisible: Flow<Boolean> =
        combine(
            size,
            position,
            message,
        ) { size, _, message ->
            size.isMedium && message.message.isNotBlank()
        }

    /** If the auth is pending confirmation and the confirm button should be shown. */
    val isConfirmButtonVisible: Flow<Boolean> =
        combine(
            size,
            position,
            isPendingConfirmation,
        ) { size, _, isPendingConfirmation ->
            size.isNotSmall && isPendingConfirmation
        }

    /** If the icon can be used as a confirmation button. */
    val isIconConfirmButton: Flow<Boolean> = size.map { it.isNotSmall }.distinctUntilChanged()

    /** If the negative button should be shown. */
    val isNegativeButtonVisible: Flow<Boolean> =
        combine(
            size,
            position,
            isAuthenticated,
            promptSelectorInteractor.isCredentialAllowed,
        ) { size, _, authState, credentialAllowed ->
            size.isNotSmall && authState.isNotAuthenticated && !credentialAllowed
        }

    /** If the cancel button should be shown (. */
    val isCancelButtonVisible: Flow<Boolean> =
        combine(
            size,
            position,
            isAuthenticated,
            isNegativeButtonVisible,
            isConfirmButtonVisible,
        ) { size, _, authState, showNegativeButton, showConfirmButton ->
            size.isNotSmall && authState.isAuthenticated && !showNegativeButton && showConfirmButton
        }

    private val _canTryAgainNow = MutableStateFlow(false)
    /**
     * If authentication can be manually restarted via the try again button or touching a
     * fingerprint sensor.
     */
    val canTryAgainNow: Flow<Boolean> =
        combine(
            _canTryAgainNow,
            size,
            position,
            isAuthenticated,
            isRetrySupported,
        ) { readyToTryAgain, size, _, authState, supportsRetry ->
            readyToTryAgain && size.isNotSmall && supportsRetry && authState.isNotAuthenticated
        }

    /** If the try again button show be shown (only the button, see [canTryAgainNow]). */
    val isTryAgainButtonVisible: Flow<Boolean> =
        combine(
            canTryAgainNow,
            modalities,
        ) { tryAgainIsPossible, modalities ->
            tryAgainIsPossible && modalities.hasFaceOnly
        }

    /** If the credential fallback button show be shown. */
    val isCredentialButtonVisible: Flow<Boolean> =
        combine(
            size,
            position,
            isAuthenticated,
            promptSelectorInteractor.isCredentialAllowed,
        ) { size, _, authState, credentialAllowed ->
            size.isMedium && authState.isNotAuthenticated && credentialAllowed
        }

    private val history = PromptHistoryImpl()
    private var messageJob: Job? = null

    /**
     * Show a temporary error [message] associated with an optional [failedModality] and play
     * [hapticFeedback].
     *
     * The [messageAfterError] will be shown via [showAuthenticating] when [authenticateAfterError]
     * is set (or via [showHelp] when not set) after the error is dismissed.
     *
     * The error is ignored if the user has already authenticated or if [suppressIf] is true given
     * the currently showing [PromptMessage] and [PromptHistory].
     */
    suspend fun showTemporaryError(
        message: String,
        messageAfterError: String,
        authenticateAfterError: Boolean,
        suppressIf: (PromptMessage, PromptHistory) -> Boolean = { _, _ -> false },
        hapticFeedback: Boolean = true,
        failedModality: BiometricModality = BiometricModality.None,
    ) = coroutineScope {
        if (_isAuthenticated.value.isAuthenticated) {
            return@coroutineScope
        }

        _canTryAgainNow.value = supportsRetry(failedModality)

        val suppress = suppressIf(_message.value, history)
        history.failure(failedModality)
        if (suppress) {
            return@coroutineScope
        }

        _isAuthenticating.value = false
        _isAuthenticated.value = PromptAuthState(false)
        _forceMediumSize.value = true
        _message.value = PromptMessage.Error(message)

        if (hapticFeedback) {
            vibrateOnError()
        }

        messageJob?.cancel()
        messageJob = launch {
            delay(BiometricPrompt.HIDE_DIALOG_DELAY.toLong())
            if (authenticateAfterError) {
                showAuthenticating(messageAfterError)
            } else {
                showHelp(messageAfterError)
            }
        }
    }

    /**
     * Call to ensure the fingerprint sensor has started. Either when the dialog is first shown
     * (most cases) or when it should be enabled after a first error (coex implicit flow).
     */
    fun ensureFingerprintHasStarted(isDelayed: Boolean) {
        if (_fingerprintStartMode.value == FingerprintStartMode.Pending) {
            _fingerprintStartMode.value =
                if (isDelayed) FingerprintStartMode.Delayed else FingerprintStartMode.Normal
        }
    }

    // enable retry only when face fails (fingerprint runs constantly)
    private fun supportsRetry(failedModality: BiometricModality) =
        failedModality == BiometricModality.Face

    /**
     * Show a persistent help message.
     *
     * Will be show even if the user has already authenticated.
     */
    suspend fun showHelp(message: String) {
        val alreadyAuthenticated = _isAuthenticated.value.isAuthenticated
        if (!alreadyAuthenticated) {
            _isAuthenticating.value = false
            _isAuthenticated.value = PromptAuthState(false)
        }

        _message.value =
            if (message.isNotBlank()) PromptMessage.Help(message) else PromptMessage.Empty
        _forceMediumSize.value = true

        messageJob?.cancel()
        messageJob = null
    }

    /**
     * Show a temporary help message and transition back to a fixed message.
     *
     * Ignored if the user has already authenticated.
     */
    suspend fun showTemporaryHelp(
        message: String,
        messageAfterHelp: String = "",
    ) = coroutineScope {
        if (_isAuthenticated.value.isAuthenticated) {
            return@coroutineScope
        }

        _isAuthenticating.value = false
        _isAuthenticated.value = PromptAuthState(false)
        _message.value =
            if (message.isNotBlank()) PromptMessage.Help(message) else PromptMessage.Empty
        _forceMediumSize.value = true

        messageJob?.cancel()
        messageJob = launch {
            delay(BiometricPrompt.HIDE_DIALOG_DELAY.toLong())
            showAuthenticating(messageAfterHelp)
        }
    }

    /** Show the user that biometrics are actively running and set [isAuthenticating]. */
    fun showAuthenticating(message: String = "", isRetry: Boolean = false) {
        if (_isAuthenticated.value.isAuthenticated) {
            // TODO(jbolinger): convert to go/tex-apc?
            Log.w(TAG, "Cannot show authenticating after authenticated")
            return
        }

        _isAuthenticating.value = true
        _isAuthenticated.value = PromptAuthState(false)
        _message.value = if (message.isBlank()) PromptMessage.Empty else PromptMessage.Help(message)

        // reset the try again button(s) after the user attempts a retry
        if (isRetry) {
            _canTryAgainNow.value = false
        }

        messageJob?.cancel()
        messageJob = null
    }

    /**
     * Show successfully authentication, set [isAuthenticated], and dismiss the prompt after a
     * [dismissAfterDelay] or prompt for explicit confirmation (if required).
     */
    suspend fun showAuthenticated(
        modality: BiometricModality,
        dismissAfterDelay: Long,
        helpMessage: String = "",
    ) {
        if (_isAuthenticated.value.isAuthenticated) {
            // TODO(jbolinger): convert to go/tex-apc?
            Log.w(TAG, "Cannot show authenticated after authenticated")
            return
        }

        _isAuthenticating.value = false
        val needsUserConfirmation = needsExplicitConfirmation(modality)
        _isAuthenticated.value =
            PromptAuthState(true, modality, needsUserConfirmation, dismissAfterDelay)
        _message.value = PromptMessage.Empty

        if (!needsUserConfirmation) {
            vibrateOnSuccess()
        }

        messageJob?.cancel()
        messageJob = null

        if (helpMessage.isNotBlank()) {
            showHelp(helpMessage)
        }
    }

    private suspend fun needsExplicitConfirmation(modality: BiometricModality): Boolean {
        val confirmationRequired = isConfirmationRequired.first()

        // Only worry about confirmationRequired if face was used to unlock
        if (modality == BiometricModality.Face) {
            return confirmationRequired
        }
        // fingerprint only never requires confirmation
        return false
    }

    /**
     * Set the prompt's auth state to authenticated and confirmed.
     *
     * This should only be used after [showAuthenticated] when the operation requires explicit user
     * confirmation.
     */
    fun confirmAuthenticated() {
        val authState = _isAuthenticated.value
        if (authState.isNotAuthenticated) {
            Log.w(TAG, "Cannot confirm authenticated when not authenticated")
            return
        }

        _isAuthenticated.value = authState.asExplicitlyConfirmed()
        _message.value = PromptMessage.Empty

        vibrateOnSuccess()

        messageJob?.cancel()
        messageJob = null
    }

    /**
     * Touch event occurred on the overlay
     *
     * Tracks whether a finger is currently down to set [_isOverlayTouched] to be used as user
     * confirmation
     */
    fun onOverlayTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            _isOverlayTouched.value = true

            if (_isAuthenticated.value.needsUserConfirmation) {
                confirmAuthenticated()
            }
            return true
        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
            _isOverlayTouched.value = false
        }
        return false
    }

    /** Sets the message used for UDFPS directional guidance */
    suspend fun onAnnounceAccessibilityHint(
        event: MotionEvent,
        touchExplorationEnabled: Boolean,
    ): Boolean {
        if (bpTalkback() && modalities.first().hasUdfps && touchExplorationEnabled) {
            // TODO(b/315184924): Remove uses of UdfpsUtils
            val scaledTouch =
                udfpsUtils.getTouchInNativeCoordinates(
                    event.getPointerId(0),
                    event,
                    udfpsOverlayInteractor.udfpsOverlayParams.value
                )
            if (
                !udfpsUtils.isWithinSensorArea(
                    event.getPointerId(0),
                    event,
                    udfpsOverlayInteractor.udfpsOverlayParams.value
                )
            ) {
                _accessibilityHint.emit(
                    udfpsUtils.onTouchOutsideOfSensorArea(
                        touchExplorationEnabled,
                        context,
                        scaledTouch.x,
                        scaledTouch.y,
                        udfpsOverlayInteractor.udfpsOverlayParams.value
                    )
                )
            }
        }
        return false
    }

    /**
     * Switch to the credential view.
     *
     * TODO(b/251476085): this should be decoupled from the shared panel controller
     */
    fun onSwitchToCredential() {
        _forceLargeSize.value = true
        promptSelectorInteractor.onSwitchToCredential()
    }

    private fun vibrateOnSuccess() {
        _hapticsToPlay.value =
            HapticsToPlay(
                HapticFeedbackConstants.CONFIRM,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
    }

    private fun vibrateOnError() {
        _hapticsToPlay.value =
            HapticsToPlay(
                HapticFeedbackConstants.REJECT,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
    }

    /** Clears the [hapticsToPlay] variable by setting its constant to the NO_HAPTICS default. */
    fun clearHaptics() {
        _hapticsToPlay.update { previous ->
            HapticsToPlay(HapticFeedbackConstants.NO_HAPTICS, previous.flag)
        }
    }

    companion object {
        const val TAG = "PromptViewModel"
    }
}

private fun Context.getUserBadgedIcon(
    prompt: BiometricPromptRequest.Biometric,
    iconProvider: IconProvider,
    activityTaskManager: ActivityTaskManager
): Drawable? {
    var icon: Drawable? = null
    val componentName = prompt.getComponentNameForLogo(activityTaskManager)
    if (componentName != null && shouldShowLogoWithOverrides(componentName)) {
        val activityInfo = getActivityInfo(componentName)
        icon = if (activityInfo == null) null else iconProvider.getIcon(activityInfo)
    }
    if (icon == null) {
        val appInfo = prompt.getApplicationInfoForLogo(this, componentName)
        if (appInfo == null) {
            Log.w(PromptViewModel.TAG, "Cannot find app logo for package $opPackageName")
            return null
        } else {
            icon = packageManager.getApplicationIcon(appInfo)
        }
    }
    return packageManager.getUserBadgedIcon(icon, UserHandle.of(prompt.userInfo.userId))
}

private fun Context.getUserBadgedLabel(
    prompt: BiometricPromptRequest.Biometric,
    activityTaskManager: ActivityTaskManager
): String {
    val componentName = prompt.getComponentNameForLogo(activityTaskManager)
    val appInfo = prompt.getApplicationInfoForLogo(this, componentName)
    return if (appInfo == null || packageManager.getApplicationLabel(appInfo).isNullOrEmpty()) {
        Log.w(PromptViewModel.TAG, "Cannot find app logo for package $opPackageName")
        ""
    } else {
        packageManager
            .getUserBadgedLabel(packageManager.getApplicationLabel(appInfo), UserHandle.of(userId))
            .toString()
    }
}

private fun BiometricPromptRequest.Biometric.getComponentNameForLogo(
    activityTaskManager: ActivityTaskManager
): ComponentName? {
    val topActivity: ComponentName? = activityTaskManager.getTasks(1).firstOrNull()?.topActivity
    return when {
        componentNameForConfirmDeviceCredentialActivity != null ->
            componentNameForConfirmDeviceCredentialActivity
        topActivity?.packageName.contentEquals(opPackageName) -> topActivity
        else -> {
            Log.w(PromptViewModel.TAG, "Top activity $topActivity is not the client $opPackageName")
            null
        }
    }
}

private fun BiometricPromptRequest.Biometric.getApplicationInfoForLogo(
    context: Context,
    componentNameForLogo: ComponentName?
): ApplicationInfo? {
    val packageName =
        when {
            componentNameForLogo != null -> componentNameForLogo.packageName
            // TODO(b/339532378): We should check whether |allowBackgroundAuthentication| should be
            // removed.
            // This is being consistent with the check in [AuthController.showDialog()].
            allowBackgroundAuthentication || isSystem(context, opPackageName) -> opPackageName
            else -> null
        }
    return if (packageName == null) {
        Log.w(PromptViewModel.TAG, "Cannot find application info for $opPackageName")
        null
    } else {
        context.getApplicationInfo(packageName)
    }
}

private fun Context.shouldShowLogoWithOverrides(componentName: ComponentName): Boolean {
    return resources
        .getStringArray(R.array.biometric_dialog_package_names_for_logo_with_overrides)
        .find { componentName.packageName.contentEquals(it) } != null
}

private fun Context.getActivityInfo(componentName: ComponentName): ActivityInfo? =
    try {
        packageManager.getActivityInfo(componentName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(PromptViewModel.TAG, "Cannot find activity info for $opPackageName", e)
        null
    }

private fun Context.getApplicationInfo(packageName: String): ApplicationInfo? =
    try {
        packageManager.getApplicationInfo(
            packageName,
            PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_ANY_USER
        )
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(PromptViewModel.TAG, "Cannot find application info for $opPackageName", e)
        null
    }

/** How the fingerprint sensor was started for the prompt. */
enum class FingerprintStartMode {
    /** Fingerprint sensor has not started. */
    Pending,

    /** Fingerprint sensor started immediately when prompt was displayed. */
    Normal,

    /** Fingerprint sensor started after the first failure of another passive modality. */
    Delayed;

    /** If this is [Normal] or [Delayed]. */
    val isStarted: Boolean
        get() = this == Normal || this == Delayed
}

/**
 * The state of haptic feedback to play. It is composed by a [HapticFeedbackConstants] and a
 * [HapticFeedbackConstants] flag.
 */
data class HapticsToPlay(val hapticFeedbackConstant: Int, val flag: Int?)
