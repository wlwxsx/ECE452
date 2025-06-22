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
    val status: String = "",
    @ServerTimestamp
    val timeStamp: Date? = null,
    val title: String = ""
) 