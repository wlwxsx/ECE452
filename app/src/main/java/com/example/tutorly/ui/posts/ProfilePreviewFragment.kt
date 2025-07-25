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

class ProfilePreviewFragment : DialogFragment() {
    private lateinit var userRepository: UserRepository
    private var reportedUserId: String? = null
    private var reporterUserId: String? = null

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
        val profileLikes = view.findViewById<TextView>(R.id.profile_likes)
        val profileBio = view.findViewById<TextView>(R.id.profile_bio)
        val profileReport = view.findViewById<Button>(R.id.report_user_button)

        profileReport.setOnClickListener { openReportDialog() }
        CoroutineScope(Dispatchers.IO).launch {
            userRepository.getUserById(userId)
                .onSuccess { user ->
                    withContext(Dispatchers.Main) {
                        if (user != null) {
                            if (user.name.isNotEmpty()) profileName.text = user.name
                            if (user.pronouns.isNotEmpty()) profilePronouns.text = user.pronouns
                            profileLikes.text = "Likes: ${user.likes}"
                            if (user.bio.isNotEmpty()) profileBio.text = user.bio
                            val firstInitial = if (user.name.isNotEmpty()) user.name.first().uppercase()
                            else "?"
                            profileInitial.text = firstInitial
                            profileInitial.setBackgroundColor(Color.parseColor(user.profileColor))

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


}
