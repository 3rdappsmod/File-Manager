package org.fossify.filemanager.helpers

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.model.LocalFileHeader

/** Consume each entry synchronously and close the archive before reporting success to the caller. */
fun forEachZipEntry(
    openInput: () -> InputStream?,
    password: CharArray? = null,
    consume: (ZipInputStream, LocalFileHeader) -> Unit
) {
    val input = openInput() ?: throw IOException("Cannot open archive")
    input.use { source ->
        ZipInputStream(BufferedInputStream(source)).use { zip ->
            password?.let { zip.setPassword(it) }
            while (true) {
                val entry = zip.nextEntry ?: break
                consume(zip, entry)
            }
        }
    }
}
