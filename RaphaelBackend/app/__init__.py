from flask import Flask
from flask_cors import CORS
from app.extensions import db, bcrypt
from config import Config
import os
from app.routes.nlp_routes import nlp_bp
import logging

logger = logging.getLogger(__name__)

def create_app():
    logger.info("Creating Flask application...")
    app = Flask(__name__)
    app.config.from_object(Config)
    
    # Enable CORS
    logger.info("Enabling CORS...")
    CORS(app)
    
    # Ensure the upload folder exists
    logger.info("Setting up upload folder...")
    upload_folder = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'uploads')
    os.makedirs(upload_folder, exist_ok=True)

    # Configure the app
    logger.info("Configuring application...")
    app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///app.db'
    app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
    app.config['UPLOAD_FOLDER'] = upload_folder
    
    # Initialize extensions
    logger.info("Initializing extensions...")
    db.init_app(app)
    bcrypt.init_app(app)
    
    # Register blueprints
    logger.info("Registering blueprints...")
    from app.routes.auth_routes import auth_bp
    from app.routes.analysis_routes import analysis_bp
    app.register_blueprint(nlp_bp, url_prefix='/api/nlp')
    app.register_blueprint(auth_bp, url_prefix='/auth')
    app.register_blueprint(analysis_bp, url_prefix='/analysis')
    
    # Create database tables
    logger.info("Creating database tables...")
    with app.app_context():
        try:
            db.create_all()
            logger.info("Database tables created successfully")
        except Exception as e:
            logger.error(f"Error creating database tables: {str(e)}", exc_info=True)
    
    logger.info("Application creation completed")
    return app