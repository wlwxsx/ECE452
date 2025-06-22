package com.example.tutorly.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.tutorly.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Get references to the new TextViews from the binding
        val nameTextView: TextView = binding.textProfileName
        val pronounsTextView: TextView = binding.textProfilePronouns
        val bioTextView: TextView = binding.textProfileAbout

        // Observe LiveData from ViewModel and update UI
        homeViewModel.name.observe(viewLifecycleOwner) {
            nameTextView.text = it
        }
        homeViewModel.pronouns.observe(viewLifecycleOwner) {
            pronounsTextView.text = it
        }
        homeViewModel.bio.observe(viewLifecycleOwner) {
            bioTextView.text = it
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}