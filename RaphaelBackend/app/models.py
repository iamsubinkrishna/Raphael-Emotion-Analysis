from app.extensions import db

class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=False)
    password = db.Column(db.String(200), nullable=False)
    messages = db.relationship('ChatMessage', backref='user', lazy=True)

    def __repr__(self):
        return f'<User {self.username}>'

class ChatMessage(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    content = db.Column(db.Text, nullable=False)
    emotion = db.Column(db.String(50))
    timestamp = db.Column(db.DateTime, nullable=False, default=db.func.current_timestamp())
    is_bot_response = db.Column(db.Boolean, default=False)

    def __repr__(self):
        return f'<ChatMessage {self.id} from user {self.user_id}>'