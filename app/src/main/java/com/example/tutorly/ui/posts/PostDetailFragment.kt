package com.example.tutorly.ui.posts

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tutorly.R
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class PostDetailFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private var postId: String? = null
    private lateinit var commentInput: EditText
    private lateinit var postCommentButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            postId = it.getString("postId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        db = FirebaseFirestore.getInstance()
        return inflater.inflate(R.layout.fragment_post_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        commentInput = view.findViewById(R.id.comment_input)
        postCommentButton = view.findViewById(R.id.post_comment_button)

        setupCommentInput()
        setupPostCommentButton()
        fetchPostDetails()
    }

    private fun setupCommentInput() {
        commentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val isContentNotEmpty = s?.toString()?.trim()?.isNotEmpty() == true
                postCommentButton.isEnabled = isContentNotEmpty
                postCommentButton.backgroundTintList = if (isContentNotEmpty) {
                    androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.button_gray)
                } else {
                    androidx.core.content.ContextCompat.getColorStateList(requireContext(), android.R.color.darker_gray)
                }
            }
        })
    }

    private fun setupPostCommentButton() {
        postCommentButton.setOnClickListener {
            postComment()
        }
    }

    private fun postComment() {
        val content = commentInput.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(context, "Please enter a comment", Toast.LENGTH_SHORT).show()
            return
        }

        if (postId == null) {
            Toast.makeText(context, "Error: Post ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        // For now, using a random UUID as userId. In a real app, you'd get this from authentication
        val userId = UUID.randomUUID().toString()
        
        val comment = Comment(
            content = content,
            postId = postId!!,
            userId = userId
        )

        // Disable button while posting
        postCommentButton.isEnabled = false
        postCommentButton.text = "POSTING..."

        db.collection("comments")
            .add(comment)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(context, "Comment posted successfully!", Toast.LENGTH_SHORT).show()
                commentInput.setText("")
                postCommentButton.text = "POST"
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error posting comment: ${exception.message}", Toast.LENGTH_LONG).show()
                postCommentButton.isEnabled = true
                postCommentButton.text = "POST"
            }
    }

    private fun fetchPostDetails() {
        if (postId == null) {
            Toast.makeText(context, "Post not found.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("posts").document(postId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val post = document.toObject(Post::class.java)
                    view?.let {
                        it.findViewById<TextView>(R.id.post_number_header).text = "POST #${document.id.take(6).uppercase()}"
                        it.findViewById<TextView>(R.id.post_title).text = post?.title
                        it.findViewById<TextView>(R.id.author_info).text = "Posted by: ${post?.posterId?.take(6)}..."
                        it.findViewById<TextView>(R.id.post_message).text = post?.message
                        val sdf = SimpleDateFormat("MM/dd/yyyy - h:mm a", Locale.getDefault())
                        it.findViewById<TextView>(R.id.post_timestamp).text = "Posted ${sdf.format(post?.timeStamp)}"
                    }
                } else {
                    Toast.makeText(context, "Post not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error getting post details: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }
} 