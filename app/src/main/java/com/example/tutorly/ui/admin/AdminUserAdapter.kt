package com.example.tutorly.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorly.R
import com.example.tutorly.User

class AdminUserAdapter(
    private val users: List<User>,
    private val onActionClick: (User, Action) -> Unit
) : RecyclerView.Adapter<AdminUserAdapter.UserViewHolder>() {

    enum class Action {
        BAN, UNBAN
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.user_name)
        val userEmail: TextView = view.findViewById(R.id.user_email)
        val userStatus: TextView = view.findViewById(R.id.user_status)
        val banButton: Button = view.findViewById(R.id.ban_button)
        val unbanButton: Button = view.findViewById(R.id.unban_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        
        holder.userName.text = user.name
        holder.userEmail.text = user.email
        holder.userStatus.text = if (user.isBanned) "BANNED" else "ACTIVE"
        holder.userStatus.setTextColor(
            if (user.isBanned) 
                holder.itemView.context.getColor(android.R.color.holo_red_dark)
            else 
                holder.itemView.context.getColor(android.R.color.holo_green_dark)
        )

        // Show/hide buttons based on ban status
        holder.banButton.visibility = if (user.isBanned) View.GONE else View.VISIBLE
        holder.unbanButton.visibility = if (user.isBanned) View.VISIBLE else View.GONE

        holder.banButton.setOnClickListener {
            onActionClick(user, Action.BAN)
        }

        holder.unbanButton.setOnClickListener {
            onActionClick(user, Action.UNBAN)
        }
    }

    override fun getItemCount() = users.size
} 