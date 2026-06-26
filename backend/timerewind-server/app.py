from flask import Flask, request, jsonify
import whisper
import os
import subprocess
import uuid
import json
import faiss
import numpy as np
from sentence_transformers import SentenceTransformer
import datetime
import tempfile
import shutil
from emotion_detector import detect_emotion, detect_voice_stress
import psutil
import threading
import time
import requests

app = Flask(__name__)

# Hugging Face API Configuration
HUGGINGFACE_API_KEY = "hHF_TOKEN = os.getenv("HF_TOKEN")"
HUGGINGFACE_API_URL = "https://api-inference.huggingface.co/models/gpt2"
FALLBACK_API_URL = "https://api-inference.huggingface.co/models/gpt2"

# Global variables for model loading
model = None
model_loading = False
model_lock = threading.Lock()

def load_whisper_model():
    """Load Whisper model with proper error handling"""
    global model, model_loading
    
    with model_lock:
        if model is not None:
            return model  # Already loaded
        
        if model_loading:
            # Wait for another thread to finish loading
            while model_loading:
                time.sleep(0.1)
            return model
        
        model_loading = True
        
        try:
            # Check available disk space first
            total, used, free = shutil.disk_usage(".")
            free_gb = free // (1024**3)
            
            if free_gb < 2:  # Less than 2GB free
                print(f"⚠️ Low disk space ({free_gb}GB free). Using base model.")
                model = whisper.load_model("base")
                print("✅ Whisper base model loaded (low memory mode)")
            else:
                # Try medium model if space available
                try:
                    model = whisper.load_model("medium")
                    print("✅ Whisper medium model loaded successfully")
                except Exception as e:
                    print(f"⚠️ Medium model failed, falling back to base: {e}")
                    model = whisper.load_model("base")
                    print("✅ Whisper base model loaded as fallback")
                    
        except Exception as e:
            print(f"❌ Error loading Whisper model: {e}")
            model = None
        finally:
            model_loading = False
            
        return model

# Initialize model on startup
print("🔄 Loading Whisper model...")
load_whisper_model()

UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

def convert_to_wav(input_path, output_path):
    try:
        # Enhanced audio conversion with better quality settings
        subprocess.run([
            'ffmpeg', '-y',  # Overwrite output file
            '-i', input_path,  # Input file
            '-acodec', 'pcm_s16le',  # 16-bit PCM audio
            '-ar', '16000',  # 16kHz sample rate (optimal for Whisper)
            '-ac', '1',  # Mono audio (Whisper works better with mono)
            '-af', 'highpass=f=200,lowpass=f=8000',  # Filter noise and high frequencies
            '-sample_fmt', 's16',  # 16-bit sample format
            output_path
        ], check=True, capture_output=True, timeout=60)  # Increased timeout
        return True
    except subprocess.TimeoutExpired:
        print("❌ Audio conversion timed out")
        return False
    except subprocess.CalledProcessError as e:
        print(f"❌ Audio conversion failed: {e}")
        return False
    except FileNotFoundError:
        print("❌ FFmpeg not found. Please install FFmpeg.")
        return False

@app.route("/transcribe", methods=["POST"])
def transcribe():
    if 'audio' not in request.files:
        return jsonify({"error": "No audio file"}), 400

    # Load model if not already loaded
    current_model = load_whisper_model()
    if current_model is None:
        return jsonify({"error": "Whisper model not loaded"}), 500

    audio = request.files['audio']
    
    # Check file size (limit to 50MB)
    audio.seek(0, 2)  # Go to end
    file_size = audio.tell()
    audio.seek(0)  # Go back to start
    
    if file_size > 50 * 1024 * 1024:  # 50MB
        return jsonify({"error": "File too large. Maximum 50MB allowed."}), 400

    # Create temporary files
    temp_dir = tempfile.mkdtemp()
    original_filename = os.path.join(temp_dir, audio.filename)
    wav_filename = os.path.join(temp_dir, "audio.wav")
    
    try:
        # Save uploaded file
        audio.save(original_filename)
        
        # Convert to WAV
        if not convert_to_wav(original_filename, wav_filename):
            return jsonify({"error": "Audio conversion failed"}), 500
        
        # Check if WAV file was created
        if not os.path.exists(wav_filename):
            return jsonify({"error": "WAV file creation failed"}), 500
        
        # Validate audio quality
        try:
            import librosa
            y, sr = librosa.load(wav_filename, sr=None, duration=10)  # Load first 10 seconds
            
            # Check audio length
            duration = len(y) / sr
            if duration < 0.5:
                return jsonify({"error": "Audio too short (less than 0.5 seconds)"}), 400
            if duration > 600:  # 10 minutes max
                return jsonify({"error": "Audio too long (more than 10 minutes)"}), 400
            
            # Check audio levels (RMS)
            rms = librosa.feature.rms(y=y)[0]
            mean_rms = np.mean(rms)
            if mean_rms < 0.01:  # Very quiet audio
                return jsonify({"error": "Audio too quiet for transcription"}), 400
                
        except Exception as e:
            print(f"Warning: Audio validation failed: {e}")
            # Continue anyway, but log the warning
        
        # Transcribe with improved settings
        result = current_model.transcribe(
            wav_filename,
            language="en",  # Specify English language
            task="transcribe",  # Explicitly set task
            fp16=False,  # Use full precision for better accuracy
            verbose=False,  # Reduce console output
            word_timestamps=False,  # Disable word timestamps for speed
            condition_on_previous_text=False,  # Don't condition on previous text
            temperature=0.0,  # Use greedy decoding for consistency
            compression_ratio_threshold=2.4,  # Filter out low-quality segments
            logprob_threshold=-1.0,  # Accept all segments
            no_speech_threshold=0.6  # Higher threshold to filter silence
        )
        transcription = result.get("text", "").strip()
        
        # Post-process transcription for better quality
        if transcription:
            # Remove extra whitespace
            transcription = " ".join(transcription.split())
            
            # Basic punctuation fixes
            transcription = transcription.replace(" .", ".")
            transcription = transcription.replace(" ,", ",")
            transcription = transcription.replace(" !", "!")
            transcription = transcription.replace(" ?", "?")
            
            # Capitalize first letter
            if transcription and transcription[0].isalpha():
                transcription = transcription[0].upper() + transcription[1:]
        
        if not transcription:
            return jsonify({
                "transcript": "",
                "emotion": "neutral",
                "confidence": 0.0,
                "voice_stress": {
                    'pitch_mean': 0.0,
                    'energy_mean': 0.0,
                    'energy_variance': 0.0,
                    'spectral_centroid': 0.0,
                    'zero_crossing_rate': 0.0,
                    'stress_level': 0.0,
                    'stress_category': 'unknown'
                }
            })

        # Emotion Detection (text-based)
        emotion, confidence = detect_emotion(transcription)
        
        # Debug logging
        print(f"🔍 Emotion Detection Debug:")
        print(f"   Text: '{transcription}'")
        print(f"   Detected Emotion: {emotion}")
        print(f"   Confidence: {confidence}")

        # Stress Detection (voice-based)
        voice_stress = detect_voice_stress(wav_filename)

        return jsonify({
            "transcript": transcription,
            "emotion": emotion,
            "confidence": confidence,
            "voice_stress": voice_stress
        })
        
    except Exception as e:
        print(f"❌ Transcription error: {e}")
        return jsonify({"error": f"Transcription failed: {str(e)}"}), 500
        
    finally:
        # Clean up temporary files
        try:
            shutil.rmtree(temp_dir)
        except Exception as e:
            print(f"Warning: Could not clean up temp files: {e}")

# ========== Semantic Search ==========
# Paths
DATA_FOLDER = 'memory_data'
INDEX_FILE = 'vector.index'
METADATA_FILE = 'metadata.json'

os.makedirs(DATA_FOLDER, exist_ok=True)

# Load or initialize metadata
if os.path.exists(METADATA_FILE):
    with open(METADATA_FILE, 'r') as f:
        metadata = json.load(f)
else:
    metadata = {}

# Sentence Transformer model
try:
    # Check memory before loading
    memory = psutil.virtual_memory()
    if memory.available < 2 * 1024 * 1024 * 1024:  # Less than 2GB available
        print(f"⚠️ Low memory ({memory.available // (1024**3)}GB available). Using smaller model.")
        embed_model = SentenceTransformer('paraphrase-MiniLM-L3-v2')  # Smaller model
    else:
        embed_model = SentenceTransformer('all-MiniLM-L6-v2')
    print("✅ Sentence Transformer model loaded successfully")
except Exception as e:
    print(f"❌ Error loading Sentence Transformer: {e}")
    embed_model = None

dimension = 384
index = faiss.IndexFlatL2(dimension)

# Load index if available
if os.path.exists(INDEX_FILE):
    index = faiss.read_index(INDEX_FILE)

@app.route('/save_transcript', methods=['POST'])
def save_transcript():
    data = request.json
    text = data.get('text')
    timestamp = data.get('timestamp')

    if not text or not timestamp:
        return jsonify({"error": "Missing text or timestamp"}), 400

    uid = str(uuid.uuid4())
    embedding = embed_model.encode([text])[0]
    index.add(np.array([embedding]).astype('float32'))

    # Create metadata with emotion and stress data
    memory_data = {'text': text, 'timestamp': timestamp}
    
    # Add emotion data if available
    if 'emotion' in data:
        memory_data['emotion'] = data['emotion']
    if 'confidence' in data:
        memory_data['confidence'] = float(data['confidence'])
    if 'stress_level' in data:
        memory_data['stress_level'] = float(data['stress_level'])
    if 'stress_category' in data:
        memory_data['stress_category'] = data['stress_category']

    metadata[uid] = memory_data

    with open(METADATA_FILE, 'w') as f:
        json.dump(metadata, f)

    faiss.write_index(index, INDEX_FILE)

    return jsonify({"message": "Transcript saved and embedded."})

@app.route('/search_memory', methods=['POST'])
def search_memory():
    if embed_model is None:
        # Fallback to simple search if embedding model is not available
        return search_memory_simple()
        
    data = request.json
    query = data.get('query', '').strip()
    
    if not query:
        return jsonify({"error": "Query is required"}), 400

    try:
        # Check memory before processing
        memory = psutil.virtual_memory()
        if memory.available < 500 * 1024 * 1024:  # Less than 500MB available
            return jsonify({"error": "Insufficient memory for search"}), 503
        
        # For short queries (less than 3 characters), use simple text search
        if len(query) < 3:
            print(f"🔍 Using simple search for short query: '{query}'")
            return search_memory_simple()
        
        # For queries with common words, also use simple search for better accuracy
        common_words = ['please', 'thank', 'hello', 'hi', 'yes', 'no', 'okay', 'ok', 'good', 'bad', 'help', 'stop', 'start', 'now', 'today', 'tomorrow', 'yesterday']
        if query.lower() in common_words or any(word in query.lower() for word in common_words):
            print(f"🔍 Using simple search for common word query: '{query}'")
            return search_memory_simple()
            
        # Encode query
        query_embedding = embed_model.encode([query])[0]
        
        # Search
        k = min(10, len(metadata))  # Limit results
        distances, indices = index.search(np.array([query_embedding]).astype('float32'), k)
        
        # Format results
        results = []
        for i, (distance, idx) in enumerate(zip(distances[0], indices[0])):
            if idx < len(metadata):
                memory_data = list(metadata.values())[idx]
                
                # Create voice_stress object
                voice_stress = {
                    'pitch_mean': 0.0,
                    'energy_mean': 0.0,
                    'energy_variance': 0.0,
                    'spectral_centroid': 0.0,
                    'zero_crossing_rate': 0.0,
                    'stress_level': memory_data.get('stress_level', 0.0),
                    'stress_category': memory_data.get('stress_category', 'unknown')
                }
                
                results.append({
                    'text': memory_data.get('text', ''),
                    'timestamp': memory_data.get('timestamp', ''),
                    'emotion': memory_data.get('emotion', ''),
                    'confidence': memory_data.get('confidence', 0.0),
                    'voice_stress': voice_stress
                })
        
        return jsonify(results)
        
    except Exception as e:
        print(f"❌ Search error: {e}")
        return jsonify({"error": f"Search failed: {str(e)}"}), 500

@app.route('/search_memory_simple', methods=['POST'])
def search_memory_simple():
    """Simple text-based search when embedding model is not available"""
    data = request.json
    query = data.get('query', '').strip().lower()
    
    if not query:
        return jsonify({"error": "Query is required"}), 400

    try:
        print(f"🔍 Simple search for query: '{query}'")
        results = []
        for memory_data in metadata.values():
            text = memory_data.get('text', '').lower()
            
            # More flexible matching for short queries
            if len(query) < 3:
                # For short queries, check if the word appears as a whole word
                words = text.split()
                if query in words or query in text:
                    match = True
                else:
                    match = False
            else:
                # For longer queries, use more precise matching
                words = text.split()
                # Check for exact word match first (most precise)
                if query in words:
                    match = True
                # Then check for substring match
                elif query in text:
                    match = True
                else:
                    match = False
            
            if match:
                # Create voice_stress object
                voice_stress = {
                    'pitch_mean': 0.0,
                    'energy_mean': 0.0,
                    'energy_variance': 0.0,
                    'spectral_centroid': 0.0,
                    'zero_crossing_rate': 0.0,
                    'stress_level': memory_data.get('stress_level', 0.0),
                    'stress_category': memory_data.get('stress_category', 'unknown')
                }
                
                results.append({
                    'text': memory_data.get('text', ''),
                    'timestamp': memory_data.get('timestamp', ''),
                    'emotion': memory_data.get('emotion', ''),
                    'confidence': memory_data.get('confidence', 0.0),
                    'voice_stress': voice_stress
                })
        
        # Sort by timestamp (newest first) and limit results
        results.sort(key=lambda x: x['timestamp'], reverse=True)
        results = results[:10]
        
        print(f"🔍 Found {len(results)} results for query: '{query}'")
        return jsonify(results)
        
    except Exception as e:
        print(f"❌ Simple search error: {e}")
        return jsonify({"error": f"Search failed: {str(e)}"}), 500

@app.route('/test', methods=['GET'])
def test_connection():
    """Simple test endpoint to check if server is reachable"""
    return jsonify({"status": "ok", "message": "Server is running"})

@app.route('/add_test_memory', methods=['POST'])
def add_test_memory():
    """Add a test memory for testing search functionality"""
    data = request.json
    text = data.get('text', 'Test memory')
    timestamp = data.get('timestamp', datetime.datetime.now().isoformat())
    emotion = data.get('emotion', 'happy')
    confidence = data.get('confidence', 0.8)
    stress_level = data.get('stress_level', 0.3)
    stress_category = data.get('stress_category', 'low')

    uid = str(uuid.uuid4())
    
    if embed_model is not None:
        embedding = embed_model.encode([text])[0]
        index.add(np.array([embedding]).astype('float32'))

    memory_data = {
        'text': text,
        'timestamp': timestamp,
        'emotion': emotion,
        'confidence': confidence,
        'stress_level': stress_level,
        'stress_category': stress_category
    }

    metadata[uid] = memory_data

    with open(METADATA_FILE, 'w') as f:
        json.dump(metadata, f)

    if embed_model is not None:
        faiss.write_index(index, INDEX_FILE)

    return jsonify({"message": "Test memory added successfully", "id": uid})

@app.route('/assistant', methods=['POST'])
def assistant():
    """AI Assistant endpoint using Hugging Face Inference API"""
    try:
        data = request.get_json()
        question = data.get('question', '').strip()
        context = data.get('context', '').strip()
        
        if not question:
            return jsonify({"error": "Question is required"}), 400
        
        if not context:
            return jsonify({"error": "No memory context available"}), 400
        
        # Create a comprehensive prompt for the AI assistant
        prompt = f"""<s>[INST] You are a helpful memory assistant that analyzes personal memories and provides insights.

CONTEXT - User's Memory Data:
{context}

USER QUESTION: {question}

INSTRUCTIONS:
- Analyze the memory data to answer the user's question
- Consider emotions, stress levels, and timestamps
- Be conversational and helpful
- If no relevant memories are found, suggest recording more memories
- Keep responses concise but informative
- Use the emotion and stress data to provide psychological insights

Please provide a helpful response: [/INST]"""

        try:
            # Make API call to Hugging Face
            headers = {
                "Authorization": f"Bearer {HUGGINGFACE_API_KEY}",
                "Content-Type": "application/json"
            }
            
            payload = {
                "inputs": prompt,
                "parameters": {
                    "max_new_tokens": 500,
                    "temperature": 0.7,
                    "top_p": 0.9,
                    "do_sample": True,
                    "return_full_text": False
                }
            }
            
            response = requests.post(HUGGINGFACE_API_URL, headers=headers, json=payload, timeout=30)
            
            if response.status_code == 200:
                result = response.json()
                if isinstance(result, list) and len(result) > 0:
                    reply = result[0].get('generated_text', '').strip()
                    # Clean up the response (remove prompt if included)
                    if reply.startswith(prompt):
                        reply = reply[len(prompt):].strip()
                    return jsonify({"reply": reply})
                else:
                    return jsonify({"error": "Unexpected response format from Hugging Face"}), 500
            elif response.status_code == 503:
                # Try fallback model if main model is loading
                print("⚠️ Main model loading, trying fallback...")
                fallback_response = requests.post(FALLBACK_API_URL, headers=headers, json=payload, timeout=30)
                if fallback_response.status_code == 200:
                    result = fallback_response.json()
                    if isinstance(result, list) and len(result) > 0:
                        reply = result[0].get('generated_text', '').strip()
                        if reply.startswith(prompt):
                            reply = reply[len(prompt):].strip()
                        return jsonify({"reply": reply + " (using fallback model)"})
                return jsonify({"error": "Models are currently loading. Please try again in a few seconds."}), 503
            elif response.status_code == 401:
                return jsonify({"error": "Hugging Face API key not configured or invalid. Please set your API key."}), 401
            elif response.status_code == 429:
                return jsonify({"error": "Rate limit exceeded. Please try again later."}), 429
            else:
                return jsonify({"error": f"Hugging Face API error: {response.status_code} - {response.text}"}), 500
                
        except requests.exceptions.Timeout:
            return jsonify({"error": "Request timed out. Please try again."}), 408
        except requests.exceptions.RequestException as e:
            return jsonify({"error": f"Network error: {str(e)}"}), 500
        except Exception as e:
            return jsonify({"error": f"Unexpected error: {str(e)}"}), 500
            
    except Exception as e:
        print(f"❌ Assistant error: {e}")
        return jsonify({"error": f"Assistant failed: {str(e)}"}), 500

# ========== Start App ==========
if __name__ == '__main__':
    app.run(debug=False, host='0.0.0.0', port=5000) 