package com.example.tutorly.ui.posts

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Comment(
    @DocumentId
    val id: String = "",
    val content: String = "",
    val postId: String = "",
    @ServerTimestamp
    val timeStamp: Date? = null,
    val userId: String = ""
) 