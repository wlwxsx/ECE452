package com.example.tutorly

data class Availability(
    val day: String = "",
    val time: String = ""
) {
    constructor() : this("", "")
}

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
    val tutoredCourses: List<String> = emptyList()
) {
    constructor() : this("", "", "", "", "", "", "", "", Availability(), false, 0, "", emptyList())
    
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
            "tutoredCourses" to tutoredCourses
        )
    }
} 