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
import android.widget.AutoCompleteTextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorly.R
import com.example.tutorly.databinding.FragmentDashboardBinding
import com.example.tutorly.ui.posts.Post
import com.example.tutorly.utils.CourseCodeLoader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var postsRecyclerView: RecyclerView
    private lateinit var postsList: ArrayList<Post>
    private lateinit var postAdapter: PostAdapter
    private lateinit var myPostsButton: Button
    private lateinit var userIdInput: EditText
    private lateinit var filterUserIdButton: Button
    private var isCurrentUserAdmin: Boolean = false
    private var filterByUserId: String? = null

    private var currentSubject: String? = null
    private var currentCourseCode: String? = null
    private var currentHelpType: String? = null
    private var showMyPostsOnly: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val addPostButton: ImageButton = binding.addPostButton
        addPostButton.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_dashboard_to_addPostFragment)
        }

        val filterButton: ImageButton = binding.filterButton
        filterButton.setOnClickListener {
            showFilterDialog()
        }

        myPostsButton = binding.myPostsButton
        // Remove userIdInput and filterUserIdButton from header
        myPostsButton.visibility = View.GONE

        // Check admin status and show appropriate controls
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val isAdmin = com.example.tutorly.UserRepository.getInstance().isUserAdmin(currentUserId).getOrNull() == true
                isCurrentUserAdmin = isAdmin
                if (!isAdmin) {
                    myPostsButton.visibility = View.VISIBLE
                    myPostsButton.setOnClickListener {
                        toggleMyPosts()
                    }
                    updateMyPostsButtonAppearance()
                }
            }
        } else {
            myPostsButton.visibility = View.VISIBLE
            myPostsButton.setOnClickListener {
                toggleMyPosts()
            }
            updateMyPostsButtonAppearance()
        }

        postsRecyclerView = binding.postsRecyclerView
        postsRecyclerView.layoutManager = LinearLayoutManager(context)
        postsRecyclerView.setHasFixedSize(true)

        postsList = arrayListOf()
        postAdapter = PostAdapter(postsList)
        postsRecyclerView.adapter = postAdapter

        fetchPosts()

        return root
    }

    private fun toggleMyPosts() {
        showMyPostsOnly = !showMyPostsOnly
        updateMyPostsButtonAppearance()
        fetchPosts()
    }

    private fun updateMyPostsButtonAppearance() {
        if (showMyPostsOnly) {
            myPostsButton.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.button_gray)
            myPostsButton.setTextColor(android.graphics.Color.WHITE)
        } else {
            myPostsButton.setBackgroundResource(R.drawable.edit_text_background)
            myPostsButton.backgroundTintList = null
            myPostsButton.setTextColor(android.graphics.Color.BLACK)
        }
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_filter, null)
        val builder = AlertDialog.Builder(context)
            .setView(dialogView)
            .setTitle("Filter Posts")
        
        val alertDialog = builder.show()

        val subjectAutoComplete = dialogView.findViewById<AutoCompleteTextView>(R.id.subject_filter_autocomplete)
        val courseCodeSpinner = dialogView.findViewById<Spinner>(R.id.course_code_filter_spinner)
        val helpTypeRadioGroup = dialogView.findViewById<RadioGroup>(R.id.help_type_radio_group)
        val userIdFilterInput = dialogView.findViewById<EditText>(R.id.user_id_filter_input)

        // Show user ID filter only for admins
        if (isCurrentUserAdmin) {
            userIdFilterInput.visibility = View.VISIBLE
            userIdFilterInput.setText(filterByUserId ?: "")
        } else {
            userIdFilterInput.visibility = View.GONE
        }

        // Load course codes from JSON
        val courseCodes = CourseCodeLoader.loadCourseCodes(requireContext())
        val subjects = CourseCodeLoader.getSubjects()
        
        // Setup subject AutoCompleteTextView with custom adapter
        val subjectAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, subjects.toMutableList()) {
            override fun getFilter(): android.widget.Filter {
                return object : android.widget.Filter() {
                    override fun performFiltering(constraint: CharSequence?): android.widget.Filter.FilterResults {
                        val results = android.widget.Filter.FilterResults()
                        val filteredList = mutableListOf<String>()
                        
                        if (constraint.isNullOrEmpty()) {
                            // Show all subjects when field is empty
                            filteredList.addAll(subjects)
                        } else {
                            // Filter subjects based on constraint (minimum 1 character)
                            val filterPattern = constraint.toString().lowercase().trim()
                            for (subject in subjects) {
                                if (subject.lowercase().contains(filterPattern)) {
                                    filteredList.add(subject)
                                }
                            }
                        }
                        
                        results.values = filteredList
                        results.count = filteredList.size
                        return results
                    }
                    
                    override fun publishResults(constraint: CharSequence?, results: android.widget.Filter.FilterResults?) {
                        clear()
                        if (results?.values != null) {
                            addAll(results.values as List<String>)
                        }
                        notifyDataSetChanged()
                    }
                }
            }
        }
        subjectAutoComplete.setAdapter(subjectAdapter)
        
        // Set threshold to 1 for minimum characters before filtering
        subjectAutoComplete.threshold = 1

        // Setup course code Spinner (initially disabled)
        val courseCodeOptions = mutableListOf("Select Course Code")
        val courseCodeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, courseCodeOptions)
        courseCodeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        courseCodeSpinner.adapter = courseCodeAdapter

        // Set current values
        if (!currentSubject.isNullOrEmpty()) {
            subjectAutoComplete.setText(currentSubject)
            // Enable course code spinner and populate it
            courseCodeSpinner.isEnabled = true
            val courseCodesForSubject = CourseCodeLoader.getCourseCodesForSubject(currentSubject!!)
            courseCodeOptions.clear()
            courseCodeOptions.add("Select Course Code")
            courseCodeOptions.addAll(courseCodesForSubject)
            courseCodeAdapter.notifyDataSetChanged()
            
            // Set current course code
            if (!currentCourseCode.isNullOrEmpty()) {
                val courseCodePosition = courseCodeOptions.indexOf(currentCourseCode)
                if (courseCodePosition >= 0) {
                    courseCodeSpinner.setSelection(courseCodePosition)
                }
            }
        }
        
        when(currentHelpType) {
            "providing" -> helpTypeRadioGroup.check(R.id.offering_radio_filter)
            "requesting" -> helpTypeRadioGroup.check(R.id.requesting_radio_filter)
            else -> helpTypeRadioGroup.check(R.id.any_radio_filter)
        }

        // Subject selection listener
        subjectAutoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val selectedSubject = s.toString().trim()
                if (selectedSubject.isNotEmpty() && subjects.contains(selectedSubject)) {
                    // Enable course code spinner and populate it
                    courseCodeSpinner.isEnabled = true
                    val courseCodesForSubject = CourseCodeLoader.getCourseCodesForSubject(selectedSubject)
                    courseCodeOptions.clear()
                    courseCodeOptions.add("Select Course Code")
                    courseCodeOptions.addAll(courseCodesForSubject)
                    courseCodeAdapter.notifyDataSetChanged()
                    courseCodeSpinner.setSelection(0) // Reset to "Select Course Code"
                } else {
                    // Disable course code spinner
                    courseCodeSpinner.isEnabled = false
                    courseCodeOptions.clear()
                    courseCodeOptions.add("Select Course Code")
                    courseCodeAdapter.notifyDataSetChanged()
                    courseCodeSpinner.setSelection(0)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogView.findViewById<Button>(R.id.apply_filters_button).setOnClickListener {
            val selectedSubject = subjectAutoComplete.text.toString().trim()
            currentSubject = if (selectedSubject.isNotEmpty() && subjects.contains(selectedSubject)) selectedSubject else null
            
            val selectedCourseCode = courseCodeSpinner.selectedItem.toString()
            currentCourseCode = if (selectedCourseCode != "Select Course Code") selectedCourseCode else null
            
            val selectedHelpTypeId = helpTypeRadioGroup.checkedRadioButtonId
            val helpTypeRadioButton = dialogView.findViewById<RadioButton>(selectedHelpTypeId)
            currentHelpType = when (helpTypeRadioButton.text.toString()) {
                "Offering" -> "providing"
                "Requesting" -> "requesting"
                else -> null
            }

            // Get user ID filter for admins
            filterByUserId = if (isCurrentUserAdmin) userIdFilterInput.text.toString().trim().ifEmpty { null } else null
            showMyPostsOnly = false // disable my posts filter if using user ID
            fetchPosts()
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.clear_filters_button).setOnClickListener {
            currentSubject = null
            currentCourseCode = null
            currentHelpType = null
            filterByUserId = null
            showMyPostsOnly = false
            updateMyPostsButtonAppearance()
            fetchPosts()
            alertDialog.dismiss()
        }
    }

    private fun fetchPosts() {
        var query: Query = db.collection("posts")
        val filterCount = listOf(currentSubject, currentCourseCode, currentHelpType).count { !it.isNullOrEmpty() }

        // Admin: filter by entered user ID
        if (isCurrentUserAdmin && !filterByUserId.isNullOrEmpty()) {
            query = query.whereEqualTo("posterId", filterByUserId)
        } else if (showMyPostsOnly) {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId != null) {
                query = query.whereEqualTo("posterId", currentUserId)
            } else {
                Toast.makeText(context, "Please log in to view your posts", Toast.LENGTH_SHORT).show()
                showMyPostsOnly = false
                updateMyPostsButtonAppearance()
                return
            }
        }

        if (!currentSubject.isNullOrEmpty()) {
            query = query.whereEqualTo("courseName", currentSubject!!.uppercase())
        }

        if (!currentCourseCode.isNullOrEmpty()) {
            query = query.whereEqualTo("courseCode", currentCourseCode)
        }

        if (!currentHelpType.isNullOrEmpty()) {
            query = query.whereEqualTo("role", currentHelpType)
        }

        val totalFilterCount = filterCount + if (showMyPostsOnly || (isCurrentUserAdmin && !filterByUserId.isNullOrEmpty())) 1 else 0
        if (totalFilterCount != 1) {
            query = query.orderBy("timeStamp", Query.Direction.DESCENDING)
        }

        query.get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    postsList.clear()
                    val fetchedPosts = result.toObjects(Post::class.java)
                    val currentUserId = auth.currentUser?.uid

                    // Filter posts based on visibility rules
                    val filteredPosts = fetchedPosts.filter { post ->
                        when (post.status) {
                            Post.STATUS_ACTIVE -> true // Open posts are visible to everyone
                            Post.STATUS_MATCHED -> {
                                // Matched posts are only visible to poster and matched user
                                currentUserId == post.posterId || currentUserId == post.matchedId
                            }
                            Post.STATUS_CLOSED -> {
                                // Closed posts are only visible to poster
                                currentUserId == post.posterId
                            }
                            else -> true // Default to visible for any other status
                        }
                    }

                    if (totalFilterCount == 1) {
                        postsList.addAll(filteredPosts.sortedByDescending { it.timeStamp })
                    } else {
                        postsList.addAll(filteredPosts)
                    }
                    
                    postAdapter.notifyDataSetChanged()
                    
                    if ((showMyPostsOnly || (isCurrentUserAdmin && !filterByUserId.isNullOrEmpty())) && postsList.isEmpty()) {
                        Toast.makeText(context, "No posts found for this user.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    postsList.clear()
                    postAdapter.notifyDataSetChanged()
                    if (showMyPostsOnly || (isCurrentUserAdmin && !filterByUserId.isNullOrEmpty())) {
                        Toast.makeText(context, "No posts found for this user.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No posts found.", Toast.LENGTH_SHORT).show()
                    }
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