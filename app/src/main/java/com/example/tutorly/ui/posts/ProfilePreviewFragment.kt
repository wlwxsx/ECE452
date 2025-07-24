package com.example.tutorly.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.tutorly.R
import com.example.tutorly.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color

class ProfilePreviewFragment : DialogFragment() {
    private lateinit var userRepository: UserRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        userRepository = UserRepository.getInstance()
        return inflater.inflate(R.layout.fragment_profile_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val userId = arguments?.getString("userId") ?: return
        val profileInitial = view.findViewById<TextView>(R.id.text_profile_profile)
        val profileName = view.findViewById<TextView>(R.id.profile_name)
        val profilePronouns = view.findViewById<TextView>(R.id.profile_pronouns)
        val profileLikes = view.findViewById<TextView>(R.id.profile_likes)
        val profileBio = view.findViewById<TextView>(R.id.profile_bio)
        CoroutineScope(Dispatchers.IO).launch {
            userRepository.getUserById(userId)
                .onSuccess { user ->
                    withContext(Dispatchers.Main) {
                        if (user != null) {
                            if (user.name.isNotEmpty()) profileName.text = user.name
                            if (user.pronouns.isNotEmpty()) profilePronouns.text = user.pronouns
                            profileLikes.text = "Likes: ${user.likes}"
                            if (user.bio.isNotEmpty()) profileBio.text = user.bio
                            val firstInitial = if (user.name.isNotEmpty()) {
                                user.name.first().uppercase()
                            } else {
                                "?"
                            }
                            profileInitial.text = firstInitial
                            profileInitial.setBackgroundColor(Color.parseColor(user.profileColor))

                        } else {
                            profileName.text = "User not found"
                        }
                    }
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        profileName.text = "Error loading profile"
                    }
                }
        }
    }
}
