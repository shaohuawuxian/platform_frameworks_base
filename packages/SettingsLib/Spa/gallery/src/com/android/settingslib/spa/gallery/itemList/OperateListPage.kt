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

package com.android.settingslib.spa.gallery.itemList

import android.os.Bundle
import com.android.settingslib.spa.framework.common.SettingsEntry
import com.android.settingslib.spa.framework.common.SettingsEntryBuilder
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.common.createSettingsPage
import com.android.settingslib.spa.framework.compose.navigator
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel

private const val TITLE = "Operate List Main"

object OperateListPageProvider : SettingsPageProvider {
    override val name = "OpList"
    private val owner = createSettingsPage()

    override fun buildEntry(arguments: Bundle?): List<SettingsEntry> {
        return listOf(
            ItemListPageProvider.buildInjectEntry("opPiP")!!.setLink(fromPage = owner).build(),
            ItemListPageProvider.buildInjectEntry("opInstall")!!.setLink(fromPage = owner).build(),
            ItemListPageProvider.buildInjectEntry("opDnD")!!.setLink(fromPage = owner).build(),
            ItemListPageProvider.buildInjectEntry("opConnect")!!.setLink(fromPage = owner).build(),
        )
    }

    override fun getTitle(arguments: Bundle?): String {
        return TITLE
    }

    fun buildInjectEntry(): SettingsEntryBuilder {
        return SettingsEntryBuilder.createInject(owner = owner)
            .setUiLayoutFn {
                Preference(object : PreferenceModel {
                    override val title = TITLE
                    override val onClick = navigator(name)
                })
            }
    }
}
