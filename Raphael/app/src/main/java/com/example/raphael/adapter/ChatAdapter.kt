package com.example.raphael.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.raphael.R
import com.example.raphael.model.ChatMessage
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val senderText: TextView = itemView.findViewById(R.id.senderText)
        val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        val emotionIcon: ImageView = itemView.findViewById(R.id.emotionIcon)
        val messageCardView: MaterialCardView = itemView.findViewById(R.id.messageCardView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        holder.messageText.text = message.message
        holder.senderText.text = if (message.isUser) "You" else "Raphael"
        holder.timestampText.text = SimpleDateFormat("hh:mm a", Locale.getDefault())
            .format(message.timestamp)

        // Set different background colors for user and bot messages
        val context = holder.itemView.context

        if (message.isUser) {
            holder.messageCardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.user_message_bg))
            holder.senderText.setTextColor(ContextCompat.getColor(context, R.color.user_message_text))
        } else {
            holder.messageCardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bot_message_bg))
            holder.senderText.setTextColor(ContextCompat.getColor(context, R.color.bot_message_text))

            // Show emotion icon if available
            message.emotion?.let { emotion ->
                holder.emotionIcon.visibility = View.VISIBLE
                val emotionIcon = when (emotion.toLowerCase()) {
                    "happy" -> R.drawable.ic_happy
                    "sad" -> R.drawable.ic_sad
                    "angry" -> R.drawable.ic_angry
                    "surprise" -> R.drawable.ic_surprise
                    "fear" -> R.drawable.ic_fear
                    "neutral" -> R.drawable.ic_neutral
                    else -> R.drawable.ic_neutral
                }
                holder.emotionIcon.setImageResource(emotionIcon)
            } ?: run {
                holder.emotionIcon.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_BOT
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}