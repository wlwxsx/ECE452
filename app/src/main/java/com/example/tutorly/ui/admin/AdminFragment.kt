package com.example.tutorly.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorly.R
import com.example.tutorly.User
import com.example.tutorly.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class AdminFragment : Fragment() {

    private var _binding: com.example.tutorly.databinding.FragmentAdminBinding? = null
    private val binding get() = _binding!!
    private val userRepository = UserRepository.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var userAdapter: AdminUserAdapter
    private lateinit var postAdapter: AdminPostAdapter
    private val users = mutableListOf<User>()
    private val posts = mutableListOf<Map<String, Any>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = com.example.tutorly.databinding.FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerViews()
        setupButtons()
        loadData()
    }

    private fun setupRecyclerViews() {
        // Setup users recycler view
        userAdapter = AdminUserAdapter(users) { user, action ->
            when (action) {
                AdminUserAdapter.Action.BAN -> banUser(user)
                AdminUserAdapter.Action.UNBAN -> unbanUser(user)
            }
        }
        
        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userAdapter
        }

        // Setup posts recycler view
        postAdapter = AdminPostAdapter(posts) { postId, action ->
            when (action) {
                AdminPostAdapter.Action.DELETE -> deletePost(postId)
            }
        }
        
        binding.postsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
        }
    }

    private fun setupButtons() {
        binding.refreshButton.setOnClickListener {
            loadData()
        }
    }

    private fun loadData() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(context, "You must be logged in", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Check if user is admin
            val isAdmin = userRepository.isUserAdmin(currentUserId).getOrNull() ?: false
            if (!isAdmin) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Access denied. Admin privileges required.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // Load users
            val usersResult = userRepository.getAllUsers()
            withContext(Dispatchers.Main) {
                if (usersResult.isSuccess) {
                    users.clear()
                    users.addAll(usersResult.getOrNull() ?: emptyList())
                    userAdapter.notifyDataSetChanged()
                }
            }

            // Load posts
            try {
                val postsSnapshot = db.collection("posts").get().await()
                val postsList = postsSnapshot.documents.mapNotNull { it.data }
                withContext(Dispatchers.Main) {
                    posts.clear()
                    posts.addAll(postsList)
                    postAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading posts: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun banUser(user: User) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) return

        CoroutineScope(Dispatchers.IO).launch {
            val result = userRepository.banUser(user.userid, currentUserId)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(context, "User banned successfully", Toast.LENGTH_SHORT).show()
                    loadData() // Refresh the list
                } else {
                    Toast.makeText(context, "Failed to ban user: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun unbanUser(user: User) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) return

        CoroutineScope(Dispatchers.IO).launch {
            val result = userRepository.unbanUser(user.userid, currentUserId)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(context, "User unbanned successfully", Toast.LENGTH_SHORT).show()
                    loadData() // Refresh the list
                } else {
                    Toast.makeText(context, "Failed to unban user: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deletePost(postId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Delete comments first
                val commentsSnapshot = db.collection("comments")
                    .whereEqualTo("postId", postId)
                    .get()
                    .await()

                val batch = db.batch()
                for (document in commentsSnapshot.documents) {
                    batch.delete(document.reference)
                }

                // Delete the post
                batch.delete(db.collection("posts").document(postId))
                batch.commit().await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Post deleted successfully", Toast.LENGTH_SHORT).show()
                    loadData() // Refresh the list
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to delete post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 