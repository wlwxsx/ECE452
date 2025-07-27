package com.example.tutorly.ui.notifications

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.core.os.bundleOf
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
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
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                val posts = result.documents.mapNotNull { doc ->
                    doc.toObject(Post::class.java)?.apply {
                        scheduledTime = doc.getDate("scheduledTime") // Safe even if field doesn't exist yet
                    }
                }
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
                        MatchEntry(user.name, user.email, courseDisplay, post.id, post.scheduledTime, post.posterId)
                    }
                    val oldSize = matchList.size
                    matchList.clear()
                    matchList.addAll(entries)
                    matchAdapter.notifyItemRangeInserted(0, matchList.size)
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
private data class MatchEntry(
    val name: String,
    val email: String,
    val course: String,
    val postId: String,
    val scheduledTime: java.util.Date? = null, // Add this
    val posterId: String
)

// Adapter for displaying matches
private class MatchAdapter(private val matches: List<MatchEntry>) : RecyclerView.Adapter<MatchAdapter.MatchViewHolder>() {
    class MatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.match_name)
        val email: TextView = itemView.findViewById(R.id.match_email)
        val courseCode: TextView = itemView.findViewById(R.id.match_course_code)
        val scheduleButton: Button = itemView.findViewById(R.id.schedule_button)
        val setTime: TextView = itemView.findViewById(R.id.match_schedule)
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


        val context = holder.itemView.context
        val isAlreadyScheduled = match.scheduledTime != null
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val isPostOwner = match.posterId == currentUserId

        holder.scheduleButton.apply {
            if (isAlreadyScheduled) {
                Log.d("DEBUG", "The match is already scheduled at: ${match.scheduledTime} in postID: ${match.postId}")
                text = context.getString(R.string.scheduled)
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                isEnabled = false
                val dtf = SimpleDateFormat("MM/dd/yyyy - h:mm a", Locale.getDefault()).format(match.scheduledTime)
                holder.setTime.text = dtf
            } else if (isPostOwner) {
                // Only post owners can schedule
                isEnabled = true
                text = context.getString(R.string.schedule)
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))

                setOnClickListener {
                    val now = Calendar.getInstance()

                    // Date Picker
                    val datePicker = DatePickerDialog(context, { _, year, month, day ->
                        // Time Picker
                        val timePicker = TimePickerDialog(context, { _, hour, minute ->
                            val selectedDateTime = Calendar.getInstance().apply {
                                set(year, month, day, hour, minute)
                            }
                            Log.d("DEBUG", "time selected: ${selectedDateTime.time}")
                            if (selectedDateTime.after(Calendar.getInstance())) {
                                Log.d("DEBUG", "Great the time is in the future")
                                val scheduledTime = selectedDateTime.time
                                val db = FirebaseFirestore.getInstance()
                                db.collection("posts")
                                    .document(match.postId)
                                    .update("scheduledTime", scheduledTime)
                                    .addOnSuccessListener {
                                        Log.d("FIRESTORE", "Attempting to update post ID: ${match.postId}")
                                        Log.d("FIRESTORE", "scheduledTime value: $scheduledTime")
                                        Toast.makeText(context, "Time scheduled!", Toast.LENGTH_SHORT).show()
                                        text = context.getString(R.string.scheduled)
                                        setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                                        isEnabled = false

                                    }
                                    .addOnFailureListener {
                                        Log.e("Firestore", "Error saving scheduled time: ${it.message}")
                                        Toast.makeText(context, "Failed to save schedule. Try again.", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                Log.d("DEBUG", "Nooo the time is in the past")
                                Toast.makeText(context, "Please select a future time.", Toast.LENGTH_SHORT).show()
                            }
                        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true)

                        timePicker.show()
                    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))

                    datePicker.datePicker.minDate = now.timeInMillis
                    datePicker.show()
                }
            } else {
                // Not the post owner → button disabled
                Log.d("DEBUG", "Not the post owner ID: you are $currentUserId, the owner is ${match.posterId}")
                text = context.getString(R.string.schedule)
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                isEnabled = false
            }
        }

        holder.itemView.setOnClickListener {
            val navController = holder.itemView.findNavController()
            val bundle = bundleOf("postId" to match.postId)
            navController.navigate(R.id.postDetailFragment, bundle)
        }
    }
    override fun getItemCount() = matches.size
}