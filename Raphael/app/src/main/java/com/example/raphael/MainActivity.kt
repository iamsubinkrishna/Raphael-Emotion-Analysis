package com.example.raphael

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.raphael.adapter.ChatAdapter
import com.example.raphael.model.ChatMessage
import com.example.raphael.network.ApiClient
import com.example.raphael.network.EmotionDetectionRequest
import com.example.raphael.network.EmotionDetectionResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class MainActivity : AppCompatActivity(), LifecycleOwner {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var voiceInputButton: ImageButton
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))

        // Get token from intent
        intent.getStringExtra("token")?.let { token ->
            ApiClient.setAuthToken(token)
        }

        // Initialize views
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        voiceInputButton = findViewById(R.id.voiceInputButton)

        // Setup RecyclerView
        chatAdapter = ChatAdapter(messages)
        chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        // Send button click listener
        sendButton.setOnClickListener {
            sendMessage()
        }

        // Voice input button click listener
        voiceInputButton.setOnClickListener {
            startVoiceInput()
        }

        // Handle keyboard send button
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // Add welcome message
        addBotMessage("Hello! I'm Raphael, your emotion-aware assistant. How can I help you today?")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_analysis -> {
                startActivity(Intent(this, AnalysisActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isNotEmpty()) {
            // Add user message to chat
            addUserMessage(message)
            messageInput.text.clear()

            // Send to backend for emotion detection and response
            detectEmotionAndGetResponse(message)
        }
    }

    private fun addUserMessage(message: String) {
        val chatMessage = ChatMessage(message, true)
        chatAdapter.addMessage(chatMessage)
        chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun addBotMessage(message: String, emotion: String? = null) {
        val chatMessage = ChatMessage(message, false, emotion = emotion)
        chatAdapter.addMessage(chatMessage)
        chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun detectEmotionAndGetResponse(message: String) {
        val request = EmotionDetectionRequest(message)
        val call = ApiClient.service.detectEmotion(request)

        call.enqueue(object : Callback<EmotionDetectionResponse> {
            override fun onResponse(
                call: Call<EmotionDetectionResponse>,
                response: Response<EmotionDetectionResponse>
            ) {
                if (response.isSuccessful) {
                    val emotionResponse = response.body()
                    emotionResponse?.let {
                        addBotMessage(it.response, it.emotion)
                    } ?: run {
                        addBotMessage("I couldn't process that. Please try again.")
                    }
                } else {
                    addBotMessage("Sorry, there was an error processing your message.")
                }
            }

            override fun onFailure(call: Call<EmotionDetectionResponse>, t: Throwable) {
                addBotMessage("Connection error. Please check your internet connection.")
            }
        })
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }

        try {
            startActivityForResult(intent, VOICE_INPUT_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_INPUT_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.get(0)?.let { spokenText ->
                messageInput.setText(spokenText)
                sendMessage()
            }
        }
    }

    companion object {
        private const val VOICE_INPUT_REQUEST_CODE = 100
    }
}