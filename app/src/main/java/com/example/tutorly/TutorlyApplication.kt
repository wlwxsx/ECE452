package com.example.tutorly

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import android.util.Log

class TutorlyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        // Configure Firestore settings
        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings
        
        // Disable Firestore logging
        FirebaseFirestore.setLoggingEnabled(false)
        
        // Set Firebase Auth language to avoid locale warnings
        try {
            FirebaseAuth.getInstance().setLanguageCode("en")
        } catch (e: Exception) {
            Log.w("TutorlyApp", "Failed to set Firebase Auth language: ${e.message}")
        }
    }
} 