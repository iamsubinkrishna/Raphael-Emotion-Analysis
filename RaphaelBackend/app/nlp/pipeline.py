import speech_recognition as sr
from textblob import TextBlob
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer
import spacy
from transformers import pipeline
import torch
import logging

# Initialize logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class NLPPipeline:
    def __init__(self):
        # Initialize components
        self.speech_recognizer = sr.Recognizer()
        self.sentiment_analyzer = SentimentIntensityAnalyzer()
        self.nlp = spacy.load("en_core_web_sm")
        self.emotion_classifier = pipeline(
            "text-classification",
            model="j-hartmann/emotion-english-distilroberta-base",
            return_all_scores=True
        )

    def process_voice_input(self, audio_file_path):
        """Convert voice input to text"""
        try:
            with sr.AudioFile(audio_file_path) as source:
                audio = self.speech_recognizer.record(source)
                text = self.speech_recognizer.recognize_google(audio)
                return text
        except Exception as e:
            logger.error(f"Error processing voice input: {str(e)}")
            return None

    def analyze_sentiment(self, text):
        """Analyze sentiment using VADER and TextBlob"""
        try:
            # VADER sentiment
            vader_scores = self.sentiment_analyzer.polarity_scores(text)
            
            # TextBlob sentiment
            blob = TextBlob(text)
            textblob_sentiment = {
                'polarity': blob.sentiment.polarity,
                'subjectivity': blob.sentiment.subjectivity
            }
            
            return {
                'vader': vader_scores,
                'textblob': textblob_sentiment
            }
        except Exception as e:
            logger.error(f"Error in sentiment analysis: {str(e)}")
            return None

    def analyze_emotion(self, text):
        """Analyze emotions using transformer model"""
        try:
            emotions = self.emotion_classifier(text)
            return emotions[0]
        except Exception as e:
            logger.error(f"Error in emotion analysis: {str(e)}")
            return None

    def extract_context(self, text):
        """Extract contextual information using spaCy"""
        try:
            doc = self.nlp(text)
            context = {
                'entities': [(ent.text, ent.label_) for ent in doc.ents],
                'key_phrases': [chunk.text for chunk in doc.noun_chunks],
                'main_verbs': [token.lemma_ for token in doc if token.pos_ == "VERB"]
            }
            return context
        except Exception as e:
            logger.error(f"Error extracting context: {str(e)}")
            return None

    def generate_response(self, text, sentiment, emotions, context):
        """Generate context-aware response based on analysis"""
        try:
            # Determine overall emotional tone
            vader_compound = sentiment['vader']['compound']
            primary_emotion = max(emotions, key=lambda x: x['score'])

            # Basic response template based on sentiment and emotion
            if vader_compound >= 0.05:
                tone = "positive"
            elif vader_compound <= -0.05:
                tone = "negative"
            else:
                tone = "neutral"

            response = {
                'tone': tone,
                'primary_emotion': primary_emotion['label'],
                'confidence': primary_emotion['score'],
                'context': context,
                'suggested_response_type': self._get_response_type(tone, primary_emotion['label'])
            }

            return response
        except Exception as e:
            logger.error(f"Error generating response: {str(e)}")
            return None

    def _get_response_type(self, tone, emotion):
        """Determine appropriate response type based on emotional context"""
        response_types = {
            'positive': {
                'joy': 'enthusiastic',
                'surprise': 'excited',
                'default': 'positive'
            },
            'negative': {
                'anger': 'calming',
                'sadness': 'empathetic',
                'fear': 'reassuring',
                'default': 'supportive'
            },
            'neutral': {
                'default': 'balanced'
            }
        }
        
        return response_types.get(tone, {}).get(emotion, response_types[tone]['default'])

    def process_input(self, input_data, input_type='text'):
        """Main pipeline processing function"""
        try:
            # Convert voice to text if needed
            if input_type == 'voice':
                text = self.process_voice_input(input_data)
                if not text:
                    return {'error': 'Voice processing failed'}
            else:
                text = input_data

            # Run analysis pipeline
            sentiment = self.analyze_sentiment(text)
            emotions = self.analyze_emotion(text)
            context = self.extract_context(text)

            # Generate response
            response = self.generate_response(text, sentiment, emotions, context)

            return {
                'input_text': text,
                'sentiment_analysis': sentiment,
                'emotion_analysis': emotions,
                'context_analysis': context,
                'response_suggestion': response
            }

        except Exception as e:
            logger.error(f"Error in pipeline processing: {str(e)}")
            return {'error': str(e)} 