package dev.vaultdrop

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import dev.vaultdrop.core.PhotoNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime

/**
 * The camera app and the system photo picker, opened for [EditorViewModel].
 *
 * The view model pins the note before either one opens and lands whatever
 * comes back. Opening them has to happen from the activity's side, so that
 * part lives here, registered the way the vault picker is.
 */
class PhotoCapture internal constructor(
    private val vm: EditorViewModel,
    private val scope: CoroutineScope,
    private val camera: ActivityResultLauncher<Uri>,
    private val picker: ActivityResultLauncher<PickVisualMediaRequest>,
) {
    /** Hands off to whichever camera app is the phone's default. */
    fun takePhoto() {
        scope.launch {
            val target = vm.startPhoto() ?: return@launch
            try {
                camera.launch(target)
            } catch (_: ActivityNotFoundException) {
                vm.onPhotoAppMissing()
            }
        }
    }

    /**
     * Opens the system photo picker for images. Ordered selection numbers each
     * photo as it is tapped and hands them back in that order, on a picker
     * new enough to offer it.
     */
    fun choosePhotos() {
        if (!vm.startChoosingPhotos()) return
        val request = PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
            .setOrderedSelection(true)
            .build()
        try {
            picker.launch(request)
        } catch (_: ActivityNotFoundException) {
            vm.onPhotoAppMissing()
        }
    }
}

@Composable
fun rememberPhotoCapture(vm: EditorViewModel): PhotoCapture {
    val scope = rememberCoroutineScope()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        vm.onPhotoTaken(saved)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.onPhotosChosen(uris)
    }
    return remember(vm, scope, camera, picker) { PhotoCapture(vm, scope, camera, picker) }
}

/**
 * The phone's side of a photo: the cache file the camera app writes into, and
 * the name and bytes of a photo the picker hands back. None of it is the vault,
 * which only [dev.vaultdrop.core.VaultFiles] touches, so it sits here beside
 * the launchers and stays out of core.
 *
 * Everything here blocks. Call it on IO.
 */
internal class PhotoFiles(context: Context) {

    private val app = context.applicationContext

    /** A file for the camera app to write, and the URI it writes through. */
    data class Shot(val file: File, val uri: Uri)

    /**
     * Whether any app answers `ACTION_IMAGE_CAPTURE`. From Android 11 on, the
     * manifest's `<queries>` entry is what lets the app see the answer.
     */
    fun hasCameraApp(): Boolean =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(app.packageManager) != null

    /**
     * Makes room for the next camera photo. The camera app creates the file
     * itself, through the URI. Null when the cache folder can't be made.
     *
     * Empties the folder first. A camera capture only starts while no other
     * capture holds the note, so anything still in here was left by one that
     * never came back: the process died while the camera was open, or the
     * delete after the copy never ran.
     */
    fun newShot(): Shot? {
        // Not looked up when this is made: the view model makes it before
        // Phase 1, and getCacheDir can create the folder.
        val dir = File(app.cacheDir, CAPTURE_DIR)
        dir.listFiles()?.forEach { it.delete() }
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val file = File(dir, "${System.currentTimeMillis()}.${PhotoNames.CAMERA_EXT}")
        val uri = runCatching { FileProvider.getUriForFile(app, app.packageName + AUTHORITY_SUFFIX, file) }
            .getOrNull() ?: return null
        return Shot(file, uri)
    }

    /**
     * The name a picked photo keeps: what its provider calls it, with the
     * extension its MIME type maps to for a name that has none. `MimeTypeMap`
     * is the table [dev.vaultdrop.core.VaultFiles.mimeFor] maps back through,
     * so the provider writing the vault keeps the name as given.
     */
    fun nameOf(uri: Uri, now: LocalDateTime): PhotoNames.Name {
        val resolver = app.contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
            }
        }.getOrNull()
        val mimeExt = runCatching { resolver.getType(uri) }.getOrNull()
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return PhotoNames.picked(displayName, mimeExt, now)
    }

    fun open(uri: Uri): InputStream? = runCatching { app.contentResolver.openInputStream(uri) }.getOrNull()

    fun open(file: File): InputStream? = runCatching { file.inputStream() }.getOrNull()

    private companion object {
        /** `cache/capture/`, the one folder `res/xml/file_paths.xml` shares. */
        const val CAPTURE_DIR = "capture"

        /**
         * Goes after the package name, as `${applicationId}.fileprovider` does
         * in the manifest, so an install under another application ID claims
         * its own authority and the two can sit side by side.
         */
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}
