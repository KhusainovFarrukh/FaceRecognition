package kh.farrukh.facerecognition.utils

import android.util.SparseIntArray
import android.view.Surface
import android.widget.Toast
import kh.farrukh.facerecognition.ui.CameraFragment

/**
 *Created by farrukh_kh on 6/8/21 5:32 PM
 *kh.farrukh.facerecognition.utils
 **/
object Utils {

    val ORIENTATIONS = SparseIntArray().let {
        it.append(Surface.ROTATION_0, 90)
        it.append(Surface.ROTATION_90, 0)
        it.append(Surface.ROTATION_180, 270)
        it.append(Surface.ROTATION_270, 180)
    }

    fun CameraFragment.showToast(text: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
        }
    }
}