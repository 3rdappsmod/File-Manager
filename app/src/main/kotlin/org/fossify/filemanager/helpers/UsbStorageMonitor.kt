package org.fossify.filemanager.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import org.fossify.commons.extensions.getStorageDirectories
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.R
import org.fossify.filemanager.extensions.config

class UsbStorageMonitor(
    private val context: Context,
    private val onRemoved: (String) -> Unit,
    private val onChanged: () -> Unit
) {
    private val volumes = StorageEvents(context, { path ->
        if (hasUsbStorage() && path != context.internalStoragePath && path != context.sdCardPath) {
            context.config.OTGPath = path.trimEnd('/')
            context.config.wasOTGHandled = true
            onChanged()
        }
    }, ::removePath)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                ?: return
            if (!device.isMassStorage()) return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> context.toast(R.string.usb_device_connected)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    context.toast(R.string.usb_device_disconnected)
                    if (!hasUsbStorage(device.deviceId)) removePath(context.config.OTGPath)
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        volumes.start()
    }

    fun close() {
        context.unregisterReceiver(receiver)
        volumes.close()
    }

    fun refreshPath() {
        ensureBackgroundThread {
            val path = if (hasUsbStorage()) {
                context.getStorageDirectories().map { it.trimEnd('/') }
                    .firstOrNull { it != context.internalStoragePath && it != context.sdCardPath }.orEmpty()
            } else {
                ""
            }
            context.config.OTGPath = path
            context.config.wasOTGHandled = path.isNotEmpty()
        }
    }

    private fun hasUsbStorage(excludedId: Int? = null): Boolean =
        context.getSystemService(UsbManager::class.java).deviceList.values.any {
            it.deviceId != excludedId && it.isMassStorage()
        }

    private fun removePath(path: String) {
        if (path.isEmpty()) return
        if (context.config.OTGPath.isWithinStorage(path)) {
            context.config.OTGPath = ""
            context.config.wasOTGHandled = false
        }
        onRemoved(path)
        onChanged()
    }
}
