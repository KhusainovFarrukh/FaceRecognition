package kh.farrukh.facerecognition.database

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.room.TypeConverter
import org.json.JSONArray

/**
 *Created by farrukh_kh on 6/9/21 3:46 PM
 *kh.farrukh.facerecognition.database
 **/
object CustomTypeConverters {

    @TypeConverter
    fun rectFToString(rectF: RectF?): String? {
        var string: String? = null
        rectF?.let {
            val jsonArray = JSONArray()
            jsonArray.put(rectF.left)
            jsonArray.put(rectF.top)
            jsonArray.put(rectF.right)
            jsonArray.put(rectF.bottom)
            string = jsonArray.toString()
        }
        return string
    }

    @TypeConverter
    fun stringToRectF(string: String?): RectF? {
        var rectF: RectF? = null
        string?.let {
            val jsonArray = JSONArray(string)
            rectF = RectF(
                (jsonArray.get(0) as Int).toFloat(),
                (jsonArray.get(1) as Int).toFloat(),
                (jsonArray.get(2) as Int).toFloat(),
                (jsonArray.get(3) as Int).toFloat(),
            )
        }
        return rectF
    }

    @TypeConverter
    fun extraToString(extra: Any?): String? {
        var string: String? = null
        extra?.let {
            val extraAsArray = extra as Array<FloatArray>
            string = extraAsArray.contentDeepToString()
        }
        Log.e("TAG", "extraToString: $string")
        return string
    }

    @TypeConverter
    fun stringToExtra(string: String?): Any? {
        var extraAsArray: Array<FloatArray>? = null
        string?.let {
            val listOfFloat = string.substring(2, string.length - 2).split(",")
            extraAsArray = Array(size = 1) {
                val floatArray = FloatArray(size = listOfFloat.size) { innerIt ->
                    listOfFloat[innerIt].toFloat()
                }
                floatArray
            }
        }
        return extraAsArray
    }

    @TypeConverter
    fun bitmapToNull(bitmap: Bitmap?): String? = null

    @TypeConverter
    fun nullToBitmap(string: String?): Bitmap? = null
}