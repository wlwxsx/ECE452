package com.example.tutorly.ui.posts

import android.app.AlertDialog
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
import com.example.tutorly.ui.profile.ProfilePreviewFragment

class PostDetailFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var userRepository: UserRepository
    private var postId: String? = null
    private var currentPost: Post? = null
    private lateinit var commentInput: EditText
    private lateinit var postCommentButton: Button
    private lateinit var deletePostButton: Button
    private lateinit var reportUserButton: Button
    private lateinit var commentsRecyclerView: RecyclerView
    private lateinit var commentAdapter: CommentAdapter
    private lateinit var commentsLabel: TextView
    private val commentsList = mutableListOf<Map<String, Any>>() // Only for UI display, not persistent storage
    private var commentsListener: com.google.firebase.firestore.ListenerRegistration? = null

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
        deletePostButton = view.findViewById(R.id.delete_post_button)
        reportUserButton = view.findViewById(R.id.report_user_button)
        commentsRecyclerView = view.findViewById(R.id.comments_recycler_view)
        commentsLabel = view.findViewById(R.id.comments_label)

        setupCommentsRecyclerView()
        setupCommentInput()
        setupPostCommentButton()
        setupDeletePostButton()
        fetchPostDetails()
    }

    private fun setupDeletePostButton() {
        deletePostButton.setOnClickListener {
            showDeletePostConfirmation()
        }
    }

    private fun showDeletePostConfirmation() {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_delete_confirmation, null)
        
        //Update the popup text for post deletion
        val linearLayout = popupView as android.widget.LinearLayout
        (linearLayout.getChildAt(0) as? TextView)?.text = "Delete Post"
        (linearLayout.getChildAt(1) as? TextView)?.text = "I understand that this action cannot be undone and this post will be permanently deleted."
        
        val popup = AlertDialog.Builder(requireContext())
            .setView(popupView)
            .create()
        
        popup.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        //button onclick listeners
        popupView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            popup.dismiss()
        }
        
        popupView.findViewById<Button>(R.id.btn_delete).setOnClickListener {
            popup.dismiss()
            deletePost()
        }
        
        popup.show()
    }

    private fun deletePost() {
        val postId = postId ?: return
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        if (currentPost?.posterId != currentUserId) return

        db.collection("comments")
            .whereEqualTo("postId", postId)
            .get()
            .addOnSuccessListener { commentSnapshots ->
                val batch = db.batch()
                
                //add all comments to the batch for deletion
                for (document in commentSnapshots.documents) {
                    batch.delete(document.reference)
                }
                
                batch.commit()
                    .addOnSuccessListener {
                        db.collection("posts").document(postId)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                                findNavController().navigateUp()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Failed to delete post", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to delete comments", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to delete post", Toast.LENGTH_SHORT).show()
            }
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
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        commentAdapter = CommentAdapter(
            commentsList, 
            userRepository, 
            currentUserId, 
            currentPost?.posterId,
            currentPost?.status,
            ::onMatchButtonClick
        )
        commentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
            setHasFixedSize(false)
        }
    }

    private fun onMatchButtonClick(matchedUserId: String) {
        if (postId == null) {
            Toast.makeText(context, "Error: Post ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != currentPost?.posterId) {
            Toast.makeText(context, "Only the post owner can match with users", Toast.LENGTH_SHORT).show()
            return
        }

        // Update the post's matchedId field and status to matched in Firestore
        val updates = hashMapOf<String, Any>(
            "matchedId" to matchedUserId,
            "status" to Post.STATUS_MATCHED
        )
        
        db.collection("posts").document(postId!!)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Successfully matched with user!", Toast.LENGTH_SHORT).show()
                // Update local post object
                currentPost = currentPost?.copy(matchedId = matchedUserId, status = Post.STATUS_MATCHED)
                // Refresh comments to hide match buttons since post is now matched
                commentAdapter.notifyDataSetChanged()
                // Refresh the post to show updated UI
                fetchPostDetails()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error matching with user: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun closePost() {
        if (postId == null) {
            Toast.makeText(context, "Error: Post ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != currentPost?.posterId) {
            Toast.makeText(context, "Only the post owner can close the post", Toast.LENGTH_SHORT).show()
            return
        }

        // Update the post status to closed in Firestore
        val updates = hashMapOf<String, Any>(
            "status" to Post.STATUS_CLOSED
        )
        
        db.collection("posts").document(postId!!)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Post closed successfully!", Toast.LENGTH_SHORT).show()
                // Update local post object
                currentPost = currentPost?.copy(status = Post.STATUS_CLOSED)
                // Refresh the post to show updated UI
                fetchPostDetails()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error closing post: ${exception.message}", Toast.LENGTH_LONG).show()
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
        
        // Create comment data with proper server timestamp
        val commentData = hashMapOf(
            "content" to content,
            "postId" to postId,
            "userId" to userId,
            "timeStamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // Disable button while posting
        postCommentButton.isEnabled = false
        postCommentButton.text = "POSTING..."
        
        db.collection("comments")
            .add(commentData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(context, "Comment posted successfully!", Toast.LENGTH_SHORT).show()
                commentInput.setText("")
                postCommentButton.text = "POST"
                postCommentButton.isEnabled = false
                postCommentButton.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), android.R.color.darker_gray)
                
                // Refresh comments after posting
                fetchComments()
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
                    currentPost = post
                    
                    // check if current user posted this post
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                    if (currentUserId != null && post?.posterId == currentUserId) {
                        //user posted this post, show delete button, hide report button
                        deletePostButton.visibility = View.VISIBLE
                        reportUserButton.visibility = View.GONE
                    } else {
                        //user didn't post this post, hide delete button, show report button
                        deletePostButton.visibility = View.GONE
                        reportUserButton.visibility = View.VISIBLE
                    }
                    
                    view?.let { view ->
                        view.findViewById<TextView>(R.id.post_number_header).text = "POST #${document.id.take(6).uppercase()}"
                        view.findViewById<TextView>(R.id.post_title).text = post?.title
                        view.findViewById<TextView>(R.id.post_message).text = post?.message
                        val sdf = SimpleDateFormat("MM/dd/yyyy - h:mm a", Locale.getDefault())
                        view.findViewById<TextView>(R.id.post_timestamp).text = "Posted ${post?.timeStamp?.let { sdf.format(it) } ?: "Unknown"}"
                        
                        // Show closed badge if post is closed
                        val closedBadge = view.findViewById<TextView>(R.id.closed_badge)
                        if (post?.status == Post.STATUS_CLOSED) {
                            closedBadge.visibility = View.VISIBLE
                        } else {
                            closedBadge.visibility = View.GONE
                        }
                        
                        // Handle report user button and close post button visibility
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                        val isPostOwner = currentUserId == post?.posterId
                        val reportUserButton = view.findViewById<Button>(R.id.report_user_button)
                        val closePostButton = view.findViewById<Button>(R.id.close_post_button)
                        val authorInfoTextView = view.findViewById<TextView>(R.id.author_info)

                        // Show close post button only if user is post owner and post is matched
                        if (isPostOwner && post?.status == Post.STATUS_MATCHED) {
                            closePostButton.visibility = View.VISIBLE
                            closePostButton.setOnClickListener {
                                closePost()
                            }
                        } else {
                            closePostButton.visibility = View.GONE
                        }

                        if (isPostOwner) {
                            reportUserButton.visibility = View.GONE
                            // Expand author info to full width when report button is hidden
                            val params = authorInfoTextView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                            params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                            params.marginEnd = 0
                            authorInfoTextView.layoutParams = params
                        } else {
                            reportUserButton.visibility = View.VISIBLE
                            // Constrain author info to leave space for report button
                            val params = authorInfoTextView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                            params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                            params.endToStart = R.id.report_user_button
                            params.marginEnd = 20
                            authorInfoTextView.layoutParams = params
                        }
                        
                        // Fetch and display post author name
                        if (post?.posterId?.isNotBlank() == true) {
                            authorInfoTextView.text = "Posted by: Loading..."
                            authorInfoTextView.setOnClickListener {
                                val dialog = ProfilePreviewFragment().apply {
                                    arguments = Bundle().apply {
                                        putString("userId", post.posterId)
                                    }
                                }
                                dialog.show(parentFragmentManager, "profile_preview")
                            }
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
                                    .onFailure { exception ->
                                        withContext(Dispatchers.Main) {
                                            authorInfoTextView.text = "Posted by: User_${post.posterId.take(6)}"
                                        }
                                    }
                            }
                        } else {
                            authorInfoTextView.text = "Posted by: Anonymous"
                        }
                    }
                    
                    // Update adapter with post owner information after post is loaded
                    setupCommentsRecyclerView()
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

        // Remove any existing listener to avoid duplicates
        commentsListener?.remove()

        // Set up real-time listener for comments with balanced caching
        commentsListener = db.collection("comments")
            .whereEqualTo("postId", postId!!)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(context, "Error fetching comments: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    android.util.Log.d("PostDetail", "Comments received: ${snapshots.documents.size} comments")
                    // Clear the local list and populate from Firebase data
                    commentsList.clear()
                    for (document in snapshots.documents) {
                        // Work directly with Firebase document data
                        val commentData = document.data
                        if (commentData != null) {
                            commentsList.add(commentData)
                        }
                    }
                    
                    // Sort comments by timestamp manually (oldest first)
                    commentsList.sortBy { commentData ->
                        val timestamp = commentData["timeStamp"] as? com.google.firebase.Timestamp
                        timestamp?.toDate()?.time ?: 0L
                    }
                    
                    // Show/hide comments section header based on whether there are comments
                    if (commentsList.isEmpty()) {
                        commentsLabel.visibility = View.GONE
                    } else {
                        commentsLabel.visibility = View.VISIBLE
                    }
                    
                    // Notify adapter that data has changed
                    commentAdapter.notifyDataSetChanged()
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up the Firebase listener to prevent memory leaks
        commentsListener?.remove()
    }
} 