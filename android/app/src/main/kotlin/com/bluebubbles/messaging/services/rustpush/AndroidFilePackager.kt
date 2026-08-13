package com.bluebubbles.messaging.services.rustpush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.util.Log
import android.webkit.MimeTypeMap
import com.fluttercandies.flutter_image_compress.exif.Exif
import uniffi.rust_lib_bluebubbles.FileInfo
import uniffi.rust_lib_bluebubbles.KotlinFilePackager
import uniffi.rust_lib_bluebubbles.PackagedFile
import java.io.ByteArrayOutputStream
import java.lang.Exception

class AndroidFilePackager(val context: Context): KotlinFilePackager {

    override fun scanFiles(paths: List<String>) {
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), arrayOf()) { path, uri ->
            Log.i("PACKAGER", "Scan completed $path $uri")
        }
    }

    override fun getFile(path: String): PackagedFile {
        try {
            val metadataRetriever = MediaMetadataRetriever()
            Log.i("PACKAGER", "Packaging for $path")
            try {
                Log.i("PACKAGER", "B")
                metadataRetriever.setDataSource(path)
            } catch (e: Exception) {
                Log.i("PACKAGER", "C")
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                options.inDensity = 1
                options.inTargetDensity = 1
                BitmapFactory.decodeFile(path, options)
                val ei = ExifInterface(path)
                val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                var width = options.outWidth.toUInt()
                var height = options.outHeight.toUInt()
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                    val temp = width
                    width = height
                    height = temp
                }
                return PackagedFile.Info(
                    FileInfo(
                        duration = null,
                        width = width,
                        height = height,
                        thumbnail = null
                    )
                )
            }
            Log.i("PACKAGER", "D")

            if (metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") {
                val duration =
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                        .toInt() / 1000.0
                val height =
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!
                        .toUInt()
                val width =
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!
                        .toUInt()
                val thumbnail = metadataRetriever.frameAtTime
                    ?: return PackagedFile.Failure("File $path has no frame!")

                val stream = ByteArrayOutputStream()
                thumbnail.compress(Bitmap.CompressFormat.PNG, 90, stream)
                val image = stream.toByteArray()

                return PackagedFile.Info(
                    FileInfo(
                        duration = duration,
                        width = width,
                        height = height,
                        thumbnail = image
                    )
                )
            } else if (metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_IMAGE) == "yes") {
                val height =
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!
                        .toUInt()
                val width =
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!
                        .toUInt()
                return PackagedFile.Info(
                    FileInfo(
                        duration = null,
                        width = width,
                        height = height,
                        thumbnail = null
                    )
                )
            } else {
                return PackagedFile.Failure("File $path has neither video nor stills")
            }
        } catch (e: Exception) {
            Log.e("PACKAGER", "$e")
            return PackagedFile.Failure("$e")
        }
    }
}