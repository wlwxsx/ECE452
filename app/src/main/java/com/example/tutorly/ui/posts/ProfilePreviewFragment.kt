package com.example.tutorly.ui.profile

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.tutorly.R
import com.example.tutorly.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

class ProfilePreviewFragment : DialogFragment() {
    private lateinit var userRepository: UserRepository
    private var reportedUserId: String? = null
    private var reporterUserId: String? = null
    private var isCurrentUserAdmin: Boolean = false
    private var isProfileBanned: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        userRepository = UserRepository.getInstance()
        return inflater.inflate(R.layout.fragment_profile_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val userId = arguments?.getString("userId") ?: return
        reportedUserId = userId
        reporterUserId = arguments?.getString("reporterUserId")
        val profileInitial = view.findViewById<TextView>(R.id.text_profile_profile)
        val profileName = view.findViewById<TextView>(R.id.profile_name)
        val profilePronouns = view.findViewById<TextView>(R.id.profile_pronouns)
        val profileBio = view.findViewById<TextView>(R.id.profile_bio)
        val profileReport = view.findViewById<Button>(R.id.report_user_button)
        val likeButton = view.findViewById<android.widget.ImageButton>(R.id.like_button)
        val likeCount = view.findViewById<TextView>(R.id.like_count)

        // Check if current user is admin
        checkAdminStatus { isAdmin ->
            isCurrentUserAdmin = isAdmin
            setupUI(profileReport, isAdmin)
        }

        likeButton.setOnClickListener {
            if (userId != reporterUserId) {
                handleLikeToggle(userId, reporterUserId, likeButton, likeCount)
            } else {
                Toast.makeText(context, "You cannot like yourself.", Toast.LENGTH_SHORT).show()
            }
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            userRepository.getUserById(userId)
                .onSuccess { user ->
                    withContext(Dispatchers.Main) {
                        if (user != null) {
                            if (user.name.isNotEmpty()) profileName.text = user.name
                            if (user.pronouns.isNotEmpty()) profilePronouns.text = user.pronouns
                            if (user.bio.isNotEmpty()) profileBio.text = user.bio
                            val firstInitial = if (user.name.isNotEmpty()) user.name.first().uppercase()
                            else "?"
                            profileInitial.text = firstInitial
                            profileInitial.setBackgroundColor(Color.parseColor(user.profileColor))
                            likeCount.text = user.likes.toString()
                            
                            // Show admin badge if user is admin
                            if (user.isAdmin) {
                                val adminBadge = view.findViewById<TextView>(R.id.admin_badge)
                                adminBadge?.visibility = View.VISIBLE
                                adminBadge?.text = "ADMIN"
                                adminBadge?.setBackgroundColor(Color.parseColor("#FFD700")) // Gold color
                                adminBadge?.setTextColor(Color.BLACK)
                            }
                            
                            // Show banned status if user is banned
                            if (user.isBanned) {
                                val bannedBadge = view.findViewById<TextView>(R.id.banned_badge)
                                bannedBadge?.visibility = View.VISIBLE
                                bannedBadge?.text = "BANNED"
                                bannedBadge?.setBackgroundColor(Color.parseColor("#FF5722")) // Red color
                                bannedBadge?.setTextColor(Color.WHITE)
                            }
                            
                            // Set like button icon based on liked state
                            if (reporterUserId != null && user.likedBy.contains(reporterUserId)) {
                                likeButton.setImageResource(R.drawable.ic_like)
                                likeButton.alpha = 1.0f
                            } else {
                                likeButton.setImageResource(R.drawable.ic_like_outline)
                                likeButton.alpha = 1.0f
                            }
                            isProfileBanned = user.isBanned
                            
                            // Update UI with correct ban status
                            if (isCurrentUserAdmin) {
                                setupUI(profileReport, isCurrentUserAdmin)
                            }
                        } else {
                            profileName.text = "User not found"
                        }
                    }
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        profileName.text = "Error loading profile"
                    }
                }
        }
    }

    private fun checkAdminStatus(callback: (Boolean) -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val isAdmin = userRepository.isUserAdmin(currentUserId).getOrNull() ?: false
                withContext(Dispatchers.Main) {
                    callback(isAdmin)
                }
            }
        } else {
            callback(false)
        }
    }

    private fun setupUI(profileReport: Button, isAdmin: Boolean) {
        if (isAdmin) {
            profileReport.text = if (isProfileBanned) "Unban User" else "Ban User"
            profileReport.setBackgroundColor(Color.parseColor("#FF5722")) // Red color for ban/unban
            profileReport.setOnClickListener {
                reportedUserId?.let { userId ->
                    reporterUserId?.let { reporterId ->
                        if (userId == reporterId) {
                            Toast.makeText(context, "You cannot ban/unban yourself.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        context?.let { ctx ->
                            showBanPopup(ctx, userId, reporterId)
                        }
                    }
                }
            }
        } else {
            profileReport.text = "Report User"
            profileReport.setBackgroundColor(Color.parseColor("#FF9800")) // Orange color for report
            profileReport.setOnClickListener { openReportDialog() }
        }
    }

    private fun openReportDialog() {
        reportedUserId?.let { userId ->
            reporterUserId?.let { reporterId ->
                if (userId == reporterId) {
                    Toast.makeText(context, "You cannot report yourself.", Toast.LENGTH_SHORT).show()
                    return
                }
                context?.let { ctx ->
                    showReportPopup(ctx, userId, reporterId)
                }
            }
        }
    }

    private fun showReportPopup(context: Context, reportedUserId: String, reporterUserId: String) {
        val popupView = LayoutInflater.from(context).inflate(R.layout.fragment_report, null)
        val description = popupView.findViewById<EditText>(R.id.report_description)
        val reportButton = popupView.findViewById<Button>(R.id.btn_report)
        val cancelButton = popupView.findViewById<Button>(R.id.btn_cancel)
        val popup = AlertDialog.Builder(context)
            .setView(popupView)
            .create()
        popup.window?.setBackgroundDrawableResource(android.R.color.transparent)
        cancelButton.setOnClickListener { popup.dismiss() }
        reportButton.setOnClickListener {
            val details = description.text.toString().trim()
            if (details.isNotEmpty()) {
                submitReport(popup, reportedUserId, reporterUserId, details)
                popup.dismiss()
            } else Toast.makeText(context, "Please describe the issue.", Toast.LENGTH_SHORT).show()
        }
        popup.show()
    }

    private fun submitReport(popup: AlertDialog, reportedUserId: String, reporterUserId: String, details: String) {
        val reportData = hashMapOf(
            "reportedUserId" to reportedUserId,
            "reportingUserId" to reporterUserId,
            "details" to details,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "status" to "pending"
        )
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("reports")
            .add(reportData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(context, "Report submitted successfully", Toast.LENGTH_SHORT).show()
                popup.dismiss()
            }
            .addOnFailureListener { exception -> Toast.makeText(context, "Error submitting report: ${exception.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun openBanDialog() {
        reportedUserId?.let { userId ->
            reporterUserId?.let { reporterId ->
                if (userId == reporterId) {
                    Toast.makeText(context, "You cannot ban yourself.", Toast.LENGTH_SHORT).show()
                    return
                }
                context?.let { ctx ->
                    showBanPopup(ctx, userId, reporterId)
                }
            }
        }
    }

    private fun showBanPopup(context: Context, reportedUserId: String, reporterUserId: String) {
        val popupView = LayoutInflater.from(context).inflate(R.layout.fragment_ban, null)
        val reason = popupView.findViewById<EditText>(R.id.ban_reason)
        val banButton = popupView.findViewById<Button>(R.id.btn_ban)
        val cancelButton = popupView.findViewById<Button>(R.id.btn_cancel)
        val dialogTitle = popupView.findViewById<TextView>(R.id.ban_dialog_title)
        val dialogMessage = popupView.findViewById<TextView>(R.id.ban_dialog_message)
        
        // Always fetch the latest ban status from server to ensure correct popup text
        CoroutineScope(Dispatchers.IO).launch {
            val userResult = userRepository.getUserById(reportedUserId, forceServer = true)
            val isCurrentlyBanned = userResult.getOrNull()?.isBanned ?: false
            
            withContext(Dispatchers.Main) {
                if (isCurrentlyBanned) {
                    // User is banned, show unban dialog
                    dialogTitle.text = "Unban User"
                    dialogMessage.text = "Are you sure you want to unban this user?"
                    banButton.text = "Unban User"
                    reason.visibility = View.GONE
                    reason.hint = ""
                    
                    val popup = AlertDialog.Builder(context)
                        .setView(popupView)
                        .create()
                    popup.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    cancelButton.setOnClickListener { popup.dismiss() }
                    banButton.setOnClickListener {
                        CoroutineScope(Dispatchers.IO).launch {
                            val result = userRepository.unbanUser(reportedUserId, reporterUserId)
                            withContext(Dispatchers.Main) {
                                if (result.isSuccess) {
                                    Toast.makeText(context, "User unbanned successfully", Toast.LENGTH_SHORT).show()
                                    popup.dismiss()
                                    // Refresh user data and UI after unban
                                    refreshProfileUIAfterBanChange(reportedUserId)
                                } else {
                                    Toast.makeText(context, "Failed to unban user: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    popup.show()
                } else {
                    // User is not banned, show ban dialog
                    dialogTitle.text = "Ban User"
                    dialogMessage.text = "Please provide a reason for banning this user:"
                    banButton.text = "Ban User"
                    reason.visibility = View.VISIBLE
                    reason.hint = "Enter ban reason..."
                    
                    val popup = AlertDialog.Builder(context)
                        .setView(popupView)
                        .create()
                    popup.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    cancelButton.setOnClickListener { popup.dismiss() }
                    banButton.setOnClickListener {
                        val banReason = reason.text.toString().trim()
                        if (banReason.isNotEmpty()) {
                            banUser(popup, reportedUserId, reporterUserId, banReason)
                            popup.dismiss()
                        } else Toast.makeText(context, "Please provide a reason for the ban.", Toast.LENGTH_SHORT).show()
                    }
                    popup.show()
                }
            }
        }
    }

    private fun banUser(popup: AlertDialog, reportedUserId: String, reporterUserId: String, reason: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = userRepository.banUser(reportedUserId, reporterUserId)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(context, "User banned successfully", Toast.LENGTH_SHORT).show()
                    popup.dismiss()
                    dismiss() // Close the profile preview
                } else {
                    Toast.makeText(context, "Failed to ban user: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshProfileUIAfterBanChange(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            userRepository.getUserById(userId, forceServer = true)
                .onSuccess { user ->
                    withContext(Dispatchers.Main) {
                        if (user != null) {
                            isProfileBanned = user.isBanned
                            val profileReport = view?.findViewById<Button>(R.id.report_user_button)
                            val bannedBadge = view?.findViewById<TextView>(R.id.banned_badge)
                            setupUI(profileReport!!, isCurrentUserAdmin)
                            if (user.isBanned) {
                                bannedBadge?.visibility = View.VISIBLE
                            } else {
                                bannedBadge?.visibility = View.GONE
                            }
                        }
                    }
                }
        }
    }

    private fun handleLikeToggle(userId: String?, reporterUserId: String?, likeButton: android.widget.ImageButton, likeCount: TextView) {
        if (userId == null || reporterUserId == null) return
        CoroutineScope(Dispatchers.IO).launch {
            userRepository.toggleUserLike(userId, reporterUserId)
                .onSuccess { liked ->
                    withContext(Dispatchers.Main) {
                        // Update the likes display and icon
                        userRepository.getUserById(userId)
                            .onSuccess { user ->
                                user?.let {
                                    likeCount.text = it.likes.toString()
                                    if (liked) {
                                        likeButton.setImageResource(R.drawable.ic_like)
                                        Toast.makeText(context, "Liked!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        likeButton.setImageResource(R.drawable.ic_like_outline)
                                        Toast.makeText(context, "Unliked!", Toast.LENGTH_SHORT).show()
                                    }
                                    likeButton.alpha = 1.0f
                                }
                            }
                    }
                }
                .onFailure { exception ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, exception.message ?: "Error toggling like", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }


}
