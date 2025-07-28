package com.example.tutorly

data class Availability(
    val day: String = "",
    val time: String = ""
)

//password is not stored in firebase
data class User(
    val userid: String = "",
    val name: String = "",
    val pronouns: String = "",
    val program: String = "",
    val year: String = "",
    val email: String = "",
    val contact: String = "",
    val bio: String = "",
    val availability: Availability = Availability(),
    val isAdmin: Boolean = false,
    val likes: Int = 0,
    val password: String = "",
    val tutoredCourses: List<String> = emptyList(),
    val profileColor: String = "#4CAF50", // DEFAULT_COLOR
    val likedBy: List<String> = emptyList(), // New field for tracking likes
    val isBanned: Boolean = false // New field for admin functionality
) {
    constructor() : this("", "", "", "", "", "", "", "", Availability(), false, 0, "", emptyList(), "#4CAF50", emptyList(), false)
    
    // create User without password for Firestore storage
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "userid" to userid,
            "name" to name,
            "pronouns" to pronouns,
            "program" to program,
            "year" to year,
            "email" to email,
            "contact" to contact,
            "bio" to bio,
            "availability" to mapOf(
                "day" to availability.day,
                "time" to availability.time
            ),
            "isAdmin" to isAdmin,
            "likes" to likes,
            "tutoredCourses" to tutoredCourses,
            "profileColor" to profileColor,
            "likedBy" to likedBy, // Add likedBy to Firestore map
            "isBanned" to isBanned // Add isBanned to Firestore map
        )
    }
} 