package com.example.tutorly.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorly.R
import java.text.SimpleDateFormat
import java.util.*

class AdminPostAdapter(
    private val posts: List<Map<String, Any>>,
    private val onActionClick: (String, Action) -> Unit
) : RecyclerView.Adapter<AdminPostAdapter.PostViewHolder>() {

    enum class Action {
        DELETE
    }

    class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val postTitle: TextView = view.findViewById(R.id.post_title)
        val postAuthor: TextView = view.findViewById(R.id.post_author)
        val postDate: TextView = view.findViewById(R.id.post_date)
        val deleteButton: Button = view.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        val postId = post["postId"] as? String ?: ""
        
        holder.postTitle.text = post["title"] as? String ?: "No Title"
        holder.postAuthor.text = "By: ${post["posterId"] as? String ?: "Unknown"}"
        
        // Format date
        val timestamp = post["timeStamp"] as? com.google.firebase.Timestamp
        val dateText = if (timestamp != null) {
            val sdf = SimpleDateFormat("MM/dd/yyyy - h:mm a", Locale.getDefault())
            sdf.format(timestamp.toDate())
        } else {
            "Unknown Date"
        }
        holder.postDate.text = dateText

        holder.deleteButton.setOnClickListener {
            onActionClick(postId, Action.DELETE)
        }
    }

    override fun getItemCount() = posts.size
} 