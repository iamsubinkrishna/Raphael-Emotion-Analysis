package com.example.raphael

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.raphael.api.NetworkClient
import com.example.raphael.databinding.ActivityAnalysisBinding
import com.example.raphael.models.EmotionRequest
import com.example.raphael.models.SentimentRequest
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale

class AnalysisActivity : AppCompatActivity(), LifecycleOwner {
    private lateinit var binding: ActivityAnalysisBinding
    private val cameraPermissionCode = 100

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as Bitmap
            processImage(imageBitmap)
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = result.data?.data
            imageUri?.let {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, it))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, it)
                }
                processImage(bitmap)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnAnalyzeSentiment.setOnClickListener {
            val text = binding.etText.text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, getString(R.string.error_enter_text), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            analyzeSentiment(text)
        }

        binding.btnCamera.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }

        binding.btnGallery.setOnClickListener {
            openGallery()
        }

        binding.btnMoreTips.setOnClickListener {
            // Show more detailed tips based on the current emotion
            val currentEmotion = getCurrentEmotion()
            showMoreTips(currentEmotion)
        }
    }

    private fun analyzeSentiment(text: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val response = NetworkClient.analysisService.analyzeSentiment(SentimentRequest(text))
                if (response.isSuccessful) {
                    response.body()?.let { sentimentResponse ->
                        binding.tvSentimentResult.text = getString(
                            R.string.sentiment_result,
                            sentimentResponse.data?.sentiment ?: "",
                            sentimentResponse.data?.polarity ?: 0.0,
                            sentimentResponse.data?.subjectivity ?: 0.0
                        )
                        binding.tvSentimentResult.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(this@AnalysisActivity, getString(R.string.error_analysis_failed), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AnalysisActivity, getString(R.string.error_generic, e.message), Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val base64Image = bitmapToBase64(bitmap)
                val response = NetworkClient.analysisService.detectEmotion(EmotionRequest(base64Image))
                
                if (response.isSuccessful) {
                    response.body()?.let { emotionResponse ->
                        val emotions = emotionResponse.data?.emotions
                        if (emotions != null) {
                            // Format all emotions with percentages
                            val emotionText = emotions.entries.joinToString("\n") { (emotion, value) ->
                                String.format(
                                    "%s: %.2f%%",
                                    emotion.capitalize(Locale.getDefault()),
                                    value
                                )
                            }
                            binding.tvEmotionResult.text = emotionText
                            binding.tvEmotionResult.visibility = View.VISIBLE

                            // Find the dominant emotion for other UI updates
                            val dominantEmotion = emotions.maxByOrNull { it.value }
                            dominantEmotion?.let { (emotion, confidence) ->
                                // Update confidence meter
                                binding.confidenceLayout.visibility = View.VISIBLE
                                binding.confidenceMeter.progress = (confidence * 100).toInt()

                                // Update emoji
                                binding.emojiDisplay.visibility = View.VISIBLE
                                val emojiResource = when(emotion.toLowerCase()) {
                                    "happy", "joy" -> R.drawable.ic_happy
                                    "sad", "sadness" -> R.drawable.ic_sad
                                    else -> R.drawable.ic_neutral
                                }
                                binding.emojiDisplay.setImageResource(emojiResource)

                                // Show suggestion
                                binding.suggestionCard.visibility = View.VISIBLE
                                val suggestionResource = when(emotion.toLowerCase()) {
                                    "happy", "joy" -> R.string.suggestion_happy
                                    "sad", "sadness" -> R.string.suggestion_sad
                                    "angry", "anger" -> R.string.suggestion_angry
                                    "fear" -> R.string.suggestion_fear
                                    "surprise" -> R.string.suggestion_surprise
                                    else -> R.string.suggestion_neutral
                                }
                                binding.tvSuggestion.text = getString(suggestionResource)
                            }
                        }
                    }
                } else {
                    Toast.makeText(this@AnalysisActivity, getString(R.string.error_emotion_detection), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AnalysisActivity, getString(R.string.error_generic, e.message), Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            cameraPermissionCode
        )
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnAnalyzeSentiment.isEnabled = !show
        binding.btnCamera.isEnabled = !show
        binding.btnGallery.isEnabled = !show
        
        // Hide results when loading
        if (show) {
            binding.tvEmotionResult.visibility = View.GONE
            binding.confidenceLayout.visibility = View.GONE
            binding.emojiDisplay.visibility = View.GONE
            binding.suggestionCard.visibility = View.GONE
        }
    }

    private fun getCurrentEmotion(): String {
        return try {
            val emotionText = binding.tvEmotionResult.text.toString()
            emotionText.substringBefore(":").toLowerCase()
        } catch (e: Exception) {
            "neutral"
        }
    }

    private fun showMoreTips(emotion: String) {
        val tips = when(emotion) {
            "happy", "joy" -> arrayOf(
                "Write down what made you happy today",
                "Call a friend and share your joy",
                "Plan something fun for tomorrow",
                "Express gratitude to someone"
            )
            "sad", "sadness" -> arrayOf(
                "Take a walk in nature",
                "Listen to uplifting music",
                "Write down your feelings",
                "Reach out to a friend or counselor"
            )
            "angry", "anger" -> arrayOf(
                "Practice deep breathing exercises",
                "Write down what's bothering you",
                "Do some physical exercise",
                "Try progressive muscle relaxation"
            )
            "fear" -> arrayOf(
                "Break big challenges into smaller steps",
                "Practice grounding techniques",
                "Focus on what you can control",
                "Talk to someone you trust"
            )
            "surprise" -> arrayOf(
                "Take time to process the unexpected",
                "Write down your thoughts",
                "Consider different perspectives",
                "Share your experience with others"
            )
            else -> arrayOf(
                "Practice mindfulness meditation",
                "Set a small goal for today",
                "Try something new",
                "Connect with nature"
            )
        }
        
        // Show tips in a dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("More Tips")
            .setItems(tips, null)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 