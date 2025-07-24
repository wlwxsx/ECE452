package com.example.tutorly.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorly.R
import com.example.tutorly.ui.posts.Post
import com.google.firebase.auth.FirebaseAuth
import com.example.tutorly.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PostAdapter(private val posts: List<Post>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val userRepository = UserRepository.getInstance()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val postTitle: TextView = itemView.findViewById(R.id.post_title)
        val posterInfo: TextView = itemView.findViewById(R.id.poster_info)
        val courseInfo: TextView = itemView.findViewById(R.id.course_info)
        val roleInfo: TextView = itemView.findViewById(R.id.role_info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        holder.postTitle.text = post.title

        if (post.posterId.isNotBlank()) {
            holder.posterInfo.text = "Loading..."
            coroutineScope.launch {
                val userResult = withContext(Dispatchers.IO) { userRepository.getUserById(post.posterId) }
                val user = userResult.getOrNull()
                if (user != null && user.name.isNotBlank()) {
                    holder.posterInfo.text = "Posted by: ${user.name}"
                } else {
                    holder.posterInfo.text = "Posted by: ${post.posterId.take(6)}..."
                }
            }
        } else {
            holder.posterInfo.text = "Posted by: Anonymous"
        }

        holder.courseInfo.text = "${post.courseName.uppercase()} ${post.courseCode}"

        // Show badges based on post status
        if (post.status == Post.STATUS_CLOSED && currentUserId == post.posterId) {
            holder.roleInfo.text = "Closed"
            holder.roleInfo.setBackgroundColor(android.graphics.Color.parseColor("#FF0000"))
        } else if (post.status == Post.STATUS_MATCHED) {
            holder.roleInfo.text = "Matched"
            holder.roleInfo.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")) // Green
        } else {
            holder.roleInfo.text = post.role.replaceFirstChar { it.uppercase() }.lowercase().replaceFirstChar { it.uppercase() }
            holder.roleInfo.setBackgroundColor(android.graphics.Color.parseColor("#005A9C"))
        }

        holder.itemView.setOnClickListener {
            val bundle = bundleOf("postId" to post.id)
            it.findNavController().navigate(R.id.action_navigation_dashboard_to_postDetailFragment, bundle)
        }
    }

    override fun getItemCount() = posts.size
} 