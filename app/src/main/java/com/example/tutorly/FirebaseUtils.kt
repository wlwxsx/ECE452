package com.example.tutorly

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException

object FirebaseUtils {
    private const val TAG = "FirebaseUtils"
    
    /**
     * Determines if a Firebase exception is related to authentication token issues
     * and can be safely ignored (logged at debug level instead of error)
     */
    fun isAuthTokenError(exception: Exception): Boolean {
        return when {
            exception is FirebaseException && 
                exception.message?.contains("Requests to this API securetoken.googleapis.com") == true -> true
            exception is FirebaseException && 
                exception.message?.contains("Failed to get auth token") == true -> true
            exception is FirebaseAuthException && 
                exception.errorCode == "ERROR_NETWORK_REQUEST_FAILED" -> true
            exception is FirebaseFirestoreException && 
                exception.code == FirebaseFirestoreException.Code.UNAUTHENTICATED -> true
            else -> false
        }
    }
    
    /**
     * Logs Firebase exceptions appropriately based on their type
     * Reduces noise from common authentication token refresh errors
     */
    fun logFirebaseException(tag: String, message: String, exception: Exception) {
        if (isAuthTokenError(exception)) {
            Log.d(tag, "$message (auth token issue - can be ignored): ${exception.message}")
        } else {
            Log.e(tag, message, exception)
        }
    }
    
    /**
     * Safe wrapper for Firebase operations that might fail due to auth issues
     */
    fun <T> safeFirebaseOperation(
        operation: () -> Unit,
        onSuccess: ((T) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null,
        operationName: String = "Firebase operation"
    ) {
        try {
            operation()
        } catch (e: Exception) {
            if (isAuthTokenError(e)) {
                Log.d(TAG, "$operationName failed with auth token error (can be ignored): ${e.message}")
            } else {
                Log.e(TAG, "$operationName failed", e)
                onFailure?.invoke(e)
            }
        }
    }
} 