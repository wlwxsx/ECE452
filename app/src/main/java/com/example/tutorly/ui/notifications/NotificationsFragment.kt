package com.example.tutorly.ui.notifications


import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
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
import androidx.fragment.app.FragmentActivity
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
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
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

    fun fetchMatches() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("posts")
            .whereEqualTo("status", Post.STATUS_MATCHED)
            .get()
            .addOnSuccessListener { result ->
                val posts = result.documents.mapNotNull { doc ->
                    val post = doc.toObject(Post::class.java)
                    post?.copy(
                        ownerScheduledTimes = (doc["ownerScheduledTimes"] as? List<*>)
                            ?.mapNotNull { (it as? com.google.firebase.Timestamp)?.toDate() },
                        finalScheduledTime = (doc["finalScheduledTime"] as? Timestamp)?.toDate(),
                        isTimeRejected = doc["isTimeRejected"] as? Boolean
                    )
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

                        MatchEntry(
                            name = user.name,
                            email = user.email,
                            course = courseDisplay,
                            postId = post.id,
                            posterId = post.posterId,
                            ownerScheduledTimes = post.ownerScheduledTimes,
                            finalScheduledTime = post.finalScheduledTime,
                            isTimeRejected = post.isTimeRejected
                        )
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

private data class MatchEntry(
    val name: String,
    val email: String,
    val course: String,
    val postId: String,
    val ownerScheduledTimes: List<Date>? = null,
    val finalScheduledTime: Date? = null,
    val isTimeRejected: Boolean? = null,
    val posterId: String
)
private fun showTimeSelectionDialog(
    context: Context,
    times: List<Date>,
    onResult: (Date?) -> Unit
) {
    val options = times.map {
        SimpleDateFormat("MM/dd/yyyy - h:mm a", Locale.getDefault()).format(it)
    }.toMutableList()
    options.add("None work for me")

    var selectedIndex = -1

    val builder = AlertDialog.Builder(context)
    builder.setTitle("Pick a time")
        .setSingleChoiceItems(options.toTypedArray(), -1) { _, which ->
            selectedIndex = which
        }
        .setPositiveButton("OK", null) // We'll override later
        .setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

    val dialog = builder.create()

    dialog.setOnShowListener {
        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        // Style buttons
        okButton?.setTextColor(android.graphics.Color.BLACK)
        cancelButton?.setTextColor(android.graphics.Color.BLACK)

        // Set safe click listener on OK button
        okButton.setOnClickListener {
            if (selectedIndex == -1) {
                Toast.makeText(context, "Please select a time.", Toast.LENGTH_SHORT).show()
            } else {
                val selected = if (selectedIndex == options.lastIndex) null else times.getOrNull(selectedIndex)
                onResult(selected)
                dialog.dismiss()
            }
        }
    }

    dialog.show()
}


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
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val isPostOwner = match.posterId == currentUserId
        val hasFinalTime = match.finalScheduledTime != null || (match.isTimeRejected ?: false)
        val hasOwnerTimes = !match.ownerScheduledTimes.isNullOrEmpty()

        holder.setTime.text = when {
            match.finalScheduledTime != null -> {
                val formatted = SimpleDateFormat("MM/dd/yyyy - h:mm a", Locale.getDefault()).format(match.finalScheduledTime)
                "✔ Scheduled: $formatted"
            }
            match.isTimeRejected == true -> "✘ Rejected: Contact them for a new time"
            else -> "⏳ Not scheduled yet"
        }

        holder.scheduleButton.apply {
            when {
                hasFinalTime -> {
                    text = context.getString(R.string.completed)
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    isEnabled = false
                }

                isPostOwner && !hasOwnerTimes -> {
                    text = "Schedule"
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                    isEnabled = true

                    setOnClickListener {
                        showSchedulePickerDialog(
                            context = context,
                            onDone = { selectedTimes ->
                                if (selectedTimes.isEmpty()) {
                                    Toast.makeText(context, "Please select at least one time.", Toast.LENGTH_SHORT).show()
                                    return@showSchedulePickerDialog
                                }

                                val db = FirebaseFirestore.getInstance()
                                db.collection("posts").document(match.postId)
                                    .update("ownerScheduledTimes", selectedTimes)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Times saved!", Toast.LENGTH_SHORT).show()

                                        // Update button UI immediately
                                        holder.scheduleButton.text = "WAITING"
                                        holder.scheduleButton.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                                        holder.scheduleButton.isEnabled = false

                                        // Refresh the fragment
                                        (context as? FragmentActivity)?.supportFragmentManager?.fragments
                                            ?.filterIsInstance<NotificationsFragment>()
                                            ?.firstOrNull()
                                            ?.fetchMatches()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(context, "Failed to save times", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            onCancel = {
                                Toast.makeText(context, "Cancelled schedule selection", Toast.LENGTH_SHORT).show()
                            }
                        )

                    }
                }

                !isPostOwner && hasOwnerTimes -> {
                    text = "Select"
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_blue_light))
                    isEnabled = true

                    setOnClickListener {
                        showTimeSelectionDialog(context, match.ownerScheduledTimes!!) { selectedTime ->
                            val db = FirebaseFirestore.getInstance()
                            val updates = if (selectedTime != null) {
                                mapOf(
                                    "finalScheduledTime" to selectedTime,
                                    "isTimeRejected" to false
                                )
                            } else {
                                mapOf(
                                    "finalScheduledTime" to null,
                                    "isTimeRejected" to true
                                )
                            }

                            db.collection("posts").document(match.postId)
                                .update(updates)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Time selection saved!", Toast.LENGTH_SHORT).show()

                                    // Immediately update UI
                                    text = context.getString(R.string.completed)
                                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                                    isEnabled = false

                                    // Optionally update the schedule label (without full refresh)
                                    holder.setTime.text = when {
                                        selectedTime != null -> {
                                            val formatted = SimpleDateFormat("MM/dd/yyyy - h:mm a", Locale.getDefault()).format(selectedTime)
                                            "✔ Scheduled: $formatted"
                                        }
                                        else -> "✘ Rejected: Contact them for a new time"
                                    }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Failed to set time", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }

                else -> {
                    text = "Waiting"
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    isEnabled = false
                }
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
