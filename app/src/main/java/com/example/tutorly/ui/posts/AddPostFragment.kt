package com.example.tutorly.ui.posts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.AutoCompleteTextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tutorly.R
import com.example.tutorly.utils.CourseCodeLoader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AddPostFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_add_post, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val postButton = view.findViewById<Button>(R.id.post_button)
        val titleInput = view.findViewById<EditText>(R.id.title_input)
        val subjectInput = view.findViewById<AutoCompleteTextView>(R.id.subject_input)
        val courseCodeSpinner = view.findViewById<Spinner>(R.id.course_code_input)
        val descriptionInput = view.findViewById<EditText>(R.id.description_input)

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
        subjectInput.setAdapter(subjectAdapter)
        subjectInput.threshold = 1

        // Setup course code Spinner (initially disabled)
        val courseCodeOptions = mutableListOf("Select Course Code")
        val courseCodeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, courseCodeOptions)
        courseCodeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        courseCodeSpinner.adapter = courseCodeAdapter

        // Subject selection listener
        subjectInput.addTextChangedListener(object : TextWatcher {
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
                updatePostButtonState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val inputWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePostButtonState()
            }
        }

        // Attach watcher to text inputs
        titleInput.addTextChangedListener(inputWatcher)
        descriptionInput.addTextChangedListener(inputWatcher)

        // Set initial disabled state
        postButton.isEnabled = false
        postButton.setBackgroundColor(android.graphics.Color.parseColor("#CCCCCC"))

        postButton.setOnClickListener {
            savePostToFirebase()
        }

        val backButton = view.findViewById<ImageButton>(R.id.back_button_top)
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun updatePostButtonState() {
        val postButton = view?.findViewById<Button>(R.id.post_button)
        val titleInput = view?.findViewById<EditText>(R.id.title_input)
        val subjectInput = view?.findViewById<AutoCompleteTextView>(R.id.subject_input)
        val courseCodeSpinner = view?.findViewById<Spinner>(R.id.course_code_input)
        val descriptionInput = view?.findViewById<EditText>(R.id.description_input)

        val allFilled = titleInput?.text?.isNotBlank() == true
                && subjectInput?.text?.isNotBlank() == true
                && courseCodeSpinner?.selectedItem?.toString() != "Select Course Code"
                && descriptionInput?.text?.isNotBlank() == true

        postButton?.isEnabled = allFilled
        postButton?.setBackgroundColor(
            if (allFilled) android.graphics.Color.parseColor("#4CAF50") // Active green
            else android.graphics.Color.parseColor("#CCCCCC")           // Disabled gray
        )
    }

    private fun savePostToFirebase() {
        val title = view?.findViewById<EditText>(R.id.title_input)?.text.toString()
        val courseName = view?.findViewById<AutoCompleteTextView>(R.id.subject_input)?.text.toString().uppercase()
        val courseCode = view?.findViewById<Spinner>(R.id.course_code_input)?.selectedItem?.toString() ?: ""
        val message = view?.findViewById<EditText>(R.id.description_input)?.text.toString()
        val isProviding = view?.findViewById<RadioButton>(R.id.providing_radio)?.isChecked ?: false
        val role = if (isProviding) "providing" else "requesting"

        val posterId = auth.currentUser?.uid

        if (posterId == null) {
            Toast.makeText(context, "You must be logged in to post.", Toast.LENGTH_SHORT).show()
            return
        }

        if (title.isNotEmpty() && courseName.isNotEmpty() && courseCode.isNotEmpty() && courseCode != "Select Course Code" && message.isNotEmpty()) {
            val post = hashMapOf(
                "title" to title,
                "courseName" to courseName,
                "courseCode" to courseCode,
                "message" to message,
                "role" to role,
                "posterId" to posterId,
                "matchedId" to "",
                "status" to Post.STATUS_ACTIVE,
                "timeStamp" to FieldValue.serverTimestamp()
            )

            db.collection("posts")
                .add(post)
                .addOnSuccessListener {
                    Toast.makeText(context, "Post successful!", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error saving post: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            Toast.makeText(context, "Please fill out all fields.", Toast.LENGTH_SHORT).show()
        }
    }
} 