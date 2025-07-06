package com.example.tutorly.ui.posts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorly.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class CommentAdapter(private val comments: List<Comment>) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val commentAuthor: TextView = itemView.findViewById(R.id.comment_author)
        val commentTimestamp: TextView = itemView.findViewById(R.id.comment_timestamp)
        val commentContent: TextView = itemView.findViewById(R.id.comment_content)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        
        // Display author info (shortened user ID)
        if (comment.userId.isNotBlank()) {
            holder.commentAuthor.text = "User_${comment.userId.take(6)}"
        } else {
            holder.commentAuthor.text = "Anonymous"
        }

        // Display comment content
        holder.commentContent.text = comment.content

        // Display timestamp in a relative format
        comment.timeStamp?.let { timestamp ->
            holder.commentTimestamp.text = formatRelativeTime(timestamp)
        } ?: run {
            holder.commentTimestamp.text = "Just now"
        }
    }

    override fun getItemCount() = comments.size

    private fun formatRelativeTime(timestamp: Date): String {
        val now = Date()
        val diffInMillis = now.time - timestamp.time
        val diffInSeconds = diffInMillis / 1000
        val diffInMinutes = diffInSeconds / 60
        val diffInHours = diffInMinutes / 60
        val diffInDays = diffInHours / 24

        return when {
            diffInSeconds < 60 -> "Just now"
            diffInMinutes < 60 -> "${diffInMinutes}m ago"
            diffInHours < 24 -> "${diffInHours}h ago"
            diffInDays < 7 -> "${diffInDays}d ago"
            else -> {
                val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                sdf.format(timestamp)
            }
        }
    }
} 