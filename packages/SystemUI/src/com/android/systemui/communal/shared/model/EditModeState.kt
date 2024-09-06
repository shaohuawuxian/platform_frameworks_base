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

package com.android.systemui.communal.shared.model

/**
 * Models the state of the edit mode activity. Used to chain the animation during the transition
 * between the hub on communal scene, and the edit mode activity after unlocking the keyguard.
 */
enum class EditModeState(val value: Int) {
    // starting activity after dismissing keyguard
    STARTING(0),
    // activity content is showing
    SHOWING(1),
}
