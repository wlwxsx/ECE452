package com.example.tutorly.ui.posts

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Comment(
    val id: String = "",
    val content: String = "",
    val postId: String = "",
    val timeStamp: Date? = null,
    val userId: String = ""
) {
    // No-argument constructor for Firebase
    constructor() : this("", "", "", null, "")
} 