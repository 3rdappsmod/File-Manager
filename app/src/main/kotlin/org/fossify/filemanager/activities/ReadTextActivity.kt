package org.fossify.filemanager.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyEditText
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityReadTextBinding
import org.fossify.filemanager.dialogs.SaveAsDialog
import org.fossify.filemanager.extensions.openPath
import java.io.File
import java.io.IOException
import org.fossify.filemanager.helpers.writeEditedText
import org.fossify.filemanager.helpers.TextEditorState
import org.fossify.filemanager.helpers.EditorSearch
import java.io.OutputStream

class ReadTextActivity : SimpleActivity() {
    companion object {
        private const val SELECT_SAVE_FILE_INTENT = 1
        private const val SELECT_SAVE_FILE_AND_EXIT_INTENT = 2
        private const val KEY_UNSAVED_TEXT = "KEY_UNSAVED_TEXT"
        private const val KEY_PENDING_SAVE_URI = "pending_save_uri"
        private const val KEY_PENDING_SAVE_EXIT = "pending_save_exit"
    }

    private val binding by viewBinding(ActivityReadTextBinding::inflate)

    private var filePath = ""
    private val document = TextEditorState()
    private var pendingDraft: String? = null
    private var pendingSaveUri: Uri? = null
    private var pendingSaveExit = false
    private var sourceUri: Uri? = null
    private var isSaving = false
    private val editorSearch = EditorSearch()
    private var savePrompt: AlertDialog? = null
    private var isSearchActive = false

    private lateinit var searchQueryET: MyEditText
    private lateinit var searchPrevBtn: ImageView
    private lateinit var searchNextBtn: ImageView
    private lateinit var searchClearBtn: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        pendingDraft = savedInstanceState?.getString(KEY_UNSAVED_TEXT)
        pendingSaveUri = savedInstanceState?.getString(KEY_PENDING_SAVE_URI)?.toUri()
        pendingSaveExit = savedInstanceState?.getBoolean(KEY_PENDING_SAVE_EXIT) == true
        binding.readTextView.isEnabled = false
        setupOptionsMenu()
        binding.apply {
            setupEdgeToEdge(padBottomImeAndSystem = listOf(readTextView))
            setupMaterialScrollListener(binding.readTextHolder, binding.readTextAppbar)
        }

        searchQueryET = findViewById(R.id.search_query)
        searchPrevBtn = findViewById(R.id.search_previous)
        searchNextBtn = findViewById(R.id.search_next)
        searchClearBtn = findViewById(R.id.search_clear)

        if (checkAppSideloading()) {
            return
        }

        val uri = if (intent.extras?.containsKey(REAL_FILE_PATH) == true) {
            Uri.fromFile(File(intent.extras?.getString(REAL_FILE_PATH).toString()))
        } else {
            intent.data
        }

        if (uri == null) {
            finish()
            return
        }

        sourceUri = uri
        val filename = getFilenameFromUri(uri)
        if (filename.isNotEmpty()) {
            binding.readTextToolbar.title = Uri.decode(filename)
        }

        binding.readTextView.onGlobalLayout {
            ensureBackgroundThread {
                checkIntent(uri)
            }
        }

        setupSearchButtons()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.readTextAppbar, NavigationIcon.Arrow)
        // Commons installs a finish() listener; route the toolbar through the same guard as system Back.
        binding.readTextToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingSaveUri?.let { outState.putString(KEY_PENDING_SAVE_URI, it.toString()) }
        outState.putBoolean(KEY_PENDING_SAVE_EXIT, pendingSaveExit)
        val text = binding.readTextView.text.toString()
        if (!document.isReady) {
            pendingDraft?.let { outState.putString(KEY_UNSAVED_TEXT, it) }
        } else if (document.hasChanges(text)) {
            outState.putString(KEY_UNSAVED_TEXT, text)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode != SELECT_SAVE_FILE_INTENT && requestCode != SELECT_SAVE_FILE_AND_EXIT_INTENT) return
        if (resultCode != Activity.RESULT_OK) return
        pendingSaveUri = resultData?.data ?: return
        pendingSaveExit = requestCode == SELECT_SAVE_FILE_AND_EXIT_INTENT
        completePendingSave()
    }

    private fun completePendingSave() {
        if (!document.isReady) return
        val destination = pendingSaveUri ?: return
        val shouldExit = pendingSaveExit
        pendingSaveUri = null
        // Compare the returned destination, not the original intent's URI.
        val overwritesOriginal = destination == sourceUri ||
            (filePath.isNotEmpty() && getRealPathFromURI(destination) == filePath)
        saveTextContent({ contentResolver.openOutputStream(destination, "wt") }, shouldExit, overwritesOriginal)
    }

    override fun onBackPressedCompat(): Boolean {
        if (isSaving || (!document.isReady && pendingDraft != null)) return true
        val hasUnsavedChanges = document.hasChanges(binding.readTextView.text.toString())
        return when {
            isSearchActive -> {
                closeSearch()
                true
            }
            hasUnsavedChanges -> {
                showSavePrompt()
                true
            }

            else -> false
        }
    }

    private fun showSavePrompt() {
        if (savePrompt?.isShowing == true) return
        savePrompt = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.save_before_closing)
            .setPositiveButton(R.string.save) { _, _ -> saveText(true) }
            .setNegativeButton(R.string.discard) { _, _ -> performDefaultBack() }
            .setNeutralButton(R.string.cancel, null)
            .create().also { dialog ->
                dialog.setOnDismissListener { savePrompt = null }
                dialog.show()
            }
    }

    private fun setupOptionsMenu() {
        binding.readTextToolbar.setOnMenuItemClickListener { menuItem ->
            if (isSaving || !document.isReady) return@setOnMenuItemClickListener true
            when (menuItem.itemId) {
                R.id.menu_search -> openSearch()
                R.id.menu_save -> saveText()
                R.id.menu_save_as -> saveAsText()
                R.id.menu_open_with -> openPath(intent.dataString!!, true)
                R.id.menu_print -> printText()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun openSearch() {
        isSearchActive = true
        binding.searchWrapper.beVisible()
        showKeyboard(searchQueryET)

        binding.readTextView.requestFocus()
        binding.readTextView.setSelection(0)

        searchQueryET.postDelayed({
            searchQueryET.requestFocus()
        }, 250)
    }

    private fun updateFilePath() {
        if (filePath.isEmpty()) {
            filePath = sourceUri?.let { getRealPathFromURI(it) }.orEmpty()
        }
    }

    private fun saveAsText(shouldExitAfterSaving: Boolean = false) {
        updateFilePath()

        if (filePath.isEmpty()) {
            SaveAsDialog(this, filePath, true) { _, filename ->
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, filename)
                    addCategory(Intent.CATEGORY_OPENABLE)

                    val requestCode = if (shouldExitAfterSaving) {
                        SELECT_SAVE_FILE_AND_EXIT_INTENT
                    } else {
                        SELECT_SAVE_FILE_INTENT
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(this, requestCode)
                }
            }
        } else {
            SaveAsDialog(this, filePath, false) { path, _ ->
                if (hasStoragePermission()) {
                    val file = File(path)
                    getFileOutputStream(file.toFileDirItem(this), true) { output ->
                        val shouldOverwriteOriginalText = path == filePath
                        saveTextContent({ output }, shouldExitAfterSaving, shouldOverwriteOriginalText)
                    }
                } else {
                    toast(R.string.no_storage_permissions)
                }
            }
        }
    }

    private fun saveText(shouldExitAfterSaving: Boolean = false) {
        if (!document.isReady) return
        if (!document.canOverwriteOriginal) {
            saveAsText(shouldExitAfterSaving)
            return
        }
        val uri = sourceUri
        if (uri?.scheme == "content") {
            // A content URI is the authoritative location; cloud/SAF documents need no filesystem path.
            saveTextContent({ contentResolver.openOutputStream(uri, "wt") }, shouldExitAfterSaving, true) {
                saveAsText(shouldExitAfterSaving)
            }
            return
        }
        updateFilePath()

        if (filePath.isEmpty()) {
            saveAsText(shouldExitAfterSaving)
        } else if (hasStoragePermission()) {
            val file = File(filePath)
            getFileOutputStream(file.toFileDirItem(this), true) { output ->
                saveTextContent({ output }, shouldExitAfterSaving, true)
            }
        } else {
            toast(R.string.no_storage_permissions)
        }
    }

    private fun saveTextContent(
        openOutput: () -> OutputStream?,
        shouldExitAfterSaving: Boolean,
        shouldOverwriteOriginalText: Boolean,
        onAccessDenied: (() -> Unit)? = null
    ) {
        val currentText = binding.readTextView.text.toString()
        isSaving = true
        ensureBackgroundThread {
            val failure = try {
                writeEditedText(openOutput, currentText)
                null
            } catch (error: IOException) {
                error
            } catch (error: SecurityException) {
                error
            } catch (error: IllegalArgumentException) {
                error
            }
            runOnUiThread {
                isSaving = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (failure != null) {
                    showErrorToast(failure)
                    if (failure is SecurityException) onAccessDenied?.invoke()
                } else {
                    toast(R.string.file_saved)
                    if (shouldOverwriteOriginalText) document.saved(currentText)
                    // Edits made while saving remain dirty and must not be lost on exit.
                    if (shouldExitAfterSaving && binding.readTextView.text.toString() == currentText) {
                        hideKeyboard()
                        performDefaultBack()
                    }
                }
            }
        }
    }

    private fun printText() {
        try {
            val webView = WebView(this)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

                override fun onPageFinished(view: WebView, url: String) {
                    createWebPrintJob(view)
                }
            }

            val text = binding.readTextView.text.toString()
            ensureBackgroundThread {
                try {
                    val base64 = Base64.encodeToString(text.toByteArray(), Base64.DEFAULT)
                    runOnUiThread {
                        webView.loadData(base64, "text/plain", "base64")
                    }
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun createWebPrintJob(webView: WebView) {
        val jobName = if (filePath.isNotEmpty()) {
            filePath.getFilenameFromPath()
        } else {
            getString(R.string.app_name)
        }

        val printAdapter = webView.createPrintDocumentAdapter(jobName)

        (getSystemService(Context.PRINT_SERVICE) as? PrintManager)?.apply {
            print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }

    private fun checkIntent(uri: Uri) {
        try {
            val text = if (uri.scheme == "file") {
                File(requireNotNull(uri.path)).readText()
            } else {
                val input = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open document")
                input.bufferedReader().use { it.readText() }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (uri.scheme == "file") filePath = uri.path.orEmpty()
                document.loaded(text)
                displayLoadedText(pendingDraft ?: text)
            }
        } catch (error: OutOfMemoryError) {
            onDocumentReadFailed(error.toString())
        } catch (error: IOException) {
            onDocumentReadFailed(error.toString())
        } catch (error: SecurityException) {
            onDocumentReadFailed(error.toString())
        } catch (error: IllegalArgumentException) {
            onDocumentReadFailed(error.toString())
        }
    }

    private fun onDocumentReadFailed(message: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            showErrorToast(message)
            val draft = pendingDraft
            if (draft == null) {
                finish()
            } else {
                // Preserve restored edits even if the original was removed or its grant expired.
                document.recoverDraft()
                displayLoadedText(draft)
            }
        }
    }

    private fun displayLoadedText(text: String) {
        pendingDraft = null
        binding.readTextView.setText(text)
        binding.readTextView.isEnabled = true
        if (text.isNotEmpty()) hideKeyboard() else showKeyboard(binding.readTextView)
        // A picker result can arrive before the document is restored after activity recreation.
        completePendingSave()
    }

    private fun setupSearchButtons() {
        searchQueryET.onTextChangeListener {
            searchTextChanged(it)
        }

        searchPrevBtn.setOnClickListener {
            goToPrevSearchResult()
        }

        searchNextBtn.setOnClickListener {
            goToNextSearchResult()
        }

        searchClearBtn.setOnClickListener {
            closeSearch()
        }

        searchQueryET.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchNextBtn.performClick()
                return@OnEditorActionListener true
            }

            false
        })

        binding.searchWrapper.setBackgroundColor(getProperPrimaryColor())
        val contrastColor = getProperPrimaryColor().getContrastColor()
        arrayListOf(searchPrevBtn, searchNextBtn, searchClearBtn).forEach {
            it.applyColorFilter(contrastColor)
        }
    }

    private fun searchTextChanged(text: String) {
        binding.readTextView.text?.clearBackgroundSpans()

        if (text.isNotBlank() && text.length > 1) {
            binding.readTextView.highlightText(text, getProperPrimaryColor())
        }

        selectSearchMatch()

        searchQueryET.postDelayed({
            searchQueryET.requestFocus()
        }, 50)
    }

    private fun goToPrevSearchResult() = selectSearchMatch(-1)

    private fun goToNextSearchResult() = selectSearchMatch(1)

    private fun closeSearch() {
        searchQueryET.text?.clear()
        isSearchActive = false
        binding.searchWrapper.beGone()
        hideKeyboard()
    }

    private fun selectSearchMatch(direction: Int = 0) {
        val editText = binding.readTextView
        val position = editorSearch.select(editText.text.toString(), searchQueryET.text.toString(), direction)
        if (position != null) {
            editText.requestFocus()
            editText.setSelection(position)
        }
    }
}
