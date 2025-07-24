package com.example.tutorly

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {
    
    companion object {
        private const val USERS_COLLECTION = "users"
        
        @Volatile
        private var INSTANCE: UserRepository? = null
        
        fun getInstance(): UserRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserRepository().also { INSTANCE = it }
            }
        }
    }
    
    private val db = FirebaseFirestore.getInstance()
    
    //save user profile to Firestore
    suspend fun saveUser(user: User): Result<Unit> {
        return try {
            db.collection(USERS_COLLECTION)
                .document(user.userid)
                .set(user.toFirestoreMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    //get user profile from Firestore
    suspend fun getUserById(userId: String): Result<User?> {
        return try {
            val document = db.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()
            
            if (document.exists()) {
                val userData = document.data
                val user = User(
                    userid = userData?.get("userid") as? String ?: "",
                    name = userData?.get("name") as? String ?: "",
                    program = userData?.get("program") as? String ?: "",
                    year = userData?.get("year") as? String ?: "",
                    email = userData?.get("email") as? String ?: "",
                    contact = userData?.get("contact") as? String ?: "",
                    bio = userData?.get("bio") as? String ?: "",
                    availability = Availability(),
                    isAdmin = userData?.get("isAdmin") as? Boolean ?: false,
                    likes = (userData?.get("likes") as? Long)?.toInt() ?: 0,
                    password = "",
                    tutoredCourses = userData?.get("tutoredCourses") as? List<String> ?: emptyList(),
                    profileColor = userData?.get("profileColor") as? String 
                        ?: userData?.get("avatarColor") as? String 
                        ?: "#4CAF50"
                )
                Result.success(user)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    //update user profile color
    suspend fun updateUserProfileColor(userId: String, profileColor: String): Result<Unit> {
        return try {
            db.collection("users").document(userId)
                .update("profileColor", profileColor)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 