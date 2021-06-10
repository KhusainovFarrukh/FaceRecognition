package kh.farrukh.facerecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kh.farrukh.facerecognition.databinding.ActivityMainBinding
import kh.farrukh.facerecognition.ui.admin.AdminActivity
import kh.farrukh.facerecognition.ui.UserActivity

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    companion object {
        const val CAMERA_REQUEST = Manifest.permission.CAMERA
        const val REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (hasPermission()) {
            setOnClickListeners()
        } else {
            requestPermissions()
        }
    }

    private fun setOnClickListeners() = with(binding) {
        btnUser.setOnClickListener {
            startActivity(Intent(this@MainActivity, UserActivity::class.java))
        }
        btnAdmin.setOnClickListener {
            startActivity(Intent(this@MainActivity, AdminActivity::class.java))
        }
    }

    private fun hasPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        checkSelfPermission(CAMERA_REQUEST) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(CAMERA_REQUEST)) {
                Toast.makeText(
                    this,
                    "Camera permission is required for Face Recognition functionality",
                    Toast.LENGTH_LONG
                ).show()
            }
            requestPermissions(
                arrayOf(CAMERA_REQUEST),
                REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (allPermissionsGranted(grantResults)
            ) {
                setOnClickListeners()
            } else {
                requestPermissions()
            }
        }
    }

    private fun allPermissionsGranted(grantResults: IntArray) : Boolean {
        grantResults.forEach { result ->
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
}