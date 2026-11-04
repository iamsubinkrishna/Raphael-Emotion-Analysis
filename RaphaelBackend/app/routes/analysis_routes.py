from flask import Blueprint, request, jsonify
from textblob import TextBlob
from deepface import DeepFace
import cv2
import numpy as np
import base64
from PIL import Image
import io
import os
import nltk
from datetime import datetime, timedelta
from transformers import pipeline
from app.models import ChatMessage, User
from app.extensions import db
import jwt
from config import Config

# Download necessary NLTK data
try:
    nltk.data.find('tokenizers/punkt')
except LookupError:
    nltk.download('punkt')

analysis_bp = Blueprint('analysis', __name__)

# Initialize the emotion classifier
emotion_classifier = pipeline(
    "text-classification",
    model="j-hartmann/emotion-english-distilroberta-base",
    return_all_scores=True
)

UPLOAD_FOLDER = 'uploads'
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

def get_user_from_token():
    auth_header = request.headers.get('Authorization')
    if not auth_header:
        return None
    try:
        token = auth_header.split(' ')[1]  # Remove 'Bearer ' prefix
        data = jwt.decode(token, Config.SECRET_KEY, algorithms=['HS256'])
        return User.query.get(data['user_id'])
    except:
        return None

def get_chat_history(user_id, limit=5):
    return ChatMessage.query.filter_by(user_id=user_id).order_by(
        ChatMessage.timestamp.desc()
    ).limit(limit).all()

def save_base64_image(base64_string):
    # Remove header if present
    if 'base64,' in base64_string:
        base64_string = base64_string.split('base64,')[1]
    
    # Decode base64 string
    img_data = base64.b64decode(base64_string)
    
    # Generate unique filename
    filename = f"image_{datetime.now().strftime('%Y%m%d_%H%M%S')}.jpg"
    filepath = os.path.join(UPLOAD_FOLDER, filename)
    
    # Save image
    with open(filepath, 'wb') as f:
        f.write(img_data)
    
    return filepath

@analysis_bp.route('/analyze_sentiment', methods=['POST'])
def analyze_sentiment():
    try:
        data = request.get_json()
        if not data or 'text' not in data:
            return jsonify({
                'status': 'error',
                'message': 'No text provided'
            }), 400

        text = data['text']
        analysis = TextBlob(text)
        
        # Get polarity (-1 to 1) and subjectivity (0 to 1)
        polarity = analysis.sentiment.polarity
        subjectivity = analysis.sentiment.subjectivity
        
        # Determine sentiment category
        if polarity > 0:
            sentiment = 'positive'
        elif polarity < 0:
            sentiment = 'negative'
        else:
            sentiment = 'neutral'
        
        return jsonify({
            'status': 'success',
            'data': {
                'sentiment': sentiment,
                'polarity': polarity,
                'subjectivity': subjectivity,
                'text': text
            },
            'message': 'Analysis completed successfully'
        })
        
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500

@analysis_bp.route('/detect_emotion', methods=['POST'])
def detect_emotion():
    try:
        data = request.get_json()
        if not data or 'text' not in data:
            return jsonify({
                'response': 'No text provided',
                'emotion': 'neutral'
            }), 400

        text = data['text']
        user = get_user_from_token()
        
        if not user:
            return jsonify({
                'response': 'Authentication required',
                'emotion': 'neutral'
            }), 401

        # Get emotion predictions
        emotions = emotion_classifier(text)[0]
        
        # Get the strongest emotion
        strongest_emotion = max(emotions, key=lambda x: x['score'])
        
        # Store user message
        user_message = ChatMessage(
            user_id=user.id,
            content=text,
            emotion=strongest_emotion['label'],
            is_bot_response=False
        )
        db.session.add(user_message)
        
        # Get recent chat history
        chat_history = get_chat_history(user.id)
        
        # Generate a response based on the emotion and chat history
        responses = {
            'joy': [
                "I sense joy in your message! That's wonderful!",
                "Your happiness is contagious!",
                "It's great to see you in such high spirits!"
            ],
            'sadness': [
                "I can tell you're feeling down. Would you like to talk about it?",
                "I'm here to listen if you need support.",
                "It's okay to feel sad sometimes. Let's work through this together."
            ],
            'anger': [
                "I understand you're feeling angry. Let's work through this together.",
                "I hear your frustration. Would you like to discuss what's bothering you?",
                "Your feelings are valid. Let's find a way to address this."
            ],
            'fear': [
                "I can sense your concern. Remember, it's okay to feel this way.",
                "We can face this together. What specifically worries you?",
                "I'm here to help you through this."
            ],
            'surprise': [
                "That seems to have caught you by surprise!",
                "Unexpected things can be quite shocking!",
                "I understand your amazement!"
            ],
            'love': [
                "There's a lot of love in your words!",
                "It's beautiful to see such affection!",
                "Your caring nature really shows!"
            ],
            'neutral': [
                "I understand what you're saying.",
                "I hear you.",
                "Let's continue our conversation."
            ]
        }
        
        # Choose response based on emotion and previous messages
        emotion_responses = responses.get(strongest_emotion['label'], responses['neutral'])
        response_text = emotion_responses[len(chat_history) % len(emotion_responses)]
        
        # Add context based on chat history
        if chat_history:
            last_emotion = chat_history[0].emotion
            if last_emotion != strongest_emotion['label']:
                response_text += f" I notice your emotion has shifted from {last_emotion} to {strongest_emotion['label']}."
        
        # Store bot response
        bot_message = ChatMessage(
            user_id=user.id,
            content=response_text,
            emotion='neutral',
            is_bot_response=True
        )
        db.session.add(bot_message)
        db.session.commit()
        
        return jsonify({
            'response': response_text,
            'emotion': strongest_emotion['label']
        })
        
    except Exception as e:
        return jsonify({
            'response': f"Error processing message: {str(e)}",
            'emotion': 'neutral'
        }), 500

@analysis_bp.route('/detect_emotion_image', methods=['POST'])
def detect_emotion_image():
    try:
        data = request.get_json()
        if not data or 'image' not in data:
            return jsonify({
                'status': 'error',
                'message': 'No image provided'
            }), 400

        # Save base64 image
        image_path = save_base64_image(data['image'])
        
        # Analyze image with DeepFace
        analysis = DeepFace.analyze(
            img_path=image_path,
            actions=['emotion'],
            enforce_detection=False
        )
        
        # Clean up temporary file
        os.remove(image_path)
        
        # Extract emotions
        emotions = analysis[0]['emotion'] if isinstance(analysis, list) else analysis['emotion']
        
        return jsonify({
            'status': 'success',
            'data': {
                'emotions': emotions
            },
            'message': 'Analysis completed successfully'
        })
        
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500

@analysis_bp.route('/chat_history', methods=['GET'])
def get_user_chat_history():
    try:
        user = get_user_from_token()
        if not user:
            return jsonify({
                'status': 'error',
                'message': 'Authentication required'
            }), 401

        # Get the limit from query parameters, default to 50
        limit = request.args.get('limit', default=50, type=int)
        
        # Get chat history
        messages = ChatMessage.query.filter_by(user_id=user.id).order_by(
            ChatMessage.timestamp.asc()
        ).limit(limit).all()
        
        # Format messages
        chat_history = [{
            'id': msg.id,
            'content': msg.content,
            'emotion': msg.emotion,
            'timestamp': msg.timestamp.isoformat(),
            'is_bot_response': msg.is_bot_response
        } for msg in messages]
        
        return jsonify({
            'status': 'success',
            'data': chat_history
        })
        
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500 