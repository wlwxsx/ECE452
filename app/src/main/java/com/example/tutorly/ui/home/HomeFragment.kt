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

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var homeViewModel: HomeViewModel
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
        
        binding.logout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireContext(), Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
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
        updateProfileColor(binding.textProfileProfile, colorHex)
        lifecycleScope.launch {
            userRepository.updateUserProfileColor(currentUser.uid, colorHex)
                .onFailure {
                    withContext(Dispatchers.Main) {
                        // Revert to previous color on failure by refreshing from server
                        homeViewModel.refreshUserData()
                        Toast.makeText(context, "Failed to save color", Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}