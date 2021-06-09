package kh.farrukh.facerecognition.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 *Created by farrukh_kh on 6/9/21 3:30 PM
 *kh.farrukh.facerecognition.database
 **/
@Dao
interface FacesDao {
    @Query("SELECT * FROM REGISTERED_FACES")
    fun getAll(): List<Recognition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFace(recognition: Recognition)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFaces(faces: List<Recognition>)
}