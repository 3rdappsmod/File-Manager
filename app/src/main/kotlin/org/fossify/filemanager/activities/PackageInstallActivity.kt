package org.fossify.filemanager.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityPackageInstallBinding
import org.fossify.filemanager.helpers.packages.PackageInstallModel

class PackageInstallActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityPackageInstallBinding::inflate)
    private val model by lazy { ViewModelProvider(this)[PackageInstallModel::class.java] }
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) commit()
        else binding.installStatus.setText(R.string.app_install_permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.installContent))
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        model.state.observe(this) { state ->
            binding.installProgress.visibility = if (!state.ready && state.error == null) View.VISIBLE else View.GONE
            binding.installConfirm.isEnabled = state.ready
            binding.installStatus.text = when {
                state.error != null -> getString(R.string.app_install_failed, state.error)
                state.ready -> getString(R.string.app_install_ready)
                else -> getString(R.string.app_install_preparing)
            }
            if (!state.ready && state.error == null) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        binding.installConfirm.setOnClickListener { requestInstall() }
        model.prepare(uri)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.installAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.installContent)
    }

    private fun requestInstall() {
        if (packageManager.canRequestPackageInstalls()) {
            commit()
        } else {
            try {
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()))
            } catch (_: ActivityNotFoundException) {
                binding.installStatus.setText(R.string.app_install_permission)
            } catch (_: SecurityException) {
                binding.installStatus.setText(R.string.app_install_permission)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun commit() {
        binding.installConfirm.isEnabled = false
        try {
            model.commit()
            finish()
        } catch (exception: Exception) {
            binding.installStatus.text = getString(R.string.app_install_failed, exception.message.orEmpty())
            binding.installConfirm.isEnabled = true
        }
    }
}
