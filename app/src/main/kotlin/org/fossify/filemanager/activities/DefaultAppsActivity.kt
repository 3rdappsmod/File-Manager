package org.fossify.filemanager.activities

import android.os.Bundle
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityDefaultAppsBinding
import org.fossify.filemanager.databinding.ItemDefaultAppBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.helpers.DefaultAppChoice
import org.fossify.filemanager.helpers.DefaultAppFeature
import org.fossify.filemanager.helpers.DefaultAppTarget
import org.fossify.filemanager.helpers.DefaultAppTargets

class DefaultAppsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDefaultAppsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.defaultAppsScroll))
        setupMaterialScrollListener(binding.defaultAppsScroll, binding.defaultAppsAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.defaultAppsAppbar, NavigationIcon.Arrow)
        render()
    }

    private fun render() {
        binding.defaultAppsList.removeAllViews()
        DefaultAppFeature.entries.forEach { feature ->
            val row = ItemDefaultAppBinding.inflate(layoutInflater, binding.defaultAppsList, false)
            row.defaultAppLabel.setText(feature.title)
            row.defaultAppValue.text = DefaultAppTargets(this).label(config.defaultApps.get(feature))
            row.root.setOnClickListener { choose(feature) }
            binding.defaultAppsList.addView(row.root)
        }
        val reset = ItemDefaultAppBinding.inflate(layoutInflater, binding.defaultAppsList, false)
        reset.defaultAppLabel.setText(R.string.feature_default_reset_all)
        reset.defaultAppValue.setText(R.string.feature_default_description)
        reset.root.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.feature_default_reset_confirm)
                .setPositiveButton(R.string.feature_default_reset_all) { _, _ ->
                    config.defaultApps.reset()
                    render()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        binding.defaultAppsList.addView(reset.root)
        updateTextColors(binding.defaultAppsList)
    }

    private fun choose(feature: DefaultAppFeature) {
        // Discovery only: no file is created and no URI permission is granted from this screen.
        val uri = "content://$packageName.provider/default-app".toUri()
        ensureBackgroundThread {
            val finder = DefaultAppTargets(this)
            val targets = feature.discoveryMimeTypes.flatMap { mime -> finder.find(feature, uri, mime) }
                .distinctBy { DefaultAppChoice.key(it.id) }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) showChoices(feature, targets)
            }
        }
    }

    private fun showChoices(feature: DefaultAppFeature, targets: List<DefaultAppTarget>) {
        val labels = listOf(getString(R.string.feature_default_ask)) + targets.map { it.label }
        val saved = config.defaultApps.get(feature)
        var selected = targets.indexOfFirst { DefaultAppChoice.matches(saved, it.id) } + 1
        MaterialAlertDialogBuilder(this)
            .setTitle(feature.title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { _, index -> selected = index }
            .setPositiveButton(R.string.ok) { _, _ ->
                config.defaultApps.set(feature, if (selected == 0) "" else targets[selected - 1].id)
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
