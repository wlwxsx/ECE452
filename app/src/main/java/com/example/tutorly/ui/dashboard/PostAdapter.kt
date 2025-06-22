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

class PostAdapter(private val posts: List<Post>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

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
        holder.postTitle.text = post.title

        if (post.posterId.isNotBlank()) {
            holder.posterInfo.text = "Posted by: ${post.posterId.take(6)}..."
        } else {
            holder.posterInfo.text = "Posted by: Anonymous"
        }

        holder.courseInfo.text = "${post.courseName.uppercase()} ${post.courseCode}"
        holder.roleInfo.text = post.role.replaceFirstChar { it.uppercase() }

        holder.itemView.setOnClickListener {
            val bundle = bundleOf("postId" to post.id)
            it.findNavController().navigate(R.id.action_navigation_dashboard_to_postDetailFragment, bundle)
        }
    }

    override fun getItemCount() = posts.size
} 