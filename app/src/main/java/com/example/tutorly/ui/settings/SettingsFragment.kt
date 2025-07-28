package com.example.tutorly.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.tutorly.Login
import com.example.tutorly.R
import com.example.tutorly.UserRepository
import com.example.tutorly.utils.PasswordValidator
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: com.example.tutorly.databinding.FragmentSettingBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsViewModel: SettingsViewModel
    private val userRepository = UserRepository.getInstance()
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    // UI elements
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
        _binding = com.example.tutorly.databinding.FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        checkAdminStatus()
    }

    private fun setupUI() {
        // Setup existing UI elements
        initializeViews(binding.root)
        setupClickListeners()
        setupTextWatchers()

        val textView: TextView = binding.textSetting
        settingsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
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
            deleteAccount()
        }
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateChangePasswordButtonState()
            }
        }

        currentPasswordField.addTextChangedListener(textWatcher)
        newPasswordField.addTextChangedListener(textWatcher)
        confirmPasswordField.addTextChangedListener(textWatcher)
    }

    private fun updateChangePasswordButtonState() {
        val currentPassword = currentPasswordField.text.toString()
        val newPassword = newPasswordField.text.toString()
        val confirmPassword = confirmPasswordField.text.toString()

        val allFieldsFilled = currentPassword.isNotEmpty() && 
                             newPassword.isNotEmpty() && 
                             confirmPassword.isNotEmpty()

        changePasswordButton.isEnabled = allFieldsFilled
        changePasswordButton.setBackgroundTintList(
            if (allFieldsFilled) 
                androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.button_gray)
            else 
                androidx.core.content.ContextCompat.getColorStateList(requireContext(), android.R.color.darker_gray)
        )
    }

    private fun changePassword() {
        val currentPassword = currentPasswordField.text.toString()
        val newPassword = newPasswordField.text.toString()
        val confirmPassword = confirmPasswordField.text.toString()

        if (newPassword != confirmPassword) {
            Toast.makeText(context, "New passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        if (!PasswordValidator.isPasswordValid(newPassword, requireContext())) {
            return
        }

        val user = auth.currentUser
        if (user != null) {
            val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
            user.reauthenticate(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        user.updatePassword(newPassword)
                            .addOnCompleteListener { updateTask ->
                                if (updateTask.isSuccessful) {
                                    Toast.makeText(context, "Password changed successfully", Toast.LENGTH_SHORT).show()
                                    clearPasswordFields()
                                } else {
                                    Toast.makeText(context, "Failed to change password", Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        Toast.makeText(context, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun deleteAccount() {
        val user = auth.currentUser
        if (user != null) {
            // Delete user data from Firestore first
            db.collection("users").document(user.uid)
                .delete()
                .addOnSuccessListener {
                    // Then delete the Firebase Auth account
                    user.delete()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                                navigateToLogin()
                            } else {
                                Toast.makeText(context, "Failed to delete account", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to delete account data", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun clearPasswordFields() {
        currentPasswordField.text.clear()
        newPasswordField.text.clear()
        confirmPasswordField.text.clear()
    }

    private fun navigateToLogin() {
        val intent = android.content.Intent(requireContext(), Login::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun checkAdminStatus() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val isAdmin = userRepository.isUserAdmin(currentUserId).getOrNull() ?: false
                withContext(Dispatchers.Main) {
                    if (isAdmin) {
                        showAdminButton()
                    }
                }
            }
        }
    }

    private fun showAdminButton() {
        val adminButton = Button(requireContext()).apply {
            text = "Admin Panel"
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.button_gray))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                findNavController().navigate(R.id.action_navigation_settings_to_adminFragment)
            }
        }
        
        // Add admin button to the LinearLayout that contains the other buttons
        val deleteAccountButton = binding.root.findViewById<Button>(R.id.btn_delete_account)
        val parent = deleteAccountButton.parent as? ViewGroup
        parent?.addView(adminButton)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 