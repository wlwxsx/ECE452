package com.example.tutorly.ui.posts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tutorly.Constants
import com.example.tutorly.R
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

        // Skip Firebase auth calls when bypassing for testing
        if (!Constants.BYPASS_AUTH_FOR_TESTING && auth.currentUser == null) {
            auth.createUserWithEmailAndPassword("admin@tutorly.com", "password")
        }

        val postButton = view.findViewById<Button>(R.id.post_button)
        val titleInput = view.findViewById<EditText>(R.id.title_input)
        val subjectInput = view.findViewById<EditText>(R.id.subject_input)
        val codeInput = view.findViewById<EditText>(R.id.course_code_input)
        val descriptionInput = view.findViewById<EditText>(R.id.description_input)

        val inputWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val allFilled = titleInput.text.isNotBlank()
                        && subjectInput.text.isNotBlank()
                        && codeInput.text.isNotBlank()
                        && descriptionInput.text.isNotBlank()

                postButton.isEnabled = allFilled
                postButton.setBackgroundColor(
                    if (allFilled) android.graphics.Color.parseColor("#4CAF50") // Active green
                    else android.graphics.Color.parseColor("#CCCCCC")           // Disabled gray
                )
            }
        }

        // Attach watcher to all inputs
        titleInput.addTextChangedListener(inputWatcher)
        subjectInput.addTextChangedListener(inputWatcher)
        codeInput.addTextChangedListener(inputWatcher)
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

    private fun savePostToFirebase() {
        val title = view?.findViewById<EditText>(R.id.title_input)?.text.toString()
        val courseName = view?.findViewById<EditText>(R.id.subject_input)?.text.toString().uppercase()
        val courseCode = view?.findViewById<EditText>(R.id.course_code_input)?.text.toString().uppercase()
        val message = view?.findViewById<EditText>(R.id.description_input)?.text.toString()
        val isProviding = view?.findViewById<RadioButton>(R.id.providing_radio)?.isChecked ?: false
        val role = if (isProviding) "providing" else "requesting"

        val posterId = if (Constants.BYPASS_AUTH_FOR_TESTING) {
            "testAdmin"
        } else {
            auth.currentUser?.uid
        }

        if (posterId == null) {
            Toast.makeText(context, "You must be logged in to post.", Toast.LENGTH_SHORT).show()
            return
        }

        if (title.isNotEmpty() && courseName.isNotEmpty() && courseCode.isNotEmpty() && message.isNotEmpty()) {
            val post = hashMapOf(
                "title" to title,
                "courseName" to courseName,
                "courseCode" to courseCode,
                "message" to message,
                "role" to role,
                "posterId" to posterId,
                "matchedId" to "",
                "status" to "active",
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