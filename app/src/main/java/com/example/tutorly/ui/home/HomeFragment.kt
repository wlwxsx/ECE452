package com.example.tutorly.ui.home

import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.example.tutorly.Login
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.tutorly.databinding.FragmentHomeBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.SetOptions

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val nameTextView: TextView = binding.textProfileName
        homeViewModel.name.observe(viewLifecycleOwner) { nameTextView.text = it }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserProfileData()
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
                    if (documentSnapshot.exists()) {
                        binding.textProfileName.setText(documentSnapshot.getString("name"))
                        binding.textProfilePronouns.setText(documentSnapshot.getString("pronouns"))
                        binding.textProfileBio.setText(documentSnapshot.getString("bio"))
                    } else { Toast.makeText(context, "No profile created yet. Please save.", Toast.LENGTH_SHORT).show() }
                }
                .addOnFailureListener { e -> Toast.makeText(context, "Failed to load profile: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun saveUserProfileUpdates() {
        val currentUser: FirebaseUser? = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, "No user logged in. Cannot save.", Toast.LENGTH_LONG).show()
            return
        }
        val userId = currentUser.uid
        val name = binding.textProfileName.text.toString().trim()
        val pronouns = binding.textProfilePronouns.text.toString().trim()
        val bio = binding.textProfileBio.text.toString().trim()
        if (name.isEmpty()) {
            binding.textProfileName.error = "Name cannot be empty"
            binding.textProfileName.requestFocus()
            return
        }
        val userUpdates = hashMapOf<String, Any>(
            "name" to name,
            "pronouns" to pronouns,
            "bio" to bio
        )
        db.collection("users").document(userId)
            .set(userUpdates, SetOptions.merge())
            .addOnSuccessListener { Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { e -> Toast.makeText(context, "Error updating profile: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}