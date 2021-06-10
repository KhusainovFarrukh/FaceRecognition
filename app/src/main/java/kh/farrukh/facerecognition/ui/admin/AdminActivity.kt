package kh.farrukh.facerecognition.ui.admin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kh.farrukh.facerecognition.database.AppDatabase
import kh.farrukh.facerecognition.database.Recognition
import kh.farrukh.facerecognition.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {

    private val appDatabase by lazy { AppDatabase.getDatabase(applicationContext) }
    private val binding by lazy { ActivityAdminBinding.inflate(layoutInflater) }
    private val userAdapter by lazy { UserAdapter(this::onDeleteClick) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setOnClickListeners()
        binding.rvUsers.adapter = userAdapter
        userAdapter.submitList(appDatabase.getFacesDao().getAll())
    }

    private fun setOnClickListeners() = with(binding) {
        fabAdd.setOnClickListener {
            startActivity(Intent(this@AdminActivity, AddUserActivity::class.java))
        }
    }

    private fun onDeleteClick(face: Recognition) {
        appDatabase.getFacesDao().deleteFace(face)
        userAdapter.submitList(appDatabase.getFacesDao().getAll())
    }

    override fun onResume() {
        super.onResume()
        userAdapter.submitList(appDatabase.getFacesDao().getAll())
    }
}