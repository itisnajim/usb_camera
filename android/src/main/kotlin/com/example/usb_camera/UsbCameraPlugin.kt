package com.example.usb_camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.core.app.ActivityCompat

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import android.renderscript.*
import android.view.Surface
import io.flutter.plugin.common.EventChannel
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.FileInputStream
import java.io.IOException
import java.util.Arrays
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** UsbCameraPlugin */
class UsbCameraPlugin : FlutterPlugin, MethodCallHandler {
    companion object {
        private const val TAG = "UsbCameraPlugin"
        private const val methodChannelName = "usb_camera"
    }

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel

    private lateinit var eventChannel: EventChannel
    private var eventSink: EventChannel.EventSink? = null

    private lateinit var videoEventChannel: EventChannel
    private var videoEventSink: EventChannel.EventSink? = null

    private lateinit var context: Context
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var cameraHandler: Handler? = null
    private var cameraThread: HandlerThread? = null
    private var captureSession: CameraCaptureSession? = null
    private var isCaptureSessionActive: Boolean = false

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, methodChannelName)
        channel.setMethodCallHandler(this)

        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "usb_camera/image_stream")
        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                eventSink = sink
            }

            override fun onCancel(arguments: Any?) {
                eventSink = null
            }
        })

        videoEventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "usb_camera/video_stream")
        videoEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                videoEventSink = sink
            }

            override fun onCancel(arguments: Any?) {
                videoEventSink = null
            }
        })

        context = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "list" -> getCameraList(result)
            "open" -> {
                val cameraId = call.argument<String>("cameraId")!!
                open(cameraId, result)
            }

            "close" -> close(result)
            "startCapture" -> startCapture(result)
            "stopCapture" -> stopCapture(result)
            else -> result.notImplemented()
        }
    }

    private fun getCameraList(result: Result): List<Map<String, Any>> {
        val cameraList = mutableListOf<Map<String, Any>>()

        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val cameraInfo = hashMapOf<String, Any>()

                cameraInfo["id"] = cameraId
                cameraInfo["facing"] =
                    characteristics.get(CameraCharacteristics.LENS_FACING) ?: "unknown"

                // Sensor information
                characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let {
                    cameraInfo["sensor_size"] = it.toString()
                }

                // Flash information
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)?.let {
                    cameraInfo["flash_available"] = it
                }

                // Hardware level
                characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)?.let {
                    cameraInfo["hardware_level"] = when (it) {
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                        else -> "Unknown"
                    }
                }

                // Supported output formats
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.let { map ->
                        val outputFormats = map.outputFormats.joinToString(", ") { format ->
                            when (format) {
                                ImageFormat.JPEG -> "JPEG"
                                ImageFormat.RAW_SENSOR -> "RAW"
                                ImageFormat.YUV_420_888 -> "YUV_420_888"
                                else -> "Format_$format"
                            }
                        }
                        cameraInfo["output_formats"] = outputFormats
                    }

                cameraList.add(cameraInfo)
            }
            result.success(cameraList)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            result.error("CANNOT_GET_CAMERA_LIST", e.localizedMessage, null);
        }

        return cameraList
    }

    private fun open(cameraId: String, result: Result) {
        if (cameraDevice?.id == cameraId) return result.error(
            "CAMERA_ALREADY_OPEN",
            "The selected camera already open.",
            null
        )
        cameraThread = HandlerThread("CameraThread").apply {
            start()
        }
        cameraHandler = Handler(cameraThread!!.looper)

        val cameraStateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                result.success(null) // Indicate success with no return value
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                result.error("CAMERA_DISCONNECTED", "The camera has been disconnected", null)
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                result.error("CAMERA_ERROR", "An error occurred with the camera: $error", null)
            }
        }

        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler)
            } else {
                result.error("PERMISSION_DENIED", "Camera permission denied", null)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            result.error(
                "CAMERA_ACCESS_EXCEPTION",
                "Failed to access the camera: ${e.message}",
                null
            )
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            result.error("INVALID_ARGUMENT", "Invalid argument provided: ${e.message}", null)
        } catch (e: Exception) {
            e.printStackTrace()
            result.error("UNKNOWN_ERROR", "An unknown error occurred: ${e.message}", null)
        }
    }

    private fun close(result: Result?) {
        try {
            cameraDevice?.close()
            cameraDevice = null
            cameraThread?.quitSafely()
            cameraThread = null
            cameraHandler?.removeCallbacksAndMessages(null)
            cameraHandler = null
            Log.d(TAG, "Camera closed")
            result?.success(null) // Indicate success
        } catch (e: Exception) {
            e.printStackTrace()
            result?.error("CLOSE_CAMERA_ERROR", "Failed to close the camera: ${e.message}", null)
        }
    }


    private fun startCapture(result: Result) {
        if (cameraDevice == null) {
            result.error("CAMERA_NOT_OPENED", "Please open a camera first.", null)
            return
        }

        if (isCaptureSessionActive) {
            result.success("Capture session already active")
            return
        }

        setupImageReader();

        try {
            val captureRequestBuilder =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)?.apply {
                    addTarget(imageReader!!.surface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                }

            cameraDevice?.createCaptureSession(
                listOf(imageReader?.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(
                                captureRequestBuilder!!.build(),
                                null,
                                cameraHandler
                            )

                            isCaptureSessionActive = true
                            result.success(cameraDevice!!.id)
                        } catch (e: CameraAccessException) {
                            result.error(
                                "START_CAPTURE_FAILED",
                                "Failed to start capture session: ${e.localizedMessage}",
                                null
                            )
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        result.error(
                            "CAPTURE_SESSION_CONFIG_FAILED",
                            "Failed to configure capture session",
                            null
                        )
                    }
                },
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            result.error(
                "START_CAPTURE_FAILED",
                "Failed to start capture session: ${e.localizedMessage}",
                null
            )
        }
    }


    private fun stopCapture(result: Result) {
        if (!isCaptureSessionActive) {
            result.error("CAPTURE_SESSION_NOT_ACTIVE", "No active capture session to stop.", null)
            return
        }

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null
            isCaptureSessionActive = false

            imageReader?.close() // Close the ImageReader
            imageReader = null // Clear the ImageReader

            result.success("Capture session stopped")
        } catch (e: CameraAccessException) {
            result.error(
                "STOP_CAPTURE_FAILED",
                "Failed to stop capture session: ${e.localizedMessage}",
                null
            )
        }
    }

    private var videoEncoder: MediaCodec? = null
    private var videoBufferInfo: MediaCodec.BufferInfo? = null
    private var videoFormat: MediaFormat? = null
    private var isStreaming: Boolean = false
    private var frameSendingThread: Thread? = null

    private fun startVideoStream(result: Result) {
        if (cameraDevice == null) {
            result.error("CAMERA_NOT_OPENED", "Camera is not opened.", null)
            return
        }

        // Initialize MediaCodec for the video encoder
        videoEncoder = MediaCodec.createEncoderByType("video/avc") // H.264 codec
        videoBufferInfo = MediaCodec.BufferInfo()

        // Set up the video format. You may need to adjust width, height, and frame rate
        val width = 640
        val height = 480
        videoFormat = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 125000) // Adjust the bitrate as needed
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5) // Key frame interval
        }

        videoEncoder?.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface =
            videoEncoder?.createInputSurface() // Create an input surface for the encoder

        videoEncoder?.start()

        // Set up camera to capture frames to the input surface
        inputSurface?.let {surface ->
            setupCameraCaptureSession(surface)
        }

        isStreaming = true
        startFrameSendingLoop()
        result.success(null) // Indicate success
    }

    private fun startFrameSendingLoop() {
        frameSendingThread = Thread(Runnable {
            while (isStreaming) {
                sendVideoFrames()
                Thread.sleep(10) // Adjust this value as needed for performance
            }
        }).apply {
            start()
        }
    }

    private fun sendVideoFrames() {
        if (videoEncoder == null || videoBufferInfo == null) return

        while (isStreaming) {
            val bufferIndex = videoEncoder!!.dequeueOutputBuffer(videoBufferInfo!!, 0)

            if (bufferIndex >= 0) {
                // Fetch the encoded frame
                val encodedData = videoEncoder!!.getOutputBuffer(bufferIndex)

                if (encodedData == null) {
                    Log.e(TAG, "Encoder output buffer is null")
                    return
                }

                encodedData.position(videoBufferInfo!!.offset)
                encodedData.limit(videoBufferInfo!!.offset + videoBufferInfo!!.size)

                // Convert the encoded data to ByteArray
                val frameData = ByteArray(videoBufferInfo!!.size)
                encodedData.get(frameData)

                // Send the frame data to Flutter via the EventChannel
                Handler(Looper.getMainLooper()).post {
                    videoEventSink?.success(frameData)
                }

                // Release the buffer
                videoEncoder!!.releaseOutputBuffer(bufferIndex, false)
            } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Subsequent data will conform to new format.
                // Can handle new format here e.g. MediaFormat outputFormat = mediaCodec.getOutputFormat();
            } else if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // No output available yet
                try {
                    // Wait a short while before trying again
                    Thread.sleep(10)
                } catch (e: InterruptedException) {
                    // Interrupted, stop the streaming
                    break
                }
            }
        }
    }

    private fun stopFrameSendingLoop() {
        isStreaming = false
        frameSendingThread?.interrupt()
        frameSendingThread = null
    }

    private fun stopVideoStream(result: Result) {
        if (!isStreaming) {
            result.error("STREAM_NOT_ACTIVE", "Stream is not active.", null)
            return
        }

        // Stop the encoder and release resources
        videoEncoder?.stop()
        videoEncoder?.release()
        videoEncoder = null

        // Stop and release the camera capture session if necessary
        // ...

        isStreaming = false
        stopFrameSendingLoop()
        result.success(null) // Indicate success
    }


    private fun setupCameraCaptureSession(inputSurface: Surface) {
        try {
            // Create a capture request builder for a camera preview
            val requestBuilder =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    this?.addTarget(inputSurface)
                }

            // Create a CameraCaptureSession for camera preview
            cameraDevice?.createCaptureSession(
                listOf(inputSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // When the session is ready, we start displaying the preview
                        captureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview
                            requestBuilder?.set(
                                CaptureRequest.CONTROL_MODE,
                                CameraMetadata.CONTROL_MODE_AUTO
                            )

                            // Finally, we start displaying the camera preview
                            val captureRequest = requestBuilder?.build()
                            captureRequest?.let {
                                captureSession?.setRepeatingRequest(it, null, cameraHandler)
                            }
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Exception setting up capture session", e)
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Log.e(TAG, "Configuration failed for capture session")
                    }
                },
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Log.e(TAG, "Exception accessing the camera", e)
        }
    }


    private fun setupImageReader() {
        // Close existing ImageReader if it exists
        imageReader?.close()
        imageReader = null

        cameraDevice?.id?.let { cameraId ->
            cameraManager.getCameraCharacteristics(cameraId).let {
                val size = Size(640, 480)
                /*
                val size = map?.getOutputSizes(selectedImageFormat)
                    ?.maxByOrNull { it.width * it.height }
                    ?: Size(640, 480)*/

                Log.d(TAG, "Selected size: $size")

                imageReader =
                    ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
                        .apply {
                            setOnImageAvailableListener({ reader ->
                                if (!isCaptureSessionActive) {
                                    Log.w(
                                        TAG,
                                        "Capture session is not active. Skipping image acquisition."
                                    )
                                    return@setOnImageAvailableListener
                                }
                                reader.acquireNextImage().use { image ->
                                    try {
                                        Log.w(TAG, "Image acquisition start")
                                        // Process the image
                                        val extractData =
                                            extractDataFromImage(image)

                                        extractData?.let { imageData ->
                                            val storageDir = File(
                                                Environment.getExternalStoragePublicDirectory(
                                                    Environment.DIRECTORY_DCIM
                                                ),
                                                methodChannelName
                                            )
                                            if (!storageDir.exists()) storageDir.mkdirs()

                                            val imageFile = File(
                                                storageDir,
                                                "IMG_${System.currentTimeMillis()}.jpg"
                                            )
                                            try {
                                                streamImageData(imageData)
                                                saveFile(imageFile.absolutePath, imageData)
                                                MediaScannerConnection.scanFile(
                                                    context,
                                                    arrayOf(imageFile.toString()),
                                                    null,
                                                    null
                                                )

                                                Log.w(
                                                    TAG,
                                                    "Image Saved to gallery ${imageFile.absolutePath}!"
                                                )
                                                uploadFileToFTP(imageFile.absolutePath)
                                            } catch (e: Exception) {
                                                Log.e(TAG, e.localizedMessage ?: e.toString())
                                                e.printStackTrace()
                                            }
                                        }

                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Log.e(
                                            TAG,
                                            "Error in setOnImageAvailableListener task: ${e.message}"
                                        )
                                    } finally {
                                        image.close()
                                    }

                                }

                            }, cameraHandler)
                        }

            }
        }
    }

    private fun saveJpegImage(image: Image): File? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val storageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            methodChannelName
        )
        if (!storageDir.exists()) storageDir.mkdirs()

        val imageFile = File(storageDir, "IMG_${System.currentTimeMillis()}.jpg")
        return try {
            FileOutputStream(imageFile).use { outputStream ->
                outputStream.write(bytes)
            }
            MediaScannerConnection.scanFile(context, arrayOf(imageFile.toString()), null, null)
            imageFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractDataFromImage(image: Image): ByteArray? {
        val bitmap = yuvImageToBitmap(image) ?: return null
        ByteArrayOutputStream().use { byteArrayOutputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
            return byteArrayOutputStream.toByteArray()
        }
    }

    private fun streamImageData(data: ByteArray) {
        Log.w(TAG, "Streaming image data of size: ${data.size} ..")
        Handler(Looper.getMainLooper()).post {
            eventSink?.success(data)
        }
    }

    private fun saveFile(path: String, data: ByteArray) {
        try {
            FileOutputStream(File(path)).use { fileOutputStream ->
                fileOutputStream.write(data)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "Error saving file: ${e.localizedMessage}")
        }
    }

    private fun yuvImageToBitmap(image: Image): Bitmap? {
        val rs = RenderScript.create(context)
        val yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

        val ySize = image.planes[0].buffer.remaining()
        val uSize = image.planes[1].buffer.remaining()
        val vSize = image.planes[2].buffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        image.planes[0].buffer.get(nv21, 0, ySize)
        image.planes[2].buffer.get(nv21, ySize, vSize)
        image.planes[1].buffer.get(nv21, ySize + vSize, uSize)

        val yuvType = Type.Builder(rs, Element.U8(rs)).setX(nv21.size)
        val inAllocation = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT)

        val outBitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val outAllocation = Allocation.createFromBitmap(
            rs,
            outBitmap,
            Allocation.MipmapControl.MIPMAP_NONE,
            Allocation.USAGE_SCRIPT
        )

        inAllocation.copyFrom(nv21)

        yuvToRgbIntrinsic.setInput(inAllocation)
        yuvToRgbIntrinsic.forEach(outAllocation)

        outAllocation.copyTo(outBitmap)

        inAllocation.destroy()
        outAllocation.destroy()
        yuvToRgbIntrinsic.destroy()
        rs.destroy()

        return outBitmap
    }

    fun makeDirectoryIfNotExist(ftpClient: FTPClient, directory: String): Boolean {
        return if (ftpClient.changeWorkingDirectory(directory)) {
            ftpClient.changeToParentDirectory() // Change back to the parent directory
            true // Directory exists
        } else {
            ftpClient.makeDirectory(directory) // Try to create the directory
        }
    }

    private fun uploadFileToFTP(filePath: String) {

        val ftpClient = FTPClient()

        try {
            ftpClient.connect("154.144.241.232", 222)
            ftpClient.login("Camera", "Cam@@2024.")

            ftpClient.enterLocalPassiveMode()
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE)

            val input = FileInputStream(filePath)
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR).toString()
            val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
            val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
            val camFolder = "CAM" + (cameraDevice?.id ?: "_UNKNOWN")
            val fileName = filePath.substringAfterLast('/')
            val basePath = "/ftp-camera/$camFolder"
            val yearPath = "$basePath/$year"
            val monthPath = "$yearPath/$month"
            val dayPath = "$monthPath/$day"

            if (makeDirectoryIfNotExist(ftpClient, basePath) &&
                makeDirectoryIfNotExist(ftpClient, yearPath) &&
                makeDirectoryIfNotExist(ftpClient, monthPath) &&
                makeDirectoryIfNotExist(ftpClient, dayPath)
            ) {
                val result =
                    ftpClient.storeFile(
                        "/ftp-camera/$camFolder/$year/$month/$day/$fileName", input
                    )
                input.close()

                if (result) {
                    Log.w(TAG, "File uploaded successfully")
                } else {
                    Log.e(TAG, "Failed to upload file")
                }
            } else {
                // Handle the error in directory creation
                Log.e(TAG, "Failed to upload file, Cannot create the base folder.")
            }

        } catch (ex: Exception) {
            Log.e(TAG, "Error: " + ex.message)
            ex.printStackTrace()
        } finally {
            try {
                ftpClient.logout()
                ftpClient.disconnect()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        close(null)
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }
}
