package de.deinetierwelt.app.handler

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.webkit.ValueCallback
import androidx.activity.result.ActivityResult
import androidx.core.content.FileProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import de.deinetierwelt.app.Constants
import de.deinetierwelt.app.Constants.Companion.FILE_PROVIDER
import de.deinetierwelt.app.activities.MainActivity
import java.io.File
import java.io.IOException
import java.util.*

object FileUploadHandler {

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var photoPath: String? = null
    private var videoPath: String? = null

    fun handleFileUpload(context: MainActivity, filePathCallback: ValueCallback<Array<Uri>>): Boolean {
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = filePathCallback

        val capturePhotoIntent = createCaptureIntent(context, MediaStore.ACTION_IMAGE_CAPTURE, ::createImageFile)
        val captureVideoIntent = createCaptureIntent(context, MediaStore.ACTION_VIDEO_CAPTURE, ::createVideoFile)

        val selectionIntent = createSelectionIntent()

        val availableIntents = listOfNotNull(capturePhotoIntent, captureVideoIntent).toTypedArray<Parcelable>()
        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, selectionIntent)
            putExtra(Intent.EXTRA_TITLE, "Select a Photo/Video")
            putExtra(Intent.EXTRA_INITIAL_INTENTS, availableIntents)
        }

        context.cameraResultLauncher.launch(chooserIntent)
        return true
    }

    fun processCameraResult(result: ActivityResult) {
        val data = result.data
        val selectedUris = mutableListOf<Uri>()

        try {
            photoPath?.let { selectedUris.addIfValidFile(it) }
            videoPath?.let { selectedUris.addIfValidFile(it) }
        } catch (e: Exception) {
            logError("Error processing camera result", e)
        }

        if (result.resultCode == Activity.RESULT_OK) {
            selectedUris.addAll(getUrisFromIntent(data))
        }

        fileUploadCallback?.onReceiveValue(selectedUris.toTypedArray())
        fileUploadCallback = null
    }

    private fun createCaptureIntent(
        context: Context,
        action: String,
        fileCreator: (Context) -> File
    ): Intent? {
        val intent = Intent(action)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }?.apply {
            try {
                val file = fileCreator(context)
                val fileUri = FileProvider.getUriForFile(context, FILE_PROVIDER, file)
                putExtra(MediaStore.EXTRA_OUTPUT, fileUri)
            } catch (ex: IOException) {
                logError("Failed to create file for $action", ex)
            }
        }
    }

    private fun createSelectionIntent(): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }

    private fun getUrisFromIntent(data: Intent?): List<Uri> {
        val uris = mutableListOf<Uri>()
        data?.let {
            when {
                it.data != null && it.clipData == null -> {
                    uris.add(Uri.parse(it.dataString))
                }
                it.clipData != null -> {
                    val clipData = it.clipData!!
                    for (i in 0 until clipData.itemCount) {
                        uris.add(clipData.getItemAt(i).uri)
                    }
                }
            }
        }
        return uris
    }

    private fun createImageFile(context: Context): File {
        val fileName = UUID.randomUUID().toString()
        val storageDir = getStorageDirectory(context, Environment.DIRECTORY_PICTURES)
        return File.createTempFile(fileName, ".jpg", storageDir).apply {
            photoPath = absolutePath
        }
    }

    private fun createVideoFile(context: Context): File {
        val fileName = UUID.randomUUID().toString()
        val storageDir = getStorageDirectory(context, Environment.DIRECTORY_MOVIES)
        return File.createTempFile(fileName, ".mp4", storageDir).apply {
            videoPath = absolutePath
        }
    }

    private fun getStorageDirectory(context: Context, type: String): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(type)
        } else {
            Environment.getExternalStoragePublicDirectory(type)
        }
    }

    private fun MutableList<Uri>.addIfValidFile(filePath: String) {
        val file = File(filePath.removePrefix("file:"))
        if (file.exists() && file.length() > 0) {
            add(Uri.fromFile(file))
        }
    }

    private fun logError(message: String, exception: Exception) {
        Log.d("E/TAG: ${Constants.TAG} $message: ${exception.localizedMessage}") 
    }
}
