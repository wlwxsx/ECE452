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
} 