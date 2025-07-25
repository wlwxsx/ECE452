package com.example.tutorly.ui.posts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorly.R
import com.example.tutorly.UserRepository
import com.example.tutorly.ui.profile.ProfilePreviewFragment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentAdapter(
    private val comments: List<Map<String, Any>>,
    private val userRepository: UserRepository,
    private val currentUserId: String?,
    private val postOwnerId: String?,
    private val postStatus: String?,
    private val onMatchClick: (String) -> Unit,
    private val parentFragmentManager: FragmentManager
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val commentAuthor: TextView = itemView.findViewById(R.id.comment_author)
        val commentTimestamp: TextView = itemView.findViewById(R.id.comment_timestamp)
        val commentContent: TextView = itemView.findViewById(R.id.comment_content)
        val matchButton: Button = itemView.findViewById(R.id.match_button)

        fun profilePreview(parentFragmentManager: FragmentManager, userId: String, currentUserId: String?) {
            commentAuthor.setOnClickListener {
                val dialog = ProfilePreviewFragment().apply {
                    arguments = Bundle().apply {
                        putString("userId", userId)
                        putString("reporterUserId", currentUserId)
                    }
                }
                dialog.show(parentFragmentManager, "profile_preview")
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val commentData = comments[position]
        
        // Extract data directly from Firebase document
        val content = commentData["content"] as? String ?: ""
        val userId = commentData["userId"] as? String ?: ""
        val timeStamp = commentData["timeStamp"] as? Timestamp
        
        // Display comment content
        holder.commentContent.text = content

        // Display timestamp in a relative format
        timeStamp?.let { timestamp ->
            holder.commentTimestamp.text = formatRelativeTime(timestamp.toDate())
        } ?: run {
            holder.commentTimestamp.text = "Just now"
        }

        // Show match button only if current user is the post owner, this isn't their own comment, and post is still open
        val isPostOwner = currentUserId == postOwnerId
        val isOwnComment = currentUserId == userId
        val isPostOpen = postStatus == Post.STATUS_ACTIVE || postStatus.isNullOrBlank()

        if (isPostOwner && !isOwnComment && userId.isNotBlank() && isPostOpen) {
            holder.matchButton.visibility = View.VISIBLE
            holder.matchButton.setOnClickListener {
                onMatchClick(userId)
            }
        } else {
            holder.matchButton.visibility = View.GONE
        }

        // Fetch and display user name
        if (userId.isNotBlank()) {
            // Show loading placeholder
            holder.commentAuthor.text = "Loading..."
            
            // Fetch user data asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                userRepository.getUserById(userId)
                    .onSuccess { user ->
                        withContext(Dispatchers.Main) {
                            if (user != null && user.name.isNotBlank()) {
                                holder.commentAuthor.text = user.name
                            } else {
                                holder.commentAuthor.text = "User_${userId.take(6)}"
                            }
                        }
                    }
                    .onFailure {
                        withContext(Dispatchers.Main) {
                            holder.commentAuthor.text = "User_${userId.take(6)}"
                        }
                    }
            }
        } else {
            holder.commentAuthor.text = "Anonymous"
        }
        holder.profilePreview(parentFragmentManager, userId, currentUserId)
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