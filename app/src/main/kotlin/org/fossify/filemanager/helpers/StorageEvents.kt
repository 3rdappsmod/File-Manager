package org.fossify.filemanager.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import androidx.core.content.ContextCompat

fun UsbDevice.isMassStorage(): Boolean = deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE ||
    (0 until interfaceCount).any { getInterface(it).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE }

fun String.isWithinStorage(root: String): Boolean {
    val normalized = root.trimEnd('/')
    return normalized.isNotEmpty() && (this == normalized || startsWith("$normalized/"))
}

/** Volume events, unlike USB attachment, indicate that a filesystem is ready or unavailable. */
class StorageEvents(
    private val context: Context,
    private val onMounted: (String) -> Unit,
    private val onRemoved: (String) -> Unit
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val path = intent.data?.path ?: return
            if (intent.action == Intent.ACTION_MEDIA_MOUNTED) onMounted(path) else onRemoved(path)
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun close() {
        context.unregisterReceiver(receiver)
    }
}
