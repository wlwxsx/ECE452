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

class Register : AppCompatActivity() {
    
    companion object {
        private const val TAG = "RegisterActivity"
    }
    
    // Firebase Auth
    private lateinit var auth: FirebaseAuth
    
    // UI element declarations
    private lateinit var editTextEmail: TextInputEditText
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var buttonReg: Button
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
        setContentView(R.layout.activity_register)
        
        auth = FirebaseAuth.getInstance()
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
            val email: String = editTextEmail.text.toString()
            val password: String = editTextPassword.text.toString()
            
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this@Register, "Enter email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (TextUtils.isEmpty(password)) {
                Toast.makeText(this@Register, "Enter password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            //firebase register, success and fails
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this@Register) { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "createUserWithEmail:success")
                        val user = auth.currentUser
                        updateUI(user)
                    } else {
                        Log.w(TAG, "createUserWithEmail:failure", task.exception)
                        val errorMessage = "Registration failed. Please check your credentials and try again."
                        Toast.makeText(
                            baseContext,
                            errorMessage,
                            Toast.LENGTH_LONG,
                        ).show()
                        updateUI(null)
                    }
                }
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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