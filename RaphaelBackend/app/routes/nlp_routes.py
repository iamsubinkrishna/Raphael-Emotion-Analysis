from flask import Blueprint, request, jsonify
from ..nlp.pipeline import NLPPipeline
import os
from werkzeug.utils import secure_filename

nlp_bp = Blueprint('nlp', __name__)
nlp_pipeline = NLPPipeline()

UPLOAD_FOLDER = 'uploads'
ALLOWED_EXTENSIONS = {'wav', 'mp3'}

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

@nlp_bp.route('/process', methods=['POST'])
def process_input():
    try:
        if 'file' in request.files:
            # Handle voice input
            file = request.files['file']
            if file and allowed_file(file.filename):
                filename = secure_filename(file.filename)
                filepath = os.path.join(UPLOAD_FOLDER, filename)
                file.save(filepath)
                
                # Process voice input
                result = nlp_pipeline.process_input(filepath, input_type='voice')
                
                # Clean up the file
                os.remove(filepath)
                return jsonify(result)
            return jsonify({'error': 'Invalid file format'}), 400
        
        # Handle text input
        data = request.get_json()
        if not data or 'text' not in data:
            return jsonify({'error': 'No text provided'}), 400
        
        result = nlp_pipeline.process_input(data['text'], input_type='text')
        return jsonify(result)

    except Exception as e:
        return jsonify({'error': str(e)}), 500 