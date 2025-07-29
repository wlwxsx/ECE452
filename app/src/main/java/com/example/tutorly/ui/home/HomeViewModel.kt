package com.example.tutorly.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tutorly.User
import com.example.tutorly.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val userRepository = UserRepository.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _user = MutableLiveData<User?>().apply {
        value = null
    }
    val user: LiveData<User?> = _user

    private val _name = MutableLiveData<String>().apply {
        value = ""
    }
    val name: LiveData<String> = _name

    init {
        loadUserData()
    }

    fun refreshUserData() {
        loadUserData(forceServer = true)
    }

    private fun loadUserData(forceServer: Boolean = false) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewModelScope.launch {
                userRepository.getUserById(currentUser.uid, forceServer)
                    .onSuccess { user ->
                        _user.value = user
                        _name.value = user?.name ?: "User Name"
                    }
                    .onFailure {
                        _user.value = null
                        _name.value = "Error loading name"
                    }
            }
        } else {
            _user.value = null
            _name.value = "Not logged in"
        }
    }
}