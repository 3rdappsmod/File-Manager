package org.fossify.filemanager.helpers

import java.io.IOException
import java.io.OutputStream

/** Do not acknowledge a save until buffered data and the underlying stream have closed successfully. */
fun writeEditedText(openOutput: () -> OutputStream?, text: String) {
    val output = openOutput() ?: throw IOException("Cannot open document for writing")
    output.use { stream ->
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
}
