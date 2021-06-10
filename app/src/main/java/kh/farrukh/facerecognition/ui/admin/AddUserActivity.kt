package kh.farrukh.facerecognition.ui.admin

import android.content.DialogInterface
import android.graphics.*
import android.hardware.camera2.CameraCharacteristics
import android.media.ImageReader
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.util.TypedValue
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kh.farrukh.facerecognition.R
import kh.farrukh.facerecognition.customviews.OverlayView
import kh.farrukh.facerecognition.database.Recognition
import kh.farrukh.facerecognition.databinding.ActivityAddUserBinding
import kh.farrukh.facerecognition.tflite.SimilarityClassifier
import kh.farrukh.facerecognition.tflite.TFLiteFaceRecognitionModel
import kh.farrukh.facerecognition.tracking.MultiBoxTracker
import kh.farrukh.facerecognition.ui.CameraActivity
import kh.farrukh.facerecognition.utils.BorderedText
import kh.farrukh.facerecognition.utils.ImageUtils
import java.io.IOException
import java.util.*

class AddUserActivity : CameraActivity(), ImageReader.OnImageAvailableListener {

    companion object {
        private const val TF_OD_API_INPUT_SIZE = 112
        private const val TF_OD_API_IS_QUANTIZED = false
        private const val TF_OD_API_MODEL_FILE = "mobile_face_net.tflite"
        private const val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"
        private val MODE = DetectorMode.TF_OD_API
        private const val MINIMUM_CONFIDENCE_TF_OD_API = 0.5f
        private const val MAINTAIN_ASPECT = false
        private val DESIRED_PREVIEW_SIZE = Size(640, 480)
        private const val SAVE_PREVIEW_BITMAP = true
        private const val TEXT_SIZE_DIP = 10f
    }

    private var sensorOrientation: Int? = null
    private var detector: SimilarityClassifier? = null
    private var lastProcessingTimeMs: Long = 0
    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null
    private var computingDetection = false
    private var addPending = false
    private var timestamp: Long = 0
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null
    private var tracker: MultiBoxTracker? = null
    private var borderedText: BorderedText? = null
    private var faceDetector: FaceDetector? = null
    private var portraitBmp: Bitmap? = null
    private var faceBmp: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_user)

        findViewById<FloatingActionButton>(R.id.fab_take_picture).setOnClickListener { onAddClick() }
        findViewById<FloatingActionButton>(R.id.fab_switch_cam).setOnClickListener { switchCamera() }

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    private fun onAddClick() {
        addPending = true
        processImage()
        Toast.makeText(this, "Detecting and calculating...", Toast.LENGTH_LONG).show();
    }

    override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics
        )
        borderedText = BorderedText(textSizePx)
        borderedText!!.setTypeface(Typeface.MONOSPACE)
        tracker = MultiBoxTracker(this)
        try {
            detector = TFLiteFaceRecognitionModel.create(
                assets,
                TF_OD_API_MODEL_FILE,
                TF_OD_API_LABELS_FILE,
                TF_OD_API_INPUT_SIZE,
                TF_OD_API_IS_QUANTIZED,
                this
            )
        } catch (e: IOException) {
            e.printStackTrace()
//            Log.e("onPreviewSizeChosen", "Exception initializing classifier")
            Toast.makeText(
                applicationContext, "Classifier could not be initialized", Toast.LENGTH_SHORT
            ).show()
            finish()
        }
        previewWidth = size!!.width
        previewHeight = size.height
        sensorOrientation = rotation - getScreenOrientation()
//        Log.e(
//            "onPreviewSizeChosen",
//            "Camera orientation relative to screen canvas: $sensorOrientation"
//        )
//        Log.e("onPreviewSizeChosen", "Initializing at size $previewWidth*$previewHeight")
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        val targetW: Int
        val targetH: Int
        if (sensorOrientation == 90 || sensorOrientation == 270) {
            targetH = previewWidth
            targetW = previewHeight
        } else {
            targetW = previewWidth
            targetH = previewHeight
        }
        val cropW = (targetW / 2.0).toInt()
        val cropH = (targetH / 2.0).toInt()
        croppedBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        portraitBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        faceBmp =
            Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888)
        frameToCropTransform = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropW, cropH,
            sensorOrientation!!, MAINTAIN_ASPECT
        )

        cropToFrameTransform = Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)
        val frameToPortraitTransform: Matrix = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            targetW, targetH,
            sensorOrientation!!, MAINTAIN_ASPECT
        )
        findViewById<OverlayView>(R.id.tracking_overlay_view).addCallback(
            object : OverlayView.DrawCallback {
                override fun drawCallback(canvas: Canvas?) {
                    tracker!!.draw(canvas!!)
                    if (isDebug()) {
                        tracker!!.drawDebug(canvas)
                    }
                }
            })
        tracker!!.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation!!)
    }

    override fun processImage() {
        if (addPending) {
            ++timestamp
            val currTimestamp = timestamp
            findViewById<OverlayView>(R.id.tracking_overlay_view).postInvalidate()

            if (computingDetection) {
                readyForNextImage()
                return
            }
            computingDetection = true
//        Log.e("processImage", "Preparing image $currTimestamp for detection in bg thread.")
            rgbFrameBitmap!!.setPixels(
                getRgbBytes(),
                0,
                previewWidth,
                0,
                0,
                previewWidth,
                previewHeight
            )
            readyForNextImage()
            val canvas = Canvas(croppedBitmap!!)
            canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
            if (SAVE_PREVIEW_BITMAP) {
                ImageUtils.saveBitmap(croppedBitmap!!, "preview.png")
            }
            val image = InputImage.fromBitmap(croppedBitmap!!, 0)
            faceDetector!!
                .process(image)
                .addOnSuccessListener(OnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        updateResults(currTimestamp, LinkedList<Recognition>())
                        addPending = false
                        return@OnSuccessListener
                    }
                    try {
                        runInBackground {
                            onFacesDetected(currTimestamp, faces, addPending)
                            addPending = false
                        }
                    } catch (e: Exception) {
                        addPending = false
                        Toast.makeText(this, "There isn`t any face", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    override fun getLayoutId(): Int {
        return R.layout.fragment_camera
    }

    override fun getDesiredPreviewFrameSize(): Size {
        return DESIRED_PREVIEW_SIZE
    }

    private enum class DetectorMode {
        TF_OD_API
    }

    override fun setNumThreads(numThreads: Int) {
//        runInBackground { detector!!.setNumThreads(numThreads) }
    }

    private fun createTransform(
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        applyRotation: Int
    ): Matrix {
        val matrix = Matrix()
        if (applyRotation != 0) {
//            if (applyRotation % 90 != 0) {
//                Log.e("createTransform", "Rotation of $applyRotation % 90 != 0")
//            }
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)
            matrix.postRotate(applyRotation.toFloat())
        }
        if (applyRotation != 0) {
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
        }
        return matrix
    }

    private fun showAddFaceDialog(rec: Recognition) {
        val builder = AlertDialog.Builder(this)
        val dialogLayout = layoutInflater.inflate(R.layout.dialog_add, null)
        val ivFace = dialogLayout.findViewById<ImageView>(R.id.iv_image)
        val etName = dialogLayout.findViewById<EditText>(R.id.et_name)
        ivFace.setImageBitmap(rec.crop)
        builder.setView(dialogLayout)
        val dlg = builder.show()
        dialogLayout.findViewById<MaterialButton>(R.id.btn_ok).setOnClickListener {
            val name = etName.text.toString()
            if (name.isEmpty()) {
                return@setOnClickListener
            }
            detector!!.register(rec.copy(title = name))
            dlg.dismiss()
        }
    }

    private fun updateResults(currTimestamp: Long, mappedRecognitions: List<Recognition>) {
//        tracker!!.trackResults(mappedRecognitions, currTimestamp)
        findViewById<OverlayView>(R.id.tracking_overlay_view).postInvalidate()
        computingDetection = false
        if (mappedRecognitions.isNotEmpty()) {
//            Log.e("updateResults", "Adding results")
            val rec: Recognition = mappedRecognitions[0]
            if (rec.extra != null) {
                showAddFaceDialog(rec)
            }
        }
        runOnUiThread {
            showInference(lastProcessingTimeMs.toString() + "ms")
        }
    }

    private fun onFacesDetected(currTimestamp: Long, faces: List<Face>, add: Boolean) {
        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap!!)
        val canvas = Canvas(cropCopyBitmap!!)
        val paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f
        var minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API
        minimumConfidence =
            when (MODE) {
                DetectorMode.TF_OD_API -> MINIMUM_CONFIDENCE_TF_OD_API
            }
        val mappedRecognitions: MutableList<Recognition> = LinkedList<Recognition>()
        val sourceW = rgbFrameBitmap!!.width
        val sourceH = rgbFrameBitmap!!.height
        val targetW = portraitBmp!!.width
        val targetH = portraitBmp!!.height
        val transform = createTransform(
            sourceW,
            sourceH,
            targetW,
            targetH,
            sensorOrientation!!
        )
        val cv = Canvas(portraitBmp!!)
        cv.drawBitmap(rgbFrameBitmap!!, transform, null)
        val cvFace = Canvas(faceBmp!!)
        val saved = false
        for (face in faces) {
//            Log.e("onFacesDetected", "Face: $face")
//            Log.e("onFacesDetected", "Running detection on face $currTimestamp")
            val boundingBox = RectF(face.boundingBox)
            val goodConfidence = true
            if (goodConfidence) {
                cropToFrameTransform!!.mapRect(boundingBox)
                val faceBB = RectF(boundingBox)
                transform.mapRect(faceBB)
                val sx = TF_OD_API_INPUT_SIZE.toFloat() / faceBB.width()
                val sy = TF_OD_API_INPUT_SIZE.toFloat() / faceBB.height()
                val matrix = Matrix()
                matrix.postTranslate(-faceBB.left, -faceBB.top)
                matrix.postScale(sx, sy)
                cvFace.drawBitmap(portraitBmp!!, matrix, null)
                var label = ""
                var confidence = -1f
                var color = Color.BLUE
                var extra: Any? = null
                var crop: Bitmap? = null
                if (add) {
                    crop = Bitmap.createBitmap(
                        portraitBmp!!,
                        faceBB.left.toInt(),
                        faceBB.top.toInt(),
                        faceBB.width().toInt(),
                        faceBB.height().toInt()
                    )
                }
                val startTime = SystemClock.uptimeMillis()
                val resultsAux: List<Recognition?> = detector!!.recognizeImage(faceBmp!!, add)
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                if (resultsAux.isNotEmpty()) {
                    val result = resultsAux[0]!!
                    extra = result.extra
                    val conf = result.distance
                    if (conf < 1.0f) {
                        confidence = conf
                        label = result.title
                        color = if (result.id == 0) {
                            Color.GREEN
                        } else {
                            Color.RED
                        }
                    }
                }
                if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {
                    val flip = Matrix()
                    if (sensorOrientation == 90 || sensorOrientation == 270) {
                        flip.postScale(1f, -1f, previewWidth / 2.0f, previewHeight / 2.0f)
                    } else {
                        flip.postScale(-1f, 1f, previewWidth / 2.0f, previewHeight / 2.0f)
                    }
                    flip.mapRect(boundingBox)
                }
                val result = Recognition(
                    0, label, confidence, boundingBox
                )
                result.color = color
                result.location = boundingBox
                result.extra = extra
                result.crop = crop
                mappedRecognitions.add(result)
            }
        }

        updateResults(currTimestamp, mappedRecognitions)
    }
}