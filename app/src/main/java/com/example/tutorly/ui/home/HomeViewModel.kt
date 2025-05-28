package com.example.tutorly.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    private val _name = MutableLiveData<String>().apply {
        value = "John Smith"
    }
    val name: LiveData<String> = _name

    private val _pronouns = MutableLiveData<String>().apply {
        value = "He/Him"
    }
    val pronouns: LiveData<String> = _pronouns

    private val _bio = MutableLiveData<String>().apply {
        value = "..." // You can put a more descriptive placeholder here
    }
    val bio: LiveData<String> = _bio
}