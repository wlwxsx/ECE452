package com.example.tutorly.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.tutorly.Login
import com.example.tutorly.R
import com.example.tutorly.utils.PasswordValidator
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class SettingsFragment : Fragment() {

    companion object {
        private const val TAG = "SettingsFragment"
    }

    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    //ui variables
    private lateinit var currentPasswordField: EditText
    private lateinit var newPasswordField: EditText
    private lateinit var confirmPasswordField: EditText
    private lateinit var changePasswordButton: Button
    private lateinit var deleteAccountButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        settingsViewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val root = inflater.inflate(R.layout.fragment_setting, container, false)

        initializeViews(root)
        setupClickListeners()
        setupTextWatchers()

        val textView: TextView = root.findViewById(R.id.text_setting)
        settingsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        
        return root
    }

    private fun initializeViews(root: View) {
        currentPasswordField = root.findViewById(R.id.current_password)
        newPasswordField = root.findViewById(R.id.new_password)
        confirmPasswordField = root.findViewById(R.id.confirm_password)
        changePasswordButton = root.findViewById(R.id.btn_change_password)
        deleteAccountButton = root.findViewById(R.id.btn_delete_account)
    }

    private fun setupClickListeners() {
        changePasswordButton.setOnClickListener {
            changePassword()
        }

        deleteAccountButton.setOnClickListener {
            showDeleteAccountConfirmation()
        }
    }

    private fun setupTextWatchers() {
        val passwordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val allFilled = currentPasswordField.text.isNotBlank()
                        && newPasswordField.text.isNotBlank()
                        && confirmPasswordField.text.isNotBlank()

                changePasswordButton.isEnabled = allFilled
                changePasswordButton.setBackgroundColor(
                    if (allFilled) Color.parseColor("#4CAF50")
                    else Color.parseColor("#CCCCCC")
                )
            }
        }

        //checking if password inputs are filled
        currentPasswordField.addTextChangedListener(passwordWatcher)
        newPasswordField.addTextChangedListener(passwordWatcher)
        confirmPasswordField.addTextChangedListener(passwordWatcher)

        //initial states and colors
        changePasswordButton.isEnabled = false
        changePasswordButton.setBackgroundColor(Color.parseColor("#CCCCCC"))
    }

    private fun changePassword() {
        val currentPassword = currentPasswordField.text.toString().trim()
        val newPassword = newPasswordField.text.toString().trim()  
        val confirmPassword = confirmPasswordField.text.toString().trim()

        //check password
        if (!PasswordValidator.validatePasswordChange(currentPassword, newPassword, confirmPassword, requireContext())) {
            return
        }

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        //reauthenticate user with current password
        val email = user.email ?: ""
        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        user.reauthenticate(credential)
            .addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    Log.d(TAG, "Re-authentication successful, proceeding with password update")
                    user.updatePassword(newPassword)
                        .addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                Log.d(TAG, "Password updated successfully")
                                Log.d(TAG, "User still authenticated: ${auth.currentUser != null}")
                                Toast.makeText(context, "Password changed successfully!", Toast.LENGTH_LONG).show()
                                clearPasswordFields()
                            } else {
                                Log.e(TAG, "Failed to update password", updateTask.exception)
                                Toast.makeText(context, "Failed to change password: ${updateTask.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    Log.e(TAG, "Re-authentication failed", reauthTask.exception)
                    Toast.makeText(context, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun clearPasswordFields() {
        currentPasswordField.text?.clear()
        newPasswordField.text?.clear()
        confirmPasswordField.text?.clear()
    }

    private fun showDeleteAccountConfirmation() {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_delete_confirmation, null)
        
        val popup = AlertDialog.Builder(requireContext())
            .setView(popupView)
            .create()
        
        popup.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        //button onclick listeners
        popupView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            popup.dismiss()
        }
        
        popupView.findViewById<Button>(R.id.btn_delete).setOnClickListener {
            popup.dismiss()
            deleteAccount()
        }
        
        popup.show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = user.uid

        //delete user data from Firestore
        db.collection("users").document(userId)
            .delete()
            .addOnCompleteListener { firestoreTask ->
                if (firestoreTask.isSuccessful) {
                    Log.d(TAG, "User data deleted from Firestore")
                    
                    //delete the Firebase Auth account
                    Log.d(TAG, "Attempting to delete Firebase Auth account for user: ${user.uid}")
                    user.delete()
                        .addOnCompleteListener { deleteTask ->
                            if (deleteTask.isSuccessful) {
                                Log.d(TAG, "Firebase Auth account deleted successfully for user: ${user.uid}")
                                Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                                // signing out and going back to login page
                                auth.signOut()
                                navigateToLogin()
                            } else {
                                Log.e(TAG, "Failed to delete Firebase Auth account for user: ${user.uid}", deleteTask.exception)
                                Log.e(TAG, "Error details: ${deleteTask.exception?.localizedMessage}")
                                Toast.makeText(context, "Failed to delete account: ${deleteTask.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    Log.e(TAG, "Failed to delete user data from Firestore", firestoreTask.exception)
                    Toast.makeText(context, "Failed to delete user data: ${firestoreTask.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
} 