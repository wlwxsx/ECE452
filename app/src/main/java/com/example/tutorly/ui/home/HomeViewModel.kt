package com.example.tutorly.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tutorly.Constants
import com.example.tutorly.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val userRepository = UserRepository.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _name = MutableLiveData<String>().apply {
        value = ""
    }
    val name: LiveData<String> = _name

    init {
        loadUserData()
    }

    private fun loadUserData() {
        // Handle bypass mode for testing
        if (Constants.BYPASS_AUTH_FOR_TESTING) {
            _name.value = "Test User (Bypassed)"
            return
        }
        
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewModelScope.launch {
                userRepository.getUserById(currentUser.uid)
                    .onSuccess { user ->
                        _name.value = user?.name ?: "User Name"
                    }
                    .onFailure {
                        _name.value = "Error loading name"
                    }
            }
        } else {
            _name.value = "Not logged in"
        }
    }
}