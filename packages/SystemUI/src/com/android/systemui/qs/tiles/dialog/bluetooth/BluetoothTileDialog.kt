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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog

/** Dialog for showing active, connected and saved bluetooth devices. */
@SysUISingleton
internal class BluetoothTileDialog
constructor(
    private val deviceItem: List<DeviceItem>,
    private val deviceItemOnClickCallback: DeviceItemOnClickCallback,
    context: Context,
) : SystemUIDialog(context, DEFAULT_THEME, DEFAULT_DISMISS_ON_DEVICE_LOCK) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(LayoutInflater.from(context).inflate(R.layout.bluetooth_tile_dialog, null))

        setupDoneButton()
        setupRecyclerView()
    }

    private fun setupDoneButton() {
        requireViewById<View>(R.id.done_button).setOnClickListener { dismiss() }
    }

    private fun setupRecyclerView() {
        requireViewById<RecyclerView>(R.id.device_list).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = Adapter(deviceItem, deviceItemOnClickCallback)
        }
    }

    internal class Adapter(
        private val deviceItem: List<DeviceItem>,
        private val onClickCallback: DeviceItemOnClickCallback
    ) : RecyclerView.Adapter<Adapter.DeviceItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceItemViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.bluetooth_device_item, parent, false)
            return DeviceItemViewHolder(view)
        }

        override fun getItemCount() = deviceItem.size

        override fun onBindViewHolder(holder: DeviceItemViewHolder, position: Int) {
            val item = getItem(position)
            holder.bind(item, onClickCallback)
        }

        internal fun getItem(position: Int) = deviceItem[position]

        internal class DeviceItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val container = view.requireViewById<View>(R.id.bluetooth_device)
            private val nameView = view.requireViewById<TextView>(R.id.bluetooth_device_name)
            private val summaryView = view.requireViewById<TextView>(R.id.bluetooth_device_summary)
            private val iconView = view.requireViewById<ImageView>(R.id.bluetooth_device_icon)

            internal fun bind(item: DeviceItem, onClickCallback: DeviceItemOnClickCallback) {
                container.apply {
                    item.background?.let { background = context.getDrawable(it) }
                    setOnClickListener { onClickCallback.onClicked(item) }
                }
                nameView.text = item.deviceName
                summaryView.text = item.connectionSummary
                iconView.apply {
                    item.iconWithDescription?.let {
                        setImageDrawable(it.first)
                        contentDescription = it.second
                    }
                }
            }
        }
    }
}
