package kh.farrukh.facerecognition.tflite

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Pair
import kh.farrukh.facerecognition.database.AppDatabase
import kh.farrukh.facerecognition.database.Recognition
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.math.sqrt

/**
 *Created by farrukh_kh on 6/8/21 1:12 PM
 *kh.farrukh.facerecognition.tflite
 **/
class TFLiteFaceRecognitionModel : SimilarityClassifier {

    companion object {
        const val OUTPUT_SIZE = 192
        const val IMAGE_MEAN = 128.0f
        const val IMAGE_STD = 128.0f
        const val NUM_THREADS = 4

        @Throws(IOException::class)
        fun create(
            assetManager: AssetManager,
            modelFilename: String,
            labelFilename: String,
            inputSize: Int,
            isQuantized: Boolean,
            context: Context
        ): SimilarityClassifier {
            val model = TFLiteFaceRecognitionModel()
            model.database = AppDatabase.getDatabase(context)
            val actualFilename = labelFilename.split("file:///android_asset/").toTypedArray()[1]
            val labelsInput = assetManager.open(actualFilename)
            val br = BufferedReader(InputStreamReader(labelsInput))
            var line: String?
            while (br.readLine().also { line = it } != null) {
//                Log.e("create", "$line")
                model.labels.add(line)
            }
            br.close()
            model.inputSize = inputSize
            try {
                model.tfLite = Interpreter(
                    loadModelFile(
                        assetManager,
                        modelFilename
                    ), Interpreter.Options().apply { setNumThreads(NUM_THREADS) }
//                    ), Interpreter.Options()

                )
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
            model.isModelQuantized = isQuantized
            val numBytesPerChannel = if (isQuantized) 1 else 4

            model.imgData =
                ByteBuffer.allocateDirect(1 * model.inputSize * model.inputSize * 3 * numBytesPerChannel)
            model.imgData.let { it?.order(ByteOrder.nativeOrder()) }
            model.intValues = IntArray(model.inputSize * model.inputSize)
            return model
        }

        @Throws(IOException::class)
        private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
            val fileDescriptor = assets.openFd(modelFilename)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    private var database: AppDatabase? = null
    private var isModelQuantized = false
    private var inputSize = 0
    private val labels = Vector<String>()
    private var intValues = intArrayOf()
    private lateinit var embeddings: Array<FloatArray>
    private var imgData: ByteBuffer? = null
    private lateinit var tfLite: Interpreter

    private fun findNearest(emb: FloatArray): Pair<String, Float>? {
        var ret: Pair<String, Float>? = null
        for (face in database!!.getFacesDao().getAll()) {
            val knownEmb = (face.extra as Array<FloatArray>)[0]
            var distance = 0f
            for (i in emb.indices) {
                val diff = emb[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = sqrt(distance.toDouble()).toFloat()
            if (ret == null || distance < ret.second) {
                ret = Pair(face.title, distance)
            }
        }
        return ret
    }

    override fun register(recognition: Recognition) {
        database!!.getFacesDao().insertFace(recognition)
    }

    override fun recognizeImage(bitmap: Bitmap, getExtra: Boolean): List<Recognition?> {
//        Trace.beginSection("recognizeImage")
//        Trace.beginSection("preprocessBitmap")
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        imgData!!.rewind()
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                if (isModelQuantized) {
                    imgData!!.put((pixelValue shr 16 and 0xFF).toByte())
                    imgData!!.put((pixelValue shr 8 and 0xFF).toByte())
                    imgData!!.put((pixelValue and 0xFF).toByte())
                } else {
                    imgData!!.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData!!.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData!!.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }

//        Trace.endSection()
//        Trace.beginSection("feed")
        val inputArray = arrayOf<Any>(imgData!!)
//        Trace.endSection()
        val outputMap: MutableMap<Int, Any> = HashMap()
        embeddings =
            Array(1) { FloatArray(OUTPUT_SIZE) }
        outputMap[0] = embeddings
//        Trace.beginSection("run")
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap)
//        Trace.endSection()

        var distance = Float.MAX_VALUE
        val id = 0
        var label = "?"

        if (database!!.getFacesDao().getAll().isNotEmpty()) {
            val nearest = findNearest(embeddings[0])
            if (nearest != null) {
                val name = nearest.first
                label = name
                distance = nearest.second
//                Log.e("findNearest", "nearest: $name - distance: $distance")
            }
        }

        val numDetectionsOutput = 1
        val recognitions = ArrayList<Recognition>(numDetectionsOutput)
        val rec = Recognition(
            id,
            label,
            distance,
            RectF()
        )

        recognitions.add(rec)
        if (getExtra) {
            rec.extra = embeddings
        }
//        Trace.endSection()
        return recognitions
    }

    override fun enableStatLogging(debug: Boolean) {
    }

    override fun getStatString() = ""

    override fun close() {
    }

//    @Deprecated("Be aware!")
//    override fun setNumThreads(numThreads: Int) {
//        tfLite.setNumThreads(NUM_THREADS)
//    }
}