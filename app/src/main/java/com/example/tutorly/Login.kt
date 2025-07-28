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
import com.example.tutorly.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Login : AppCompatActivity() {
    
    companion object {
        private const val TAG = "LoginActivity"
    }
    
    private lateinit var auth: FirebaseAuth
    private lateinit var editTextEmail: TextInputEditText
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var buttonLogin: Button
    private lateinit var textView: TextView

    // if user is logged in, go to the main menu page
    public override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            navigateToMainActivity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        
        auth = FirebaseAuth.getInstance()
        editTextEmail = findViewById(R.id.email)
        editTextPassword = findViewById(R.id.password)
        buttonLogin = findViewById(R.id.btn_login)
        textView = findViewById(R.id.goToRegister)

        //go to register page
        textView.setOnClickListener { view ->
            val intent = Intent(applicationContext, Register::class.java)
            startActivity(intent)
            finish()
        }
        
        //login button logic
        buttonLogin.setOnClickListener { view ->
            val email: String = editTextEmail.text.toString()
            val password: String = editTextPassword.text.toString()
            
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this@Login, "Enter email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (TextUtils.isEmpty(password)) {
                Toast.makeText(this@Login, "Enter password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            //firebase login, success and fails
            signInWithEmailAndPassword(email, password)
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun signInWithEmailAndPassword(email: String, password: String) {
        // Clear user cache before login
        UserRepository.getInstance().clearCache()
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, check if user is banned
                    val user = auth.currentUser
                    checkUserBanStatus(user)
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    val errorMessage = when {
                        task.exception?.message?.contains("UNAUTHENTICATED") == true -> 
                            "Authentication service unavailable. Please check your internet connection and try again."
                        task.exception?.message?.contains("network") == true -> 
                            "Network error. Please check your internet connection and try again."
                        task.exception?.message?.contains("blocked") == true -> 
                            "Authentication service temporarily unavailable. Please try again later."
                        else -> "Login failed. Please check your credentials and try again."
                    }
                    Toast.makeText(
                        baseContext,
                        errorMessage,
                        Toast.LENGTH_LONG,
                    ).show()
                    updateUI(null)
                }
            }
    }

    //navigate to main activity after login, will not switch page on failed logins
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
            navigateToMainActivity()
        }
    }

    private fun checkUserBanStatus(user: FirebaseUser?) {
        if (user == null) {
            updateUI(null)
            return
        }
        // Always fetch from server to get latest ban status
        CoroutineScope(Dispatchers.Main).launch {
            val result = UserRepository.getInstance().getUserById(user.uid, forceServer = true)
            val userObj = result.getOrNull()
            if (userObj?.isBanned == true) {
                auth.signOut()
                Toast.makeText(this@Login, "Your account has been banned.", Toast.LENGTH_LONG).show()
                updateUI(null)
            } else {
                updateUI(user)
            }
        }
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}