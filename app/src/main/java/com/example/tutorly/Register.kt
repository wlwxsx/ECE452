package com.example.tutorly

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class Register : AppCompatActivity() {
    
    companion object {
        private const val TAG = "RegisterActivity"
    }
    
    //firebase Auth and UserRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository
    
    // UI element declarations
    private lateinit var editTextName: TextInputEditText
    private lateinit var editTextEmail: TextInputEditText
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var buttonReg: Button
    private lateinit var textView: TextView

    // if user is logged in, go to the main menu page
    public override fun onStart() {
        super.onStart()
        // Skip Firebase user check when bypassing auth for testing
        if (Constants.BYPASS_AUTH_FOR_TESTING) {
            return
        }
        val currentUser = auth.currentUser
        if (currentUser != null) {
            navigateToMainActivity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        
        auth = FirebaseAuth.getInstance()
        userRepository = UserRepository.getInstance()
        
        // Initialize UI elements
        editTextName = findViewById(R.id.name)
        editTextEmail = findViewById(R.id.email)
        editTextPassword = findViewById(R.id.password)
        buttonReg = findViewById(R.id.btn_register)
        textView = findViewById(R.id.goToLogin)

        //go to login page
        textView.setOnClickListener { view ->
            val intent = Intent(applicationContext, Login::class.java)
            startActivity(intent)
            finish()
        }

        //register button logic
        buttonReg.setOnClickListener { view ->
            registerUser()
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun registerUser() {
        val name = editTextName.text.toString().trim()
        val email = editTextEmail.text.toString().trim()
        val password = editTextPassword.text.toString()
        
        if (!validateInputs(name, email, password)) {
            return
        }
        
        // Bypass Firebase Auth for testing
        if (Constants.BYPASS_AUTH_FOR_TESTING) {
            Log.d(TAG, "Bypassing Firebase Auth for testing")
            navigateToMainActivity()
            return
        }
        
        // creating users with firebase auth
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this@Register) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:success")
                    val firebaseUser = auth.currentUser
                    firebaseUser?.let {
                        val user = User(
                            userid = it.uid,
                            name = name,
                            program = "",
                            year = "",
                            email = email,
                            contact = "",
                            bio = "",
                            availability = Availability(),
                            isAdmin = false,
                            likes = 0,
                            password = "",
                            tutoredCourses = emptyList()
                        )
                        
                        saveUserProfile(user)
                    }
                } else {
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    val errorMessage = "Registration failed. Please check your credentials and try again."
                    Toast.makeText(
                        baseContext,
                        errorMessage,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
    }
    
    private fun validateInputs(name: String, email: String, password: String): Boolean {
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Enter your name", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Enter email", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (!email.endsWith("@uwaterloo.ca")) {
            Toast.makeText(this, "Email must be a valid @uwaterloo.ca address", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter password", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return true
    }
    
    private fun saveUserProfile(user: User) {
        lifecycleScope.launch {
            userRepository.saveUser(user)
                .onSuccess {
                    Log.d(TAG, "User profile saved successfully")
                    updateUI(auth.currentUser)
                }
                .onFailure { e ->
                    Log.w(TAG, "Error saving user profile", e)
                    Toast.makeText(this@Register, "Failed to save user profile. Please try again.", 
                        Toast.LENGTH_LONG).show()
                    auth.signOut()
                }
        }
    }

    //navigate to main activity after register, will not switch page on errors
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
            navigateToMainActivity()
        }
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}