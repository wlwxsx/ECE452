package com.example.tutorly.ui.notifications


import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.core.os.bundleOf
import android.icu.util.Calendar
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
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
            .whereIn("status", listOf(Post.STATUS_MATCHED, Post.STATUS_CLOSED))
            .get()
            .addOnSuccessListener { result ->
                val posts = result.documents.mapNotNull { doc ->
                    val raw = doc.toObject(Post::class.java)
                    if (raw != null) {
                        val ownerTimes = (doc["ownerScheduledTimes"] as? List<*>)
                            ?.mapNotNull { (it as? Timestamp)?.toDate() }

                        val finalTime = (doc["finalScheduledTime"] as? Timestamp)?.toDate()
                        val isRejected = doc["isTimeRejected"] as? Boolean
                        val timestamp = (doc["timestamp"] as? Timestamp)?.toDate()

                        raw.copy(
                            ownerScheduledTimes = ownerTimes,
                            finalScheduledTime = finalTime,
                            isTimeRejected = isRejected,
                            timeStamp = timestamp // Now safely overrides timestamp here
                        )
                    } else null
                }


                val relevantPosts = posts.filter {
                    it.posterId == currentUserId || it.matchedId == currentUserId
                }

                if (relevantPosts.isEmpty()) {
                    matchList.clear()
                    matchAdapter.notifyDataSetChanged()
                    binding.textNotifications.text = "No matches yet."
                    return@addOnSuccessListener
                }

                // Sort: matched first, then closed; newest to oldest
                val sortedPosts = relevantPosts.sortedWith(
                    compareBy<Post> { it.status != Post.STATUS_MATCHED }
                        .thenByDescending { it.timeStamp ?: Date(0) }
                )

                uiScope.launch {
                    val entries = sortedPosts.mapNotNull { post ->
                        val role = result.documents.find { it.id == post.id }?.getString("role")
                        val isPostOwnerHelping = role == "providing"
                        val otherUserId = if (post.posterId == currentUserId) post.matchedId else post.posterId

                        if (otherUserId.isBlank()) return@mapNotNull null

                        val userResult = withContext(Dispatchers.IO) {
                            userRepository.getUserById(otherUserId)
                        }
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
                            isTimeRejected = post.isTimeRejected,
                            isPostOwnerHelping = isPostOwnerHelping
                        )
                    }

                    matchList.clear()
                    matchList.addAll(entries)
                    matchAdapter.notifyDataSetChanged()

                    binding.textNotifications.text =
                        if (entries.isEmpty()) "No matches yet." else "MY MATCHES"
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
    val posterId: String,
    val isPostOwnerHelping: Boolean  // <- NEW FIELD
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

fun closePostAndUpdateUI(
    context: Context,
    postId: String,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    db.collection("posts").document(postId)
        .get()
        .addOnSuccessListener { document ->
            val postOwnerId = document.getString("posterId")
            if (currentUserId != postOwnerId) {
                Toast.makeText(context, "Only the post owner can close the post", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            db.collection("posts").document(postId)
                .update("status", Post.STATUS_CLOSED)
                .addOnSuccessListener {
                    Toast.makeText(context, "Session closed!", Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
                .addOnFailureListener { e -> onFailure(e) }
        }
}

private fun closePost(postId: String, context: Context) {
    val db = FirebaseFirestore.getInstance()
    db.collection("posts").document(postId)
        .update("status", Post.STATUS_CLOSED)
        .addOnSuccessListener {
            Toast.makeText(context, "Session closed!", Toast.LENGTH_SHORT).show()

            (context as? FragmentActivity)?.supportFragmentManager?.fragments
                ?.filterIsInstance<NotificationsFragment>()
                ?.firstOrNull()
                ?.fetchMatches()
        }
        .addOnFailureListener {
            Toast.makeText(context, "Failed to close session", Toast.LENGTH_SHORT).show()
        }
}


private fun showRatingDialog(context: Context, postId: String, tutorId: String, isPostOwner: Boolean) {
    val builder = AlertDialog.Builder(context)

    val icon = ContextCompat.getDrawable(context, R.drawable.ic_like)
    icon?.setTint(ContextCompat.getColor(context, R.color.teal_700))

    builder.setTitle("Rate Your Tutor")
        .setMessage("Did you like your tutor? You can give them a like!")
        .setIcon(icon)
        .setPositiveButton("Like") { dialog, _ ->
            dialog.dismiss()

            // Show animated heart
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_animated_like, null)
            val heart = view.findViewById<ImageView>(R.id.heart_icon)

            val popup = AlertDialog.Builder(context)
                .setView(view)
                .create()

            popup.show()

            val anim = ScaleAnimation(
                0.5f, 1.5f, 0.5f, 1.5f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 400
                repeatCount = 1
                repeatMode = Animation.REVERSE
                fillAfter = true
            }

            heart.startAnimation(anim)

            // Dismiss after animation
            Handler(Looper.getMainLooper()).postDelayed({
                popup.dismiss()
            }, 800)

            // Firestore update
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(tutorId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentLikes = snapshot.getLong("likes") ?: 0
                val likedBy = snapshot.get("likedBy") as? List<String> ?: emptyList()
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

                if (currentUserId != null && !likedBy.contains(currentUserId)) {
                    transaction.update(userRef, "likes", currentLikes + 1)
                    transaction.update(userRef, "likedBy", likedBy + currentUserId)
                }
            }.addOnSuccessListener {
                Toast.makeText(context, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Toast.makeText(context, "Failed to submit like.", Toast.LENGTH_SHORT).show()
            }

            closePost(postId, context)
            Handler(Looper.getMainLooper()).postDelayed({
                (context as? FragmentActivity)?.supportFragmentManager?.fragments
                    ?.filterIsInstance<NotificationsFragment>()
                    ?.firstOrNull()
                    ?.fetchMatches()
            }, 10) // Delay ensures Firestore updates are reflected
        }
        .setNegativeButton("No Thanks") { dialog, _ ->
            dialog.dismiss()
            closePost(postId, context)
        }
        .show()
}



private class MatchAdapter(private val matches: List<MatchEntry>) : RecyclerView.Adapter<MatchAdapter.MatchViewHolder>() {
    class MatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.match_name)
        val email: TextView = itemView.findViewById(R.id.match_email)
        val courseCode: TextView = itemView.findViewById(R.id.match_course_code)
        val scheduleButton: Button = itemView.findViewById(R.id.schedule_button)
        val closeSessionButton: Button = itemView.findViewById(R.id.close_session_button)
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
        val isStudent = if (match.isPostOwnerHelping) !isPostOwner else isPostOwner
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

        val isSchedulingDone = match.finalScheduledTime != null || match.isTimeRejected == true

        val db = FirebaseFirestore.getInstance()
        db.collection("posts").document(match.postId).get()
            .addOnSuccessListener { doc ->
                if (doc.getString("status") == Post.STATUS_CLOSED) {
                    holder.itemView.alpha = 0.5f
                    holder.scheduleButton.isEnabled = false
                    holder.closeSessionButton.text = "COMPLETED"
                    holder.closeSessionButton.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    holder.closeSessionButton.isEnabled = false
                }
            }

        if (isSchedulingDone && isStudent) {
            holder.scheduleButton.visibility = View.GONE
            holder.closeSessionButton.visibility = View.VISIBLE

            // Set green background
            holder.closeSessionButton.setBackgroundColor(
                ContextCompat.getColor(context, android.R.color.holo_green_dark)
            )

            holder.closeSessionButton.setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Close Session")
                    .setMessage("Do you want to end the session and close the post?")
                    .setPositiveButton("Yes") { dialog, _ ->
                        dialog.dismiss()

                        // Show rating prompt after confirmation
                        val tutorId = if (match.isPostOwnerHelping) match.posterId else currentUserId ?: ""
                        val isOwner = match.posterId == FirebaseAuth.getInstance().currentUser?.uid

                        showRatingDialog(context, match.postId, tutorId, isOwner)
                    }
                    .setNegativeButton("No", null)
                    .show()
            }

        } else {
            holder.closeSessionButton.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            val navController = holder.itemView.findNavController()
            val bundle = bundleOf("postId" to match.postId)
            navController.navigate(R.id.postDetailFragment, bundle)
        }
    }
    override fun getItemCount() = matches.size
}
