package com.example.tutorly

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
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
    
    // Simple in-memory cache for user data
    private val userCache = mutableMapOf<String, User>()

    //save user profile to Firestore
    suspend fun saveUser(user: User): Result<Unit> {
        return try {
            db.collection(USERS_COLLECTION)
                .document(user.userid)
                .set(user.toFirestoreMap())
                .await()

            // Update cache after successful save
            userCache[user.userid] = user

            Log.d(TAG, "User saved successfully: ${user.userid}")
            Result.success(Unit)
        } catch (e: Exception) {
            FirebaseUtils.logFirebaseException(TAG, "Error saving user: ${user.userid}", e)
            Result.failure(e)
        }
    }
    
    //get user profile from Firestore
    suspend fun getUserById(userId: String, forceServer: Boolean = false): Result<User?> {
        return try {
            // Check cache first unless forceServer is true
            if (!forceServer) {
                userCache[userId]?.let { cachedUser ->
                    Log.d(TAG, "User retrieved from cache: $userId")
                    return Result.success(cachedUser)
                }
            }

            // Try server if forceServer, otherwise try cache first then server
            val document = if (forceServer) {
                db.collection(USERS_COLLECTION)
                    .document(userId)
                    .get(Source.SERVER)
                    .await()
            } else {
                try {
                    db.collection(USERS_COLLECTION)
                        .document(userId)
                        .get(Source.CACHE)
                        .await()
                } catch (e: Exception) {
                    db.collection(USERS_COLLECTION)
                        .document(userId)
                        .get(Source.SERVER)
                        .await()
                }
            }

            val userData = if (document.exists()) {
                document.data
            } else {
                null
            }
            
            if (userData != null) {
                // Debug: Print the actual data structure
                Log.d(TAG, "User data retrieved for $userId: $userData")

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
                    isAdmin = userData["isAdmin"] as? Boolean ?: false,
                    likes = (userData["likes"] as? Long)?.toInt() ?: 0,
                    password = "", // Never retrieve password
                    tutoredCourses = userData["tutoredCourses"] as? List<String> ?: emptyList(),
                    profileColor = userData?.get("profileColor") as? String
                        ?: userData?.get("avatarColor") as? String
                        ?: "#4CAF50",
                    likedBy = (userData["likedBy"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    isBanned = userData["isBanned"] as? Boolean ?: false
                )

                Log.d(TAG, "User object created: name='${user.name}', userid='${user.userid}'")

                // Cache the user
                userCache[userId] = user

                Log.d(TAG, "User retrieved successfully: $userId")
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
                .await()
            
            // Update cache
            userCache[userId]?.let { cachedUser ->
                userCache[userId] = cachedUser.copy(profileColor = profileColor)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    //update user like count
    suspend fun incrementUserLikes(userId: String, currentUserId: String): Result<Unit> {
        return try {
            // Get the user document
            val userDocRef = db.collection("users").document(userId)
            val snapshot = userDocRef.get().await()
            val likedBy = (snapshot.get("likedBy") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            if (likedBy.contains(currentUserId)) {
                // Already liked
                return Result.failure(Exception("You have already liked this user."))
            }
            // Atomically increment likes and add to likedBy
            userDocRef.update(
                mapOf(
                    "likes" to com.google.firebase.firestore.FieldValue.increment(1),
                    "likedBy" to com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId)
                )
            ).await()
            // Update cache if user is cached
            userCache[userId]?.let { cachedUser ->
                userCache[userId] = cachedUser.copy(
                    likes = cachedUser.likes + 1,
                    likedBy = cachedUser.likedBy + currentUserId
                )
            }
            Log.d(TAG, "User likes incremented successfully: $userId by $currentUserId")
            Result.success(Unit)
        } catch (e: Exception) {
            FirebaseUtils.logFirebaseException(TAG, "Error incrementing user likes: $userId", e)
            Result.failure(e)
        }
    }

    //toggle user like
    suspend fun toggleUserLike(userId: String, currentUserId: String): Result<Boolean> {
        return try {
            val userDocRef = db.collection(USERS_COLLECTION).document(userId)
            val snapshot = userDocRef.get().await()
            val likedBy = (snapshot.get("likedBy") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val isLiked = likedBy.contains(currentUserId)
            if (isLiked) {
                // Unlike: decrement likes and remove from likedBy
                userDocRef.update(
                    mapOf(
                        "likes" to com.google.firebase.firestore.FieldValue.increment(-1),
                        "likedBy" to com.google.firebase.firestore.FieldValue.arrayRemove(currentUserId)
                    )
                ).await()
                userCache[userId]?.let { cachedUser ->
                    userCache[userId] = cachedUser.copy(
                        likes = (cachedUser.likes - 1).coerceAtLeast(0),
                        likedBy = cachedUser.likedBy - currentUserId
                    )
                }
                Log.d(TAG, "User unliked successfully: $userId by $currentUserId")
                Result.success(false)
            } else {
                // Like: increment likes and add to likedBy
                userDocRef.update(
                    mapOf(
                        "likes" to com.google.firebase.firestore.FieldValue.increment(1),
                        "likedBy" to com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId)
                    )
                ).await()
                userCache[userId]?.let { cachedUser ->
                    userCache[userId] = cachedUser.copy(
                        likes = cachedUser.likes + 1,
                        likedBy = cachedUser.likedBy + currentUserId
                    )
                }
                Log.d(TAG, "User liked successfully: $userId by $currentUserId")
                Result.success(true)
            }
        } catch (e: Exception) {
            FirebaseUtils.logFirebaseException(TAG, "Error toggling user like: $userId", e)
            Result.failure(e)
        }
    }

    // Clear cache if needed
    fun clearCache() {
        userCache.clear()
        Log.d(TAG, "User cache cleared")
    }
    
    // Clear user from cache
    fun clearUserFromCache(userId: String) {
        userCache.remove(userId)
        Log.d(TAG, "User removed from cache: $userId")
    }

    // Admin methods
    suspend fun banUser(userId: String, adminUserId: String): Result<Unit> {
        return try {
            // First check if the current user is an admin
            val adminUser = getUserById(adminUserId).getOrNull()
            if (adminUser?.isAdmin != true) {
                return Result.failure(Exception("Only admins can ban users"))
            }

            // Update the user's banned status
            db.collection(USERS_COLLECTION).document(userId)
                .update("isBanned", true)
                .await()

            // Update cache if user is cached
            userCache[userId]?.let { cachedUser ->
                userCache[userId] = cachedUser.copy(isBanned = true)
            }

            Log.d(TAG, "User banned successfully: $userId by admin: $adminUserId")
            Result.success(Unit)
        } catch (e: Exception) {
            FirebaseUtils.logFirebaseException(TAG, "Error banning user: $userId", e)
            Result.failure(e)
        }
    }

    suspend fun unbanUser(userId: String, adminUserId: String): Result<Unit> {
        return try {
            // First check if the current user is an admin
            val adminUser = getUserById(adminUserId).getOrNull()
            if (adminUser?.isAdmin != true) {
                return Result.failure(Exception("Only admins can unban users"))
            }

            // Update the user's banned status
            db.collection(USERS_COLLECTION).document(userId)
                .update("isBanned", false)
                .await()

            // Update cache if user is cached
            userCache[userId]?.let { cachedUser ->
                userCache[userId] = cachedUser.copy(isBanned = false)
            }

            Log.d(TAG, "User unbanned successfully: $userId by admin: $adminUserId")
            Result.success(Unit)
        } catch (e: Exception) {
            FirebaseUtils.logFirebaseException(TAG, "Error unbanning user: $userId", e)
            Result.failure(e)
        }
    }

    suspend fun isUserAdmin(userId: String): Result<Boolean> {
        return try {
            val user = getUserById(userId).getOrNull()
            Result.success(user?.isAdmin == true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val snapshot = db.collection(USERS_COLLECTION).get().await()
            val users = snapshot.documents.mapNotNull { document ->
                val userData = document.data
                if (userData != null) {
                    User(
                        userid = userData["userid"] as? String ?: "",
                        name = userData["name"] as? String ?: "",
                        pronouns = userData["pronouns"] as? String ?: "",
                        program = userData["program"] as? String ?: "",
                        year = userData["year"] as? String ?: "",
                        email = userData["email"] as? String ?: "",
                        contact = userData["contact"] as? String ?: "",
                        bio = userData["bio"] as? String ?: "",
                        availability = Availability(), // Default for now
                        isAdmin = userData["isAdmin"] as? Boolean ?: false,
                        likes = (userData["likes"] as? Long)?.toInt() ?: 0,
                        password = "", // Never retrieve password
                        tutoredCourses = userData["tutoredCourses"] as? List<String> ?: emptyList(),
                        profileColor = userData["profileColor"] as? String ?: "#4CAF50",
                        likedBy = (userData["likedBy"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        isBanned = userData["isBanned"] as? Boolean ?: false
                    )
                } else null
            }
            Result.success(users)
        } catch (e: Exception) {
            FirebaseUtils.logFirebaseException(TAG, "Error getting all users", e)
            Result.failure(e)
        }
    }

    // Method to make a user an admin (for initial setup)
    suspend fun makeUserAdmin(userId: String): Result<Unit> {
        return try {
            db.collection(USERS_COLLECTION).document(userId)
                .update("isAdmin", true)
                .await()

            // Update cache if user is cached
            userCache[userId]?.let { cachedUser ->
                userCache[userId] = cachedUser.copy(isAdmin = true)
            }

            Log.d(TAG, "User made admin successfully: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            FirebaseUtils.logFirebaseException(TAG, "Error making user admin: $userId", e)
            Result.failure(e)
        }
    }
}