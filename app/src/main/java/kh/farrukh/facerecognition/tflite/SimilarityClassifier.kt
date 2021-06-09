package kh.farrukh.facerecognition.tflite

import android.graphics.Bitmap
import kh.farrukh.facerecognition.database.Recognition

/**
 *Created by farrukh_kh on 6/8/21 1:01 PM
 *kh.farrukh.facerecognition.tflite
 **/
interface SimilarityClassifier {

    fun register(recognition: Recognition)

    fun recognizeImage(bitmap: Bitmap, getExtra: Boolean): List<Recognition?>

    fun enableStatLogging(debug: Boolean)

    fun getStatString(): String

    fun close()

//    fun setNumThreads(numThreads: Int)
}