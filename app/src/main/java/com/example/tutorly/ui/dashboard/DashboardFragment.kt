package com.example.tutorly.ui.dashboard

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorly.R
import com.example.tutorly.databinding.FragmentDashboardBinding
import com.example.tutorly.ui.posts.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var postsRecyclerView: RecyclerView
    private lateinit var postsList: ArrayList<Post>
    private lateinit var postAdapter: PostAdapter

    private var currentSubject: String? = null
    private var currentCourseCode: String? = null
    private var currentHelpType: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val addPostButton: ImageButton = binding.addPostButton
        addPostButton.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_dashboard_to_addPostFragment)
        }

        val filterButton: ImageButton = binding.filterButton
        filterButton.setOnClickListener {
            showFilterDialog()
        }

        db = FirebaseFirestore.getInstance()

        postsRecyclerView = binding.postsRecyclerView
        postsRecyclerView.layoutManager = LinearLayoutManager(context)
        postsRecyclerView.setHasFixedSize(true)

        postsList = arrayListOf()
        postAdapter = PostAdapter(postsList)
        postsRecyclerView.adapter = postAdapter

        fetchPosts()

        return root
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_filter, null)
        val builder = AlertDialog.Builder(context)
            .setView(dialogView)
            .setTitle("Filter Posts")
        
        val alertDialog = builder.show()

        val subjectInput = dialogView.findViewById<EditText>(R.id.subject_filter_input)
        val courseCodeInput = dialogView.findViewById<EditText>(R.id.course_code_filter_input)
        val helpTypeRadioGroup = dialogView.findViewById<RadioGroup>(R.id.help_type_radio_group)

        subjectInput.setText(currentSubject)
        courseCodeInput.setText(currentCourseCode)
        when(currentHelpType) {
            "providing" -> helpTypeRadioGroup.check(R.id.offering_radio_filter)
            "requesting" -> helpTypeRadioGroup.check(R.id.requesting_radio_filter)
            else -> helpTypeRadioGroup.check(R.id.any_radio_filter)
        }

        dialogView.findViewById<Button>(R.id.apply_filters_button).setOnClickListener {
            currentSubject = subjectInput.text.toString().trim()
            currentCourseCode = courseCodeInput.text.toString().trim()
            val selectedHelpTypeId = helpTypeRadioGroup.checkedRadioButtonId
            val helpTypeRadioButton = dialogView.findViewById<RadioButton>(selectedHelpTypeId)
            currentHelpType = when (helpTypeRadioButton.text.toString()) {
                "Offering" -> "providing"
                "Requesting" -> "requesting"
                else -> null
            }
            
            fetchPosts()
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.clear_filters_button).setOnClickListener {
            currentSubject = null
            currentCourseCode = null
            currentHelpType = null
            fetchPosts()
            alertDialog.dismiss()
        }
    }

    private fun fetchPosts() {
        var query: Query = db.collection("posts")
        val filterCount = listOf(currentSubject, currentCourseCode, currentHelpType).count { !it.isNullOrEmpty() }

        if (!currentSubject.isNullOrEmpty()) {
            query = query.whereEqualTo("courseName", currentSubject!!.uppercase())
        }

        if (!currentCourseCode.isNullOrEmpty()) {
            query = query.whereEqualTo("courseCode", currentCourseCode)
        }

        if (!currentHelpType.isNullOrEmpty()) {
            query = query.whereEqualTo("role", currentHelpType)
        }

        if (filterCount != 1) {
            query = query.orderBy("timeStamp", Query.Direction.DESCENDING)
        }

        query.get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    postsList.clear()
                    val fetchedPosts = result.toObjects(Post::class.java)

                    if (filterCount == 1) {
                        postsList.addAll(fetchedPosts.sortedByDescending { it.timeStamp })
                    } else {
                        postsList.addAll(fetchedPosts)
                    }
                    
                    postAdapter.notifyDataSetChanged()
                } else {
                    postsList.clear()
                    postAdapter.notifyDataSetChanged()
                    Toast.makeText(context, "No posts found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("DashboardFragment", "Error getting posts", exception)
                Toast.makeText(context, "Error getting posts: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}