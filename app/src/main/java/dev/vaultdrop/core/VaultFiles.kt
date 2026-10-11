package dev.vaultdrop.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale

/**
 * Thin SAF layer built on document-id arithmetic rather than `DocumentFile`.
 *
 * `DocumentFile.findFile()` lists an entire directory to resolve one name, which
 * is what makes SAF feel slow. Because a tree grant lets us mint the document id
 * of any descendant directly, opening today's note is a single `openInputStream`
 * — the one filesystem operation Phase 1 is allowed.
 */
class VaultFiles(context: Context, val treeUri: Uri) {

    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val rootDocId: String = DocumentsContract.getTreeDocumentId(treeUri)

    fun uriFor(relativePath: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docIdFor(relativePath))

    private fun docIdFor(relativePath: String): String {
        val clean = relativePath.trim('/')
        if (clean.isEmpty()) return rootDocId
        return if (rootDocId.endsWith("/")) rootDocId + clean else "$rootDocId/$clean"
    }

    // ── Reading ──────────────────────────────────────────────────────────────

    /** @return the file's bytes, or null when it does not exist / cannot be read. */
    fun readBytes(relativePath: String): ByteArray? = readBytes(uriFor(relativePath))

    fun readBytes(uri: Uri): ByteArray? = try {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: FileNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun readText(relativePath: String): String? =
        readBytes(relativePath)?.toString(Charsets.UTF_8)

    fun exists(relativePath: String): Boolean = metadata(relativePath) != null

    data class Meta(val lastModified: Long, val mimeType: String?)

    fun metadata(relativePath: String): Meta? = try {
        resolver.query(
            uriFor(relativePath),
            arrayOf(
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                Meta(
                    lastModified = if (cursor.isNull(0)) 0L else cursor.getLong(0),
                    mimeType = cursor.getString(1),
                )
            } else null
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    // ── Writing ──────────────────────────────────────────────────────────────

    /**
     * Creates the file, making any missing parent directories first.
     *
     * Only ever called after a read or an exists check has come back empty, so
     * this cannot clobber an existing file. Returns null if the provider refuses.
     *
     * [mime] has to agree with the name's extension, or the provider appends one
     * of its own. Notes keep the Markdown default; an attachment passes
     * [mimeFor] its extension.
     */
    fun create(path: NotePath, mime: String = MIME_MARKDOWN): Uri? {
        val parent = ensureDirectory(path.dirs) ?: return null
        return try {
            // "text/markdown" matters: with "text/plain" the provider would
            // rewrite the extension to .txt and Obsidian would stop seeing it.
            DocumentsContract.createDocument(resolver, parent, mime, path.fileName)
        } catch (_: Exception) {
            // Deliberately wide, as in ensureDirectory. DocumentsContract rethrows
            // whatever the provider raised — IllegalStateException("Failed to touch")
            // from FileSystemProvider, UnsupportedOperationException from a provider
            // that will not create — and a refusal to create the note is an error
            // for openNote to report, not a reason to take the app down.
            null
        }
    }

    private fun ensureDirectory(dirs: List<String>): Uri? {
        var currentPath = ""
        var currentUri = uriFor("")
        for (dir in dirs) {
            val next = if (currentPath.isEmpty()) dir else "$currentPath/$dir"
            val nextUri = uriFor(next)
            currentUri = if (metadata(next) != null) {
                nextUri
            } else {
                try {
                    DocumentsContract.createDocument(
                        resolver, currentUri, DocumentsContract.Document.MIME_TYPE_DIR, dir,
                    ) ?: return null
                } catch (_: Exception) {
                    return null
                }
            }
            currentPath = next
        }
        return currentUri
    }

    /**
     * Removes the document. Only ever called on a note already copied into
     * `.trash/`, so this is the second half of a move, not a destruction.
     *
     * @return true when the provider says the document is gone.
     */
    fun delete(uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(resolver, uri)
    } catch (_: Exception) {
        // As wide as create's, and for the same reason: DocumentsContract
        // rethrows whatever the provider raised, and a refusal to delete is
        // something for the caller to report, not a reason to take the app down.
        false
    }

    /** Truncating write. "wt" matters — plain "w" leaves the tail of a longer previous body. */
    fun write(uri: Uri, text: String): Boolean = try {
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    } catch (_: Exception) {
        false
    }

    /**
     * [write] for a file that is not text: [source] streamed in a buffer at a
     * time, through the same truncating "wt". Does not close [source]; whoever
     * opened it does.
     */
    fun copyInto(uri: Uri, source: InputStream): Boolean = try {
        resolver.openOutputStream(uri, "wt")?.use { source.copyTo(it, COPY_BUFFER) } != null
    } catch (_: Exception) {
        false
    }

    /**
     * Whether [uri] is the document at [relativePath].
     *
     * A provider can hand back something other than what it was asked to create:
     * ` (1)` on a name that was taken in the meantime, or its own extension on a
     * name its MIME table disagrees with. A link written to the name we asked for
     * would then point at someone else's file, or at nothing.
     */
    fun isAt(uri: Uri, relativePath: String): Boolean =
        runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() == docIdFor(relativePath)

    // ── Reading what another app shared ──────────────────────────────────────

    /** What a shared file says about itself. Its provider may answer neither. */
    data class Shared(val displayName: String?, val mimeType: String?)

    /**
     * The shared file's `DISPLAY_NAME` and type, from the app that shared it.
     * Two round trips into that app's provider, so never on the launch path.
     */
    fun describeShared(uri: Uri): Shared {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return Shared(null, null)
        val name = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        val mime = try {
            resolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        return Shared(name, mime)
    }

    /**
     * Opens a shared file for reading, or null.
     *
     * Only `content:` URIs. A `file:` URI would be read with VaultDrop's own
     * permissions, so any app could share `file:///data/data/dev.vaultdrop/…`
     * and have VaultDrop copy its private files into the vault for the sync
     * client to carry off.
     */
    fun openShared(uri: Uri): InputStream? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return try {
            resolver.openInputStream(uri)
        } catch (_: Exception) {
            // The sender's grant lapsed, or its provider refused: a file that
            // cannot be copied, which the caller reports.
            null
        }
    }

    // ── Walking (Phase 2 only) ───────────────────────────────────────────────

    data class Child(val name: String, val mimeType: String?) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    /**
     * Two columns, because two is all the walk reads.
     *
     * The listing used to carry each file's document id, size and mtime as well,
     * so an incremental pass could tell whether a cached preview was still good.
     * Nothing caches note content any more, so the provider no longer has to stat
     * every file in the vault to answer a walk.
     */
    fun listChildren(relativePath: String): List<Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, docIdFor(relativePath),
        )
        return try {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0) ?: continue
                        add(Child(name = name, mimeType = cursor.getString(1)))
                    }
                }
            }.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    /**
     * Reads at most `buffer.size` bytes off the front of a document.
     *
     * [VaultSearch] reads the vault a note at a time and only ever wants enough of
     * each to search, so pulling a whole 40 KB file across the binder to look at
     * the first part of it is exactly the cost this avoids.
     *
     * The buffer belongs to the caller. A scan reads every note in the vault and
     * only ever has a handful of reads in flight, so it hands the same few
     * buffers round rather than leaving one per note behind for the collector.
     */
    fun readHead(uri: Uri, buffer: ByteArray): String? = try {
        resolver.openInputStream(uri)?.use { stream ->
            val limit = buffer.size
            var filled = 0
            while (filled < limit) {
                val read = stream.read(buffer, filled, limit - filled)
                if (read <= 0) break
                filled += read
            }
            // The cut can land mid-codepoint; drop the partial tail rather than
            // leaving a replacement character in a snippet.
            String(buffer, 0, filled, Charsets.UTF_8).trimEnd('\uFFFD')
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val MIME_MARKDOWN = "text/markdown"

        /**
         * The MIME type for a file ending in [ext], from `MimeTypeMap`: the table
         * `ExternalStorageProvider` checks a new name against, so the two agree
         * and the name is kept. An extension it does not know goes as
         * `application/octet-stream`, which the provider also leaves alone.
         */
        fun mimeFor(ext: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.ROOT)) ?: MIME_UNKNOWN

        private const val MIME_UNKNOWN = "application/octet-stream"

        /** [mimeFor] the other way round, for a shared file whose name has no extension. */
        fun extensionFor(mime: String): String? =
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.lowercase(Locale.ROOT))

        /** A 4 MB photo in 64 writes rather than the 512 the default buffer takes. */
        private const val COPY_BUFFER = 64 * 1024

        const val OBSIDIAN_DIR = ".obsidian"
        const val CONFIG_PATH = ".obsidian/daily-notes.json"
    }
}
