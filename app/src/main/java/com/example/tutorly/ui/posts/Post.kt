package com.example.tutorly.ui.posts

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Post(
    @DocumentId
    val id: String = "",
    val courseCode: String = "",
    val courseName: String = "",
    val matchedId: String = "",
    val message: String = "",
    val posterId: String = "",
    val role: String = "",
    val status: String = "active", // Default status
    @ServerTimestamp
    val timeStamp: Date? = null,
    val title: String = "",
    var scheduledTime: Date? = null // ← add this
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_MATCHED = "matched"
        const val STATUS_CLOSED = "closed"
    }
} 