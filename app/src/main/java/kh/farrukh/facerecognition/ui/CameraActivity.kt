package kh.farrukh.facerecognition.ui

import android.content.Intent
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image.Plane
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.*
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kh.farrukh.facerecognition.R
import kh.farrukh.facerecognition.utils.ConnectionCallback
import kh.farrukh.facerecognition.utils.ImageUtils

/**
 *Created by farrukh_kh on 6/8/21 7:25 PM
 *kh.farrukh.facerecognition.ui
 **/
abstract class CameraActivity : AppCompatActivity(), OnImageAvailableListener,
    PreviewCallback {

    protected var previewWidth = 0
    protected var previewHeight = 0
    private val debug = false
    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private var useCamera2API = false
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    private var useFacing: Int? = null
    private var cameraId: String? = null
    protected fun getCameraFacing() = useFacing

    companion object {
        private const val KEY_USE_FACING = "use_facing"
    }

    protected abstract fun processImage()
    protected abstract fun onPreviewSizeChosen(size: Size?, rotation: Int)
    protected abstract fun getLayoutId(): Int
    protected abstract fun getDesiredPreviewFrameSize(): Size?
    protected abstract fun setNumThreads(numThreads: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
//        Log.e("onCreate", "$this")
        super.onCreate(savedInstanceState)
        useFacing = intent.getIntExtra(KEY_USE_FACING, CameraCharacteristics.LENS_FACING_FRONT);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_user)

        setFragment()

        //TODO
        // apiSwitchCompat.setOnCheckedChangeListener(this)
        // plusImageView.setOnClickListener(this)
        // minusImageView.setOnClickListener(this)

        findViewById<FloatingActionButton>(R.id.fab_switch_cam).setOnClickListener { switchCamera() }
    }

    protected fun switchCamera() {
        useFacing = if (useFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            CameraCharacteristics.LENS_FACING_BACK
        } else {
            CameraCharacteristics.LENS_FACING_FRONT
        }
        intent.putExtra(KEY_USE_FACING, useFacing)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        restartWith(intent)
    }

    private fun restartWith(intent: Intent) {
        finish()
        overridePendingTransition(0, 0)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    protected fun getRgbBytes(): IntArray? {
        imageConverter!!.run()
        return rgbBytes
    }

    protected fun getLuminanceStride() = yRowStride

    protected fun getLuminance() = yuvBytes[0]

    override fun onPreviewFrame(bytes: ByteArray?, camera: Camera) {
        if (isProcessingFrame) {
//            Log.e("onPreviewFrame", "Dropping frame")
            return
        }
        try {
            if (rgbBytes == null) {
                val previewSize = camera.parameters.previewSize
                previewHeight = previewSize.height
                previewWidth = previewSize.width
                rgbBytes = IntArray(previewWidth * previewHeight)
                var rotation = 90
                if (useFacing == CameraCharacteristics.LENS_FACING_FRONT) rotation = 270
                onPreviewSizeChosen(Size(previewSize.width, previewSize.height), rotation)
            }
        } catch (e: Exception) {
//            Log.e("onPreviewFrame", "Exception: ${e.message}")
            return
        }
        isProcessingFrame = true
        yuvBytes[0] = bytes
        yRowStride = previewWidth
        imageConverter = Runnable {
            ImageUtils.convertYUV420SPToARGB8888(
                bytes!!,
                previewWidth,
                previewHeight,
                rgbBytes!!
            )
        }
        postInferenceCallback = Runnable {
            camera.addCallbackBuffer(bytes)
            isProcessingFrame = false
        }
        processImage()
    }

    override fun onImageAvailable(reader: ImageReader) {
        if (previewWidth == 0 || previewHeight == 0) return
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return
            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true
//            Trace.beginSection("imageAvailable")
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = Runnable {
                ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes!!
                )
            }
            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }
            processImage()
        } catch (e: Exception) {
//            Log.e("onImageAvailable", "Exception: ${e.message}")
//            Trace.endSection()
            return
        }
//        Trace.endSection()
    }

//    @Synchronized
//    public override fun onStart() {
//        Log.e("onStart", "$this")
//        super.onStart()
//    }

    @Synchronized
    public override fun onResume() {
//        Log.e("onResume", "$this")
        super.onResume()
        handlerThread = HandlerThread("inference")
        handlerThread!!.start()
        handler = Handler(handlerThread!!.looper)
    }

    @Synchronized
    public override fun onPause() {
//        Log.e("onPause", "$this")
        handlerThread!!.quitSafely()
        try {
            handlerThread!!.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
//            Log.e("onPause", "Exception: ${e.message}")
        }
        super.onPause()
    }

//    @Synchronized
//    public override fun onStop() {
//        Log.e("onStop", "$this")
//        super.onStop()
//    }

//    @Synchronized
//    public override fun onDestroy() {
//        Log.e("onDestroy", "$this")
//        super.onDestroy()
//    }

    @Synchronized
    protected fun runInBackground(r: Runnable?) {
        if (handler != null) {
            handler!!.post(r!!)
        }
    }

    private fun isHardwareLevelSupported(
        characteristics: CameraCharacteristics, requiredLevel: Int
    ): Boolean {
        val deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
        return if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            requiredLevel == deviceLevel
        } else requiredLevel <= deviceLevel
    }

    private fun chooseCamera(): String? {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (useFacing != null && facing != null && facing != useFacing) continue
                useCamera2API = (facing == CameraCharacteristics.LENS_FACING_EXTERNAL
                        || isHardwareLevelSupported(
                    characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                ))
//                Log.e("chooseCamera", "Camera API lv2: $useCamera2API")
                return cameraId
            }
        } catch (e: CameraAccessException) {
//            Log.e("chooseCamera", "Not allowed to access camera")
        }
        return null
    }

    private fun setFragment() {
        cameraId = chooseCamera()
        val fragment: Fragment
        if (useCamera2API) {
            val camera2Fragment = CameraFragment.newInstance(
                object : ConnectionCallback {
                    override fun onPreviewSizeChosen(size: Size?, cameraRotation: Int) {
                        previewHeight = size!!.height
                        previewWidth = size.width
                        this@CameraActivity.onPreviewSizeChosen(size, cameraRotation)
                    }
                },
                this,
                getLayoutId(),
                getDesiredPreviewFrameSize()!!
            )
            camera2Fragment.setCamera(cameraId!!)
            fragment = camera2Fragment
        } else {
            val facing =
                if (useFacing == CameraCharacteristics.LENS_FACING_BACK) Camera.CameraInfo.CAMERA_FACING_BACK else Camera.CameraInfo.CAMERA_FACING_FRONT
            val frag = LegacyCameraFragment.newInstance(
                this,
                getLayoutId(),
                getDesiredPreviewFrameSize()!!, facing
            )
            fragment = frag
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    private fun fillBytes(planes: Array<Plane>, yuvBytes: Array<ByteArray?>) {
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
//                Log.e("fillBytes", "Initializing buffer $i at size ${buffer.capacity()}")
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]!!]
        }
    }

    fun isDebug() = debug

    protected fun readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback!!.run()
        }
    }

    protected fun getScreenOrientation() = when (windowManager.defaultDisplay.rotation) {
        Surface.ROTATION_270 -> 270
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_90 -> 90
        else -> 0
    }

    protected fun showInference(inferenceTime: String?) {
        findViewById<TextView>(R.id.tv_inference).text = inferenceTime
    }
}