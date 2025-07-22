package com.example.tutorly

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {
    
    companion object {
        private const val TAG = "UserRepository"
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
            Log.d(TAG, "User saved successfully: ${user.userid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user: ${user.userid}", e)
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
                    pronouns = userData?.get("pronouns") as? String ?: "",
                    program = userData?.get("program") as? String ?: "",
                    year = userData?.get("year") as? String ?: "",
                    email = userData?.get("email") as? String ?: "",
                    contact = userData?.get("contact") as? String ?: "",
                    bio = userData?.get("bio") as? String ?: "",
                    availability = Availability(), // Default for now
                    isAdmin = userData?.get("isAdmin") as? Boolean ?: false,
                    likes = (userData?.get("likes") as? Long)?.toInt() ?: 0,
                    password = "", // Never retrieve password
                    tutoredCourses = userData?.get("tutoredCourses") as? List<String> ?: emptyList()
                )
                Log.d(TAG, "User retrieved successfully: $userId")
                Result.success(user)
            } else {
                Log.d(TAG, "User not found: $userId")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving user: $userId", e)
            Result.failure(e)
        }
    }
} 