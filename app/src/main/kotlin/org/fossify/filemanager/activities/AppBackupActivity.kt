package org.fossify.filemanager.activities

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.res.ColorStateList
import android.view.ViewGroup
import android.widget.TextView
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.filemanager.adapters.BackupAppsAdapter
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityAppBackupBinding
import org.fossify.filemanager.helpers.packages.AppBackupModel
import org.fossify.filemanager.helpers.packages.BackupApp
import org.fossify.filemanager.helpers.packages.BackupState

// Activity callbacks and selection state belong to the same screen.
@Suppress("TooManyFunctions")
class AppBackupActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAppBackupBinding::inflate)
    private val model by lazy { ViewModelProvider(this)[AppBackupModel::class.java] }
    private var shown = emptyList<BackupApp>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.backupContent))
        setupControls()
        model.apps.observe(this) { renderList() }
        model.state.observe(this) { renderState(it) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (model.state.value?.busy == true) {
                    MaterialAlertDialogBuilder(this@AppBackupActivity)
                        .setMessage(R.string.app_backup_cancel_confirm)
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }
                        .setNegativeButton(R.string.cancel, null).show()
                } else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.backupAppbar, NavigationIcon.Arrow, getProperBackgroundColor())
        binding.backupToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        applyTheme()
        renderList()
    }

    private fun applyTheme() {
        val text = getProperTextColor()
        val primary = getProperPrimaryColor()
        binding.root.setBackgroundColor(getProperBackgroundColor())
        updateTextColors(binding.backupContent)
        binding.backupSearch.setTextColor(text)
        binding.backupSearch.setHintTextColor(ColorStateList.valueOf(text).withAlpha(HINT_ALPHA))
        binding.backupSearch.backgroundTintList = ColorStateList.valueOf(primary)
        binding.backupSave.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(primary, android.graphics.Color.argb(DISABLED_ALPHA,
                android.graphics.Color.red(primary), android.graphics.Color.green(primary),
                android.graphics.Color.blue(primary)))
        )
        binding.backupSave.setTextColor(ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(primary.getContrastColor(), text)
        ))
        binding.backupProgress.indeterminateTintList = ColorStateList.valueOf(primary)
        for (index in 0 until binding.backupContent.childCount) {
            val view = binding.backupContent.getChildAt(index)
            if (view is TextView && view !== binding.backupSave) view.setTextColor(text)
        }
    }

    private fun colorCategory(view: View): View = view.apply {
        (this as? TextView)?.setTextColor(getProperTextColor())
        setBackgroundColor(getProperBackgroundColor())
    }

    private fun setupControls() {
        binding.backupCategory.adapter = object : ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.app_backup_user), getString(R.string.app_backup_system))) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                colorCategory(super.getView(position, convertView, parent))
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                colorCategory(super.getDropDownView(position, convertView, parent))
        }
        binding.backupCategory.setSelection(if (model.system) 1 else 0)
        binding.backupCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (model.system != (position == 1)) {
                    model.system = position == 1
                    model.selected.clear()
                    renderList()
                }
            }
        }
        binding.backupSearch.setText(model.query)
        binding.backupSearch.doAfterTextChanged {
            model.query = it.toString()
            renderList()
        }
        binding.backupList.setOnItemClickListener { _, _, position, _ ->
            val name = shown[position].packageName
            if (!model.selected.add(name)) model.selected.remove(name)
            (binding.backupList.adapter as? BackupAppsAdapter)?.notifyDataSetChanged()
            renderState(model.state.value ?: BackupState())
        }
        binding.backupSave.setOnClickListener {
            handleStoragePermission { granted -> if (granted) model.backup() }
        }
    }

    private fun renderList() {
        shown = model.apps.value.orEmpty().filter {
            it.system == model.system &&
                (it.label.contains(model.query, true) || it.packageName.contains(model.query, true))
        }
        binding.backupList.adapter = BackupAppsAdapter(this, shown, model.selected)
        renderState(model.state.value ?: BackupState())
    }

    private fun renderState(state: BackupState) {
        binding.backupDestination.setText(R.string.app_backup_destination)
        binding.backupSave.text = getString(R.string.app_backup_save_count, model.selected.size)
        binding.backupSave.isEnabled = !state.busy && model.selected.isNotEmpty()
        listOf(binding.backupCategory, binding.backupSearch,
            binding.backupList).forEach { it.isEnabled = !state.busy }
        binding.backupProgress.visibility = if (state.busy) View.VISIBLE else View.GONE
        if (state.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.backupStatus.text = when {
            state.busy -> getString(R.string.app_backup_progress, state.completed, state.total, state.current)
            state.total > 0 -> getString(R.string.app_backup_result, state.completed, state.errors.size)
            state.errors.isNotEmpty() -> state.errors.joinToString("\n")
            else -> getString(R.string.app_backup_app_count, shown.size)
        }
        binding.backupStatus.setOnClickListener {
            if (state.errors.isNotEmpty()) MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_backup_errors).setMessage(state.errors.joinToString("\n\n"))
                .setPositiveButton(R.string.ok, null).show()
        }
    }

    companion object {
        private const val HINT_ALPHA = 170
        private const val DISABLED_ALPHA = 100
    }
}
