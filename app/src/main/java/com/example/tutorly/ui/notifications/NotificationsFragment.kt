package com.example.tutorly.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
import android.app.AlertDialog
import android.widget.Button
import android.widget.EditText
import java.text.SimpleDateFormat
import java.util.*

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var userRepository: UserRepository
    private lateinit var matchAdapter: MatchAdapter
    private lateinit var reportAdapter: ReportAdapter
    private val matchList = mutableListOf<MatchEntry>()
    private val reportList = mutableListOf<ReportDisplayEntry>()
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private var isCurrentUserAdmin: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        db = FirebaseFirestore.getInstance()
        userRepository = UserRepository.getInstance()
        checkAdminStatus()
        return binding.root
    }

    private fun checkAdminStatus() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val isAdmin = userRepository.isUserAdmin(currentUserId).getOrNull() ?: false
                withContext(Dispatchers.Main) {
                    isCurrentUserAdmin = isAdmin
                    setupRecyclerView()
                    if (isAdmin) {
                        fetchReports()
                        binding.textNotifications.text = "REPORTS"
                    } else {
                        fetchMatches()
                        binding.textNotifications.text = "MY MATCHES"
                    }
                }
            }
        } else {
            setupRecyclerView()
            fetchMatches()
            binding.textNotifications.text = "MY MATCHES"
        }
    }

    private fun setupRecyclerView() {
        if (isCurrentUserAdmin) {
            reportAdapter = ReportAdapter(
                reports = reportList,
                onBanClick = { report ->
                    showBanDialog(report)
                },
                onRejectClick = { report ->
                    rejectReport(report)
                },
                onItemClick = { report ->
                    showReportedUserDetails(report)
                }
            )
            binding.matchRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = reportAdapter
            }
        } else {
            matchAdapter = MatchAdapter(matchList)
            binding.matchRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = matchAdapter
            }
        }
    }

    private fun fetchReports() {
        db.collection("reports")
            .get()
            .addOnSuccessListener { result ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                
                val reports = result.documents.mapNotNull { doc ->
                    val data = doc.data
                    if (data != null) {
                        ReportEntry(
                            reportedUserId = data["reportedUserId"] as? String ?: "",
                            reportingUserId = data["reportingUserId"] as? String ?: "",
                            details = data["details"] as? String ?: "",
                            timestamp = data["timestamp"] as? com.google.firebase.Timestamp,
                            status = data["status"] as? String ?: "pending",
                            reportId = doc.id
                        )
                    } else null
                }
                
                uiScope.launch {
                    val reportEntries = reports.mapNotNull { report ->
                        val reportedUserResult = withContext(Dispatchers.IO) { 
                            userRepository.getUserById(report.reportedUserId) 
                        }
                        val reportingUserResult = withContext(Dispatchers.IO) { 
                            userRepository.getUserById(report.reportingUserId) 
                        }
                        
                        val reportedUser = reportedUserResult.getOrNull()
                        val reportingUser = reportingUserResult.getOrNull()
                        
                        if (reportedUser != null && reportingUser != null) {
                            ReportDisplayEntry(
                                reportedUserName = reportedUser.name,
                                reportedUserEmail = reportedUser.email,
                                reportingUserName = reportingUser.name,
                                reportingUserEmail = reportingUser.email,
                                details = report.details,
                                timestamp = report.timestamp,
                                status = report.status,
                                reportId = report.reportId,
                                reportedUserId = report.reportedUserId,
                                isBanned = reportedUser.isBanned
                            )
                        } else null
                    }
                    
                    reportList.clear()
                    reportList.addAll(reportEntries)
                    reportAdapter.notifyDataSetChanged()
                    
                    if (isAdded && _binding != null) {
                        _binding?.textNotifications?.text = if (reportEntries.isEmpty()) "No reports yet." else "REPORTS"
                    }
                }
            }
            .addOnFailureListener {
                if (isAdded && _binding != null) {
                    _binding?.textNotifications?.text = "Failed to load reports."
                }
            }
    }

    private fun showBanDialog(report: ReportDisplayEntry) {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_ban, null)
        val reason = popupView.findViewById<EditText>(R.id.ban_reason)
        val banButton = popupView.findViewById<Button>(R.id.btn_ban)
        val cancelButton = popupView.findViewById<Button>(R.id.btn_cancel)
        val dialogTitle = popupView.findViewById<TextView>(R.id.ban_dialog_title)
        val dialogMessage = popupView.findViewById<TextView>(R.id.ban_dialog_message)
        
        val popup = AlertDialog.Builder(requireContext())
            .setView(popupView)
            .create()
        popup.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        cancelButton.setOnClickListener { popup.dismiss() }
        if (report.isBanned) {
            // User is banned, show unban dialog
            dialogTitle.text = "Unban User"
            dialogMessage.text = "Are you sure you want to unban this user?"
            banButton.text = "Unban User"
            reason.visibility = View.GONE
            reason.hint = ""
            banButton.setOnClickListener {
                unbanUser(report.reportedUserId, popup)
            }
        } else {
            // User is not banned, show ban dialog
            dialogTitle.text = "Ban User"
            dialogMessage.text = "Please provide a reason for banning this user:"
            banButton.text = "Ban User"
            reason.visibility = View.VISIBLE
            reason.hint = "Enter ban reason..."
            // Pre-fill the reason with report details
            reason.setText("Reported for: ${report.details}")
            banButton.setOnClickListener {
                val banReason = reason.text.toString().trim()
                if (banReason.isNotEmpty()) {
                    banUser(report.reportedUserId, banReason, popup)
                } else {
                    Toast.makeText(context, "Please provide a reason for the ban.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        popup.show()
    }

    private fun banUser(userId: String, reason: String, popup: AlertDialog) {
        uiScope.launch {
            val result = withContext(Dispatchers.IO) {
                userRepository.banUser(userId, FirebaseAuth.getInstance().currentUser?.uid ?: "")
            }
            
            if (result.isSuccess) {
                popup.dismiss()
                Toast.makeText(requireContext(), "User banned successfully", Toast.LENGTH_SHORT).show()
                // Remove all reports for this user from the list
                reportList.removeAll { it.reportedUserId == userId }
                reportAdapter.notifyDataSetChanged()
                
                // Update the header text
                if (isAdded && _binding != null) {
                    _binding?.textNotifications?.text = if (reportList.isEmpty()) "No reports yet." else "REPORTS"
                }
            } else {
                Toast.makeText(requireContext(), "Failed to ban user: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unbanUser(userId: String, popup: AlertDialog) {
        uiScope.launch {
            val result = withContext(Dispatchers.IO) {
                userRepository.unbanUser(userId, FirebaseAuth.getInstance().currentUser?.uid ?: "")
            }
            if (result.isSuccess) {
                popup.dismiss()
                Toast.makeText(requireContext(), "User unbanned successfully", Toast.LENGTH_SHORT).show()
                // Remove all reports for this user from the list
                reportList.removeAll { it.reportedUserId == userId }
                reportAdapter.notifyDataSetChanged()
                
                // Update the header text
                if (isAdded && _binding != null) {
                    _binding?.textNotifications?.text = if (reportList.isEmpty()) "No reports yet." else "REPORTS"
                }
            } else {
                Toast.makeText(requireContext(), "Failed to unban user: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun rejectReport(report: ReportDisplayEntry) {
        // Show confirmation dialog
        AlertDialog.Builder(requireContext())
            .setTitle("Reject Report")
            .setMessage("Are you sure you want to reject this report? This will remove it from the reports list.")
            .setPositiveButton("Reject") { _, _ ->
                // Remove the report from Firestore
                db.collection("reports").document(report.reportId)
                    .delete()
                    .addOnSuccessListener {
                        // Remove from local list and update UI
                        reportList.removeAll { it.reportId == report.reportId }
                        reportAdapter.notifyDataSetChanged()
                        
                        // Update the header text
                        if (isAdded && _binding != null) {
                            _binding?.textNotifications?.text = if (reportList.isEmpty()) "No reports yet." else "REPORTS"
                        }
                        
                        Toast.makeText(requireContext(), "Report rejected and removed", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to reject report: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchMatches() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("posts")
            .whereEqualTo("status", Post.STATUS_MATCHED)
            .get()
            .addOnSuccessListener { result ->
                // Check if fragment is still attached and binding is available
                if (!isAdded || _binding == null) return@addOnSuccessListener
                
                val posts = result.documents.mapNotNull { it.toObject(Post::class.java) }
                val relevantPosts = posts.filter {
                    (it.posterId == currentUserId || it.matchedId == currentUserId)
                }
                if (relevantPosts.isEmpty()) {
                    matchList.clear()
                    matchAdapter.notifyDataSetChanged()
                    _binding?.textNotifications?.text = "No matches yet."
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
                    
                    // Check if fragment is still attached before updating UI
                    if (isAdded && _binding != null) {
                        _binding?.textNotifications?.text = if (entries.isEmpty()) "No matches yet." else "MY MATCHES"
                    }
                }
            }
            .addOnFailureListener {
                // Check if fragment is still attached before updating UI
                if (isAdded && _binding != null) {
                    _binding?.textNotifications?.text = "Failed to load matches."
                }
            }
    }

    private fun showReportedUserDetails(report: ReportDisplayEntry) {
        // Fetch the reported user's details from the repository
        uiScope.launch {
            val userResult = withContext(Dispatchers.IO) {
                userRepository.getUserById(report.reportedUserId)
            }
            val user = userResult.getOrNull()
            if (user != null && isAdded) {
                val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_user_details, null)
                dialogView.findViewById<TextView>(R.id.user_id).text = "User ID: ${user.userid}"
                dialogView.findViewById<TextView>(R.id.user_name).text = "Name: ${user.name}"
                dialogView.findViewById<TextView>(R.id.user_email).text = "Email: ${user.email}"
                dialogView.findViewById<TextView>(R.id.user_pronouns).text = "Pronouns: ${user.pronouns}"
                dialogView.findViewById<TextView>(R.id.user_program).text = "Program: ${user.program}"
                dialogView.findViewById<TextView>(R.id.user_year).text = "Year: ${user.year}"
                dialogView.findViewById<TextView>(R.id.user_contact).text = "Contact: ${user.contact}"
                dialogView.findViewById<TextView>(R.id.user_bio).text = "Bio: ${user.bio}"

                AlertDialog.Builder(requireContext())
                    .setTitle("Reported User Details")
                    .setView(dialogView)
                    .setPositiveButton("Close", null)
                    .show()
            } else if (isAdded) {
                Toast.makeText(requireContext(), "Failed to load user details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        job.cancel()
    }
}

// Data classes for reports
private data class ReportEntry(
    val reportedUserId: String,
    val reportingUserId: String,
    val details: String,
    val timestamp: com.google.firebase.Timestamp?,
    val status: String,
    val reportId: String
)

private data class ReportDisplayEntry(
    val reportedUserName: String,
    val reportedUserEmail: String,
    val reportingUserName: String,
    val reportingUserEmail: String,
    val details: String,
    val timestamp: com.google.firebase.Timestamp?,
    val status: String,
    val reportId: String,
    val reportedUserId: String,
    val isBanned: Boolean
)

// Data class for match entry
private data class MatchEntry(val name: String, val email: String, val course: String, val postId: String)

// Adapter for displaying reports
private class ReportAdapter(
    private val reports: MutableList<ReportDisplayEntry>,
    private val onBanClick: (ReportDisplayEntry) -> Unit,
    private val onRejectClick: (ReportDisplayEntry) -> Unit,
    private val onItemClick: (ReportDisplayEntry) -> Unit
) : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {
    
    class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val reportedUser: TextView = itemView.findViewById(R.id.reported_user)
        val reportingUser: TextView = itemView.findViewById(R.id.reporting_user)
        val details: TextView = itemView.findViewById(R.id.report_details)
        val timestamp: TextView = itemView.findViewById(R.id.report_timestamp)
        val banButton: Button = itemView.findViewById(R.id.ban_button)
        val rejectButton: Button = itemView.findViewById(R.id.reject_button)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report, parent, false)
        return ReportViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        holder.reportedUser.text = "Reported: ${report.reportedUserName} (${report.reportedUserEmail})"
        holder.reportingUser.text = "By: ${report.reportingUserName} (${report.reportingUserEmail})"
        holder.details.text = "Details: ${report.details}"
        
        val timestamp = report.timestamp?.toDate()
        val dateText = if (timestamp != null) {
            val sdf = SimpleDateFormat("MM/dd/yyyy - h:mm a", Locale.getDefault())
            sdf.format(timestamp)
        } else {
            "Unknown date"
        }
        holder.timestamp.text = "Reported on: $dateText"
        
        holder.banButton.text = if (report.isBanned) "Unban User" else "Ban User"
        holder.banButton.setOnClickListener {
            onBanClick(report)
        }
        
        holder.rejectButton.setOnClickListener {
            onRejectClick(report)
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(report)
        }
    }
    
    override fun getItemCount() = reports.size
}

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