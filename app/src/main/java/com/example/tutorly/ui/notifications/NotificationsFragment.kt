package com.example.tutorly.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.tutorly.databinding.FragmentNotificationsBinding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorly.UserRepository
import com.example.tutorly.ui.posts.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.tutorly.R

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var userRepository: UserRepository
    private lateinit var matchAdapter: MatchAdapter
    private val matchList = mutableListOf<MatchEntry>()
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        db = FirebaseFirestore.getInstance()
        userRepository = UserRepository.getInstance()
        setupRecyclerView()
        fetchMatches()
        return binding.root
    }

    private fun setupRecyclerView() {
        matchAdapter = MatchAdapter(matchList)
        binding.matchRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = matchAdapter
        }
    }

    private fun fetchMatches() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("posts")
            .whereEqualTo("status", Post.STATUS_MATCHED)
            .get()
            .addOnSuccessListener { result ->
                val posts = result.documents.mapNotNull { it.toObject(Post::class.java) }
                val relevantPosts = posts.filter {
                    (it.posterId == currentUserId || it.matchedId == currentUserId)
                }
                if (relevantPosts.isEmpty()) {
                    matchList.clear()
                    matchAdapter.notifyDataSetChanged()
                    binding.textNotifications.text = "No matches yet."
                    return@addOnSuccessListener
                }
                uiScope.launch {
                    val entries = relevantPosts.mapNotNull { post ->
                        val otherUserId = if (post.posterId == currentUserId) post.matchedId else post.posterId
                        if (otherUserId.isBlank()) return@mapNotNull null
                        val userResult = withContext(Dispatchers.IO) { userRepository.getUserById(otherUserId) }
                        val user = userResult.getOrNull() ?: return@mapNotNull null
                        val courseDisplay = (post.courseName + " " + post.courseCode).trim()
                        MatchEntry(user.name, user.email, courseDisplay, post.id)
                    }
                    matchList.clear()
                    matchList.addAll(entries)
                    matchAdapter.notifyDataSetChanged()
                    binding.textNotifications.text = if (entries.isEmpty()) "No matches yet." else "MY MATCHES"
                }
            }
            .addOnFailureListener {
                binding.textNotifications.text = "Failed to load matches."
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        job.cancel()
    }
}

// Data class for match entry
private data class MatchEntry(val name: String, val email: String, val course: String, val postId: String)

// Adapter for displaying matches
private class MatchAdapter(private val matches: List<MatchEntry>) : RecyclerView.Adapter<MatchAdapter.MatchViewHolder>() {
    class MatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.match_name)
        val email: TextView = itemView.findViewById(R.id.match_email)
        val courseCode: TextView = itemView.findViewById(R.id.match_course_code)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_match, parent, false)
        return MatchViewHolder(view)
    }
    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        val match = matches[position]
        holder.name.text = match.name
        holder.email.text = match.email
        holder.courseCode.text = match.course
        holder.itemView.setOnClickListener {
            val navController = androidx.navigation.Navigation.findNavController(holder.itemView)
            val bundle = androidx.core.os.bundleOf("postId" to match.postId)
            navController.navigate(com.example.tutorly.R.id.postDetailFragment, bundle)
        }
    }
    override fun getItemCount() = matches.size
}