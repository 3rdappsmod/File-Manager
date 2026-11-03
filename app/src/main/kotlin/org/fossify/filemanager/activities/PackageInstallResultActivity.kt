package org.fossify.filemanager.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.view.View
import androidx.core.content.IntentCompat
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityPackageInstallBinding

/** Only PackageInstaller's explicit PendingIntent can open this non-exported callback activity. */
class PackageInstallResultActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityPackageInstallBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.installContent))
        binding.installProgress.visibility = View.GONE
        binding.installConfirm.isEnabled = true
        binding.installConfirm.setText(R.string.ok)
        binding.installConfirm.setOnClickListener { finish() }
        handleResult()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.installAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.installContent)
    }

    private fun handleResult() {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> confirm()
            PackageInstaller.STATUS_SUCCESS -> binding.installStatus.setText(R.string.app_install_success)
            PackageInstaller.STATUS_FAILURE_ABORTED -> binding.installStatus.setText(R.string.app_install_cancelled)
            else -> binding.installStatus.text = getString(R.string.app_install_failed,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
        }
    }

    private fun confirm() {
        val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        if (confirmation == null) {
            binding.installStatus.setText(R.string.app_install_confirmation_failed)
            return
        }
        try {
            startActivity(confirmation)
            finish()
        } catch (_: ActivityNotFoundException) {
            binding.installStatus.setText(R.string.app_install_confirmation_failed)
        } catch (_: SecurityException) {
            binding.installStatus.setText(R.string.app_install_confirmation_failed)
        }
    }
}
