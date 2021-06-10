package kh.farrukh.facerecognition.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kh.farrukh.facerecognition.database.Recognition
import kh.farrukh.facerecognition.databinding.ViewHolderUserBinding

/**
 *Created by farrukh_kh on 6/9/21 10:56 PM
 *kh.farrukh.facerecognition.ui.admin
 **/
class UserAdapter(private val onDeleteClick: (Recognition) -> Unit) :
    ListAdapter<Recognition, UserAdapter.UserViewHolder>(object :
        DiffUtil.ItemCallback<Recognition>() {
        override fun areItemsTheSame(oldItem: Recognition, newItem: Recognition) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Recognition, newItem: Recognition) =
            oldItem == newItem
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = UserViewHolder(
        ViewHolderUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.onUserBind(getItem(position))
    }

    inner class UserViewHolder(private val binding: ViewHolderUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.imvDelete.setOnClickListener { onDeleteClick.invoke(getItem(adapterPosition)) }
        }

        fun onUserBind(user: Recognition) = with(binding) {
            tvName.text = user.title.trim()
        }
    }
}