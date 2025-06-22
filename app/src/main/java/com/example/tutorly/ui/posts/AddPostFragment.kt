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

        if (auth.currentUser == null) {
            auth.createUserWithEmailAndPassword("admin@tutorly.com", "password")
        }

        val postButton = view.findViewById<Button>(R.id.post_button)
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
        val role = if (isProviding) "providing" else "looking for"

//        val posterId = auth.currentUser?.uid

        //temp
        val posterId = "testAdmin";

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