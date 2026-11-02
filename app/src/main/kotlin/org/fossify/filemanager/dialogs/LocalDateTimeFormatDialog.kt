package org.fossify.filemanager.dialogs

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.DialogLocalDateTimeFormatBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.helpers.LocalDateFormats

class LocalDateTimeFormatDialog(activity: Activity) {
    init {
        val binding = DialogLocalDateTimeFormatBinding.inflate(activity.layoutInflater)
        val config = activity.config
        binding.use24Hour.isChecked = config.use24HourFormat
        val patterns = LocalDateFormats.patterns
        var selected = patterns.indexOf(LocalDateFormats.normalize(config.dateFormat))
        val now = Date()
        val labels = patterns.map { SimpleDateFormat(it, Locale.KOREA).format(now) }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.change_date_and_time_format)
            .setSingleChoiceItems(labels, selected) { _, index -> selected = index }
            .setView(binding.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                config.dateFormat = patterns[selected]
                config.use24HourFormat = binding.use24Hour.isChecked
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
