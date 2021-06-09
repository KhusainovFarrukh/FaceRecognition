package kh.farrukh.facerecognition.database

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 *Created by farrukh_kh on 6/8/21 1:01 PM
 *kh.farrukh.facerecognition.tflite
 **/
@Entity(tableName = "registered_faces")
data class Recognition(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "distance")
    val distance: Float,
    @ColumnInfo(name = "location")
    var location: RectF?,
    @ColumnInfo(name = "color")
    var color: Int? = null,
    @ColumnInfo(name = "extra")
    var extra: Any? = null,
    @ColumnInfo(name = "crop")
    var crop: Bitmap? = null
)
