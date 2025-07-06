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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorly.R
import com.example.tutorly.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class PostDetailFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var userRepository: UserRepository
    private var postId: String? = null
    private lateinit var commentInput: EditText
    private lateinit var postCommentButton: Button
    private lateinit var commentsRecyclerView: RecyclerView
    private lateinit var commentAdapter: CommentAdapter
    private val commentsList = mutableListOf<Comment>()

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
        userRepository = UserRepository.getInstance()
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
        commentsRecyclerView = view.findViewById(R.id.comments_recycler_view)

        setupCommentsRecyclerView()
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
                
                // Update button color based on content
                if (isContentNotEmpty) {
                    postCommentButton.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.button_gray)
                } else {
                    postCommentButton.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), android.R.color.darker_gray)
                }
            }
        })
    }

    private fun setupPostCommentButton() {
        postCommentButton.setOnClickListener {
            postComment()
        }
    }

    private fun setupCommentsRecyclerView() {
        commentAdapter = CommentAdapter(commentsList, userRepository)
        commentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
            setHasFixedSize(false)
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

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "You must be logged in to comment.", Toast.LENGTH_SHORT).show()
            return
        }
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
                postCommentButton.isEnabled = false
                postCommentButton.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), android.R.color.darker_gray)
                fetchComments() // Refresh comments after posting
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error posting comment: ${exception.message}", Toast.LENGTH_LONG).show()
                postCommentButton.isEnabled = true
                postCommentButton.text = "POST"
                postCommentButton.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.button_gray)
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
                    view?.let { view ->
                        view.findViewById<TextView>(R.id.post_number_header).text = "POST #${document.id.take(6).uppercase()}"
                        view.findViewById<TextView>(R.id.post_title).text = post?.title
                        view.findViewById<TextView>(R.id.post_message).text = post?.message
                        val sdf = SimpleDateFormat("MM/dd/yyyy - h:mm a", Locale.getDefault())
                        view.findViewById<TextView>(R.id.post_timestamp).text = "Posted ${sdf.format(post?.timeStamp)}"
                        
                        // Fetch and display post author name
                        val authorInfoTextView = view.findViewById<TextView>(R.id.author_info)
                        if (post?.posterId?.isNotBlank() == true) {
                            authorInfoTextView.text = "Posted by: Loading..."
                            
                            CoroutineScope(Dispatchers.IO).launch {
                                userRepository.getUserById(post.posterId)
                                    .onSuccess { user ->
                                        withContext(Dispatchers.Main) {
                                            if (user != null && user.name.isNotBlank()) {
                                                authorInfoTextView.text = "Posted by: ${user.name}"
                                            } else {
                                                authorInfoTextView.text = "Posted by: User_${post.posterId.take(6)}"
                                            }
                                        }
                                    }
                                    .onFailure {
                                        withContext(Dispatchers.Main) {
                                            authorInfoTextView.text = "Posted by: User_${post.posterId.take(6)}"
                                        }
                                    }
                            }
                        } else {
                            authorInfoTextView.text = "Posted by: Anonymous"
                        }
                    }
                    fetchComments() // Fetch comments after post details are loaded
                } else {
                    Toast.makeText(context, "Post not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error getting post details: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun fetchComments() {
        if (postId == null) return

        db.collection("comments")
            .whereEqualTo("postId", postId!!)
            .get()
            .addOnSuccessListener { result ->
                commentsList.clear()
                for (document in result) {
                    val comment = document.toObject(Comment::class.java)
                    commentsList.add(comment)
                }
                // Sort manually by timestamp (oldest first)
                commentsList.sortBy { it.timeStamp }
                commentAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error fetching comments: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }
} 