package com.example.tutorly.ui.home

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.google.firebase.auth.FirebaseAuth
import com.example.tutorly.Login
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.tutorly.R
import com.example.tutorly.UserRepository
import com.example.tutorly.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userRepository: UserRepository

    companion object {
        private const val DEFAULT_COLOR = "#4CAF50"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        userRepository = UserRepository.getInstance()

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // get reference to UI elements
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val nameTextView: TextView = binding.textProfileName
        val profileTextView: TextView = binding.textProfileProfile
        val editColorButton: ImageButton = binding.btnEditProfileColor

        //Set up edit color button click listener
        editColorButton.setOnClickListener {
            showColorPickerPopup()
        }

        // Observe LiveData from ViewModel and update UI
        homeViewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                nameTextView.text = user.name

                //profile is first initial in the profile circle
                val firstInitial = if (user.name.isNotEmpty()) {
                    user.name.first().uppercase()
                } else {
                    "?"
                }
                profileTextView.text = firstInitial

                //Update profile color
                updateProfileColor(profileTextView, user.profileColor)
            }
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserProfileData()
        checkAdminStatus()
        homeViewModel.name.observe(viewLifecycleOwner) { nameString -> binding.textProfileName.setText(nameString) }
        binding.logout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireContext(), Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        binding.buttonSaveProfile.setOnClickListener { saveUserProfileUpdates() }
    }

    private fun loadUserProfileData() {
        val currentUser: FirebaseUser? = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            db.collection("users").document(userId).get()
                .addOnSuccessListener { documentSnapshot ->
                    // Check if fragment is still attached and binding is available
                    if (!isAdded || _binding == null) return@addOnSuccessListener
                    
                    if (documentSnapshot.exists()) {
                        _binding?.textProfileName?.setText(documentSnapshot.getString("name"))
                        _binding?.textProfilePronouns?.setText(documentSnapshot.getString("pronouns"))
                        _binding?.textProfileBio?.setText(documentSnapshot.getString("bio"))
                        
                        // Load and display like count
                        val likes = documentSnapshot.getLong("likes")?.toInt() ?: 0
                        _binding?.likeCountText?.text = likes.toString()
                        
                        // Check and display admin status
                        val isAdmin = documentSnapshot.getBoolean("isAdmin") ?: false
                        if (isAdmin) {
                            _binding?.adminBadge?.visibility = View.VISIBLE
                        } else {
                            _binding?.adminBadge?.visibility = View.GONE
                        }
                        
                        // Update profile color from Firestore data
                        val profileColor = documentSnapshot.getString("profileColor") ?: DEFAULT_COLOR
                        _binding?.textProfileProfile?.let { profileTextView ->
                            updateProfileColor(profileTextView, profileColor)
                        }
                    } else { 
                        Toast.makeText(context, "No profile created yet. Please save.", Toast.LENGTH_SHORT).show() 
                    }
                }
                .addOnFailureListener { e -> 
                    if (isAdded) {
                        Toast.makeText(context, "Failed to load profile: ${e.message}", Toast.LENGTH_LONG).show() 
                    }
                }
        }
    }

    private fun saveUserProfileUpdates() {
        val currentUser: FirebaseUser? = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, "No user logged in. Cannot save.", Toast.LENGTH_LONG).show()
            return
        }
        
        // Check if binding is available
        if (_binding == null) return
        
        val userId = currentUser.uid
        val name = _binding?.textProfileName?.text?.toString()?.trim() ?: ""
        val pronouns = _binding?.textProfilePronouns?.text?.toString()?.trim() ?: ""
        val bio = _binding?.textProfileBio?.text?.toString()?.trim() ?: ""
        
        if (name.isEmpty()) {
            _binding?.textProfileName?.error = "Name cannot be empty"
            _binding?.textProfileName?.requestFocus()
            return
        }
        
        val userUpdates = hashMapOf<String, Any>(
            "name" to name,
            "pronouns" to pronouns,
            "bio" to bio
        )
        db.collection("users").document(userId)
            .set(userUpdates, SetOptions.merge())
            .addOnSuccessListener { 
                if (isAdded) {
                    Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show() 
                }
            }
            .addOnFailureListener { e -> 
                if (isAdded) {
                    Toast.makeText(context, "Error updating profile: ${e.message}", Toast.LENGTH_LONG).show() 
                }
            }
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.refreshUserData()
    }

    private fun showColorPickerPopup() {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_color_picker, null)

        val popup = AlertDialog.Builder(requireContext())
            .setView(popupView)
            .create()

        popup.window?.setBackgroundDrawableResource(android.R.color.transparent)

        //button click listeners
        val colorMap = mapOf(
            R.id.color_red to "#F44336",
            R.id.color_orange to "#FF9800",
            R.id.color_yellow to "#FFEB3B",
            R.id.color_pink to "#E91E63",
            R.id.color_purple to "#9C27B0",
            R.id.color_green to DEFAULT_COLOR,
            R.id.color_teal to "#009688",
            R.id.color_blue to "#2196F3",
            R.id.color_brown to "#795548",
            R.id.color_grey to "#607D8B"
        )

        colorMap.forEach { (buttonId, colorHex) ->
            popupView.findViewById<Button>(buttonId).setOnClickListener {
                saveProfileColor(colorHex)
                popup.dismiss()
            }
        }

        popupView.findViewById<Button>(R.id.btn_cancel_color).setOnClickListener {
            popup.dismiss()
        }

        popup.show()
    }

    private fun saveProfileColor(colorHex: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        
        // Check if binding is available
        if (_binding == null) return
        
        updateProfileColor(_binding!!.textProfileProfile, colorHex)
        lifecycleScope.launch {
            userRepository.updateUserProfileColor(currentUser.uid, colorHex)
                .onSuccess {
                    userRepository.clearUserFromCache(currentUser.uid)
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        // Check if fragment is still attached before updating UI
                        if (isAdded && _binding != null) {
                            // Revert to previous color on failure by refreshing from server
                            homeViewModel.refreshUserData()
                            Toast.makeText(context, "Failed to save color", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
    }

    private fun updateProfileColor(profileTextView: TextView, colorHex: String) {
        val newDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(colorHex))
            setStroke(12, Color.WHITE)
        }
        profileTextView.background = newDrawable
    }

    private fun checkAdminStatus() {
        val currentUser: FirebaseUser? = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            CoroutineScope(Dispatchers.IO).launch {
                val isAdmin = userRepository.isUserAdmin(userId).getOrNull() ?: false
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        if (isAdmin) {
                            _binding?.adminBadge?.visibility = View.VISIBLE
                        } else {
                            _binding?.adminBadge?.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}