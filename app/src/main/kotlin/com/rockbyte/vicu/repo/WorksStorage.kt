package com.rockbyte.vicu.repo

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject

/** A completed metadata file is the publication boundary; media paths never change. */
internal class WorksStorage(
    private val context: Context,
    private val currentTimeMillis: () -> Long,
    private val directory: () -> File? = {
        context.getExternalFilesDir(null)?.let { File(it, "works") }
    },
) : WorksStore {
    override val changes = MutableSharedFlow<Unit>(replay = 1)
    private var prepared = false
    private val pending = mutableMapOf<Uri, Pair<File, MediaKind>>()

    @Synchronized
    private fun root(): File {
        val root = directory() ?: throw IOException("External storage unavailable")
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create works directory")
        if (!prepared) {
            // Only at process initialization, before any new output can be created.
            val directories = root.listFiles() ?: throw IOException("Cannot list works directory")
            directories.filter { it.isDirectory }.forEach { folder ->
                val metadata = AtomicFile(File(folder, "completed.json"))
                val complete = try { metadata.openRead().use { true } } catch (_: IOException) { false }
                if (!complete && !folder.deleteRecursively()) throw IOException("Cannot clean unfinished work")
            }
            prepared = true
        }
        return root
    }

    @Synchronized
    override fun create(inputName: String, suffix: String, extension: String, kind: MediaKind): Uri {
        require(extension.length <= 16 && extension.matches(Regex("[a-zA-Z0-9]+")))
        require(suffix.length <= 32 && suffix.matches(Regex("[a-zA-Z0-9_-]*")))
        val stem = inputName.substringBeforeLast('.', inputName)
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "_").trim('_').take(60)
            .ifBlank { kind.name.lowercase() }
        val folder = File(root(), UUID.randomUUID().toString())
        if (!folder.mkdir()) throw IOException("Cannot create work")
        try {
            val file = File(folder, "${stem}${suffix}_${currentTimeMillis()}.$extension")
            if (!file.createNewFile()) throw IOException("Cannot create output")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.works", file)
            pending[uri] = file to kind
            return uri
        } catch (error: Exception) {
            folder.deleteRecursively()
            throw error
        }
    }

    @Synchronized
    override fun publish(uri: Uri) {
        val (file, kind) = checkNotNull(pending[uri]) { "Unknown pending output" }
        if (file.length() == 0L) throw IOException("Empty output")
        val metadata = JSONObject().apply {
            put("name", file.name)
            put("kind", kind.name)
            put("dateAdded", currentTimeMillis() / 1000)
            put("durationMs", if (kind == MediaKind.IMAGE) 0L else duration(uri))
        }
        val bytes = metadata.toString().toByteArray(Charsets.UTF_8)
        val atomic = AtomicFile(File(file.parentFile, "completed.json"))
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            atomic.finishWrite(output)
            // AtomicFile logs rename failures rather than throwing them.
            if (!atomic.baseFile.isFile || atomic.baseFile.length() != bytes.size.toLong()) {
                throw IOException("Metadata publication failed")
            }
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
        pending.remove(uri)
        changes.tryEmit(Unit)
    }

    private fun duration(uri: Uri): Long = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        }
    } catch (_: RuntimeException) { 0L }
      catch (_: IOException) { 0L }

    @Synchronized
    override fun delete(uri: Uri) {
        require(uri.authority == "${context.packageName}.works" && uri.pathSegments.firstOrNull() == "works")
        val external = context.getExternalFilesDir(null) ?: throw IOException("External storage unavailable")
        val file = File(File(external, "works"), uri.pathSegments.drop(1).joinToString("/")).canonicalFile
        require(file.parentFile?.parentFile == root().canonicalFile) { "Output outside works directory" }
        if (file.parentFile!!.exists() && !file.parentFile!!.deleteRecursively()) throw IOException("Cannot delete output")
        pending.remove(uri)
        changes.tryEmit(Unit)
    }

    // ponytail: scan completed metadata on refresh; add an index if large libraries make this slow.
    @Synchronized
    override fun query(): List<MediaItem> {
        val folders = root().listFiles() ?: throw IOException("Cannot list works directory")
        return folders.filter { it.isDirectory }.mapNotNull { folder ->
            try {
                val metadata = JSONObject(AtomicFile(File(folder, "completed.json")).openRead()
                    .bufferedReader(Charsets.UTF_8).use { it.readText() })
                val name = metadata.getString("name")
                val file = File(folder, name).canonicalFile
                if (file.parentFile != folder.canonicalFile || !file.isFile || file.length() == 0L) return@mapNotNull null
                MediaItem(FileProvider.getUriForFile(context, "${context.packageName}.works", file),
                    name, MediaKind.valueOf(metadata.getString("kind")),
                    metadata.getLong("dateAdded"), metadata.getLong("durationMs"))
            } catch (_: IOException) { null }
              catch (_: org.json.JSONException) { null }
              catch (_: IllegalArgumentException) { null }
        }.sortedByDescending(MediaItem::dateAdded)
    }
}
