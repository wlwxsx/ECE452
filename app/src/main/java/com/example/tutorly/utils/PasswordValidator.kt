package com.example.tutorly.utils

import android.content.Context
import android.widget.Toast

object PasswordValidator {
    //for checking if register password is correct
    fun isPasswordValid(password: String, context: Context): Boolean {
        if (password.length < 8) {
            Toast.makeText(context, "Password must be at least 8 characters long", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (!password.any { it.isUpperCase() }) {
            Toast.makeText(context, "Password must contain at least one uppercase letter", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (!password.any { it.isLowerCase() }) {
            Toast.makeText(context, "Password must contain at least one lowercase letter", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (!password.any { it.isDigit() }) {
            Toast.makeText(context, "Password must contain at least one number", Toast.LENGTH_LONG).show()
            return false
        }
        
        val symbolPattern = "[!@#$%\\^&*()_+\\-=]".toRegex()
        if (!password.any { symbolPattern.matches(it.toString()) }) {
            Toast.makeText(context, "Password must contain at least one symbol (!@#$%^&*()_+-=)", Toast.LENGTH_LONG).show()
            return false
        }
        
        return true
    }
    
    //for checking if changing password is correct
    fun validatePasswordChange(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        context: Context
    ): Boolean {
        //fields are filled
        if (currentPassword.isBlank()) {
            Toast.makeText(context, "Please enter your current password", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (newPassword.isBlank()) {
            Toast.makeText(context, "Please enter a new password", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (confirmPassword.isBlank()) {
            Toast.makeText(context, "Please confirm your new password", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // new passwords match
        if (newPassword != confirmPassword) {
            Toast.makeText(context, "New passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // new password is different from current
        if (currentPassword == newPassword) {
            Toast.makeText(context, "New password must be different from current password", Toast.LENGTH_SHORT).show()
            return false
        }
        return isPasswordValid(newPassword, context)
    }
}