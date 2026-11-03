package org.fossify.filemanager.adapters

import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ItemBackupAppBinding
import org.fossify.filemanager.helpers.packages.BackupApp

class BackupAppsAdapter(
    private val owner: LifecycleOwner,
    private val apps: List<BackupApp>,
    private val selected: Set<String>
) : BaseAdapter() {
    private val icons = LruCache<String, Drawable>(ICON_CACHE_SIZE)
    private class Holder(val binding: ItemBackupAppBinding, var job: Job? = null)

    override fun getCount() = apps.size
    override fun getItem(position: Int) = apps[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder = convertView?.tag as? Holder ?: Holder(
            ItemBackupAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        ).also { it.binding.root.tag = it }
        val app = getItem(position)
        val binding = holder.binding
        val context = parent.context
        val text = context.getProperTextColor()
        binding.backupAppName.text = app.label
        binding.backupAppDetails.text = context.getString(R.string.app_backup_details, app.version, app.packageName)
        binding.backupAppName.setTextColor(text)
        binding.backupAppDetails.setTextColor(text)
        binding.backupAppDetails.alpha = SECONDARY_ALPHA
        binding.backupAppChecked.isChecked = app.packageName in selected
        binding.backupAppChecked.buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(context.getProperPrimaryColor(), text)
        )
        ViewCompat.setAccessibilityDelegate(binding.root, object : AccessibilityDelegateCompat() {
            @Suppress("DEPRECATION") // Boolean checked state remains compatible with Android 8+.
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isCheckable = true
                info.isChecked = app.packageName in selected
            }
        })
        holder.job?.cancel()
        binding.backupAppIcon.setImageDrawable(icons.get(app.packageName))
        if (icons.get(app.packageName) == null) {
            val manager = context.applicationContext.packageManager
            holder.job = owner.lifecycleScope.launch {
                val icon = withContext(Dispatchers.IO) {
                    try {
                        manager.getApplicationIcon(app.packageName)
                    } catch (_: PackageManager.NameNotFoundException) {
                        manager.defaultActivityIcon
                    }
                }
                icons.put(app.packageName, icon)
                binding.backupAppIcon.setImageDrawable(icon)
            }
        }
        return binding.root
    }

    companion object {
        private const val ICON_CACHE_SIZE = 48
        private const val SECONDARY_ALPHA = 0.75f
    }
}
