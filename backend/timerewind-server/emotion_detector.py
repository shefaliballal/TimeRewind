import numpy as np
import librosa
from transformers import pipeline
import re
import os

# Initialize emotion classification pipeline
try:
    emotion_classifier = pipeline(
        "text-classification",
        model="j-hartmann/emotion-english-distilroberta-base",
        return_all_scores=True
    )
    print("✅ Emotion classifier loaded successfully")
except Exception as e:
    print(f"❌ Error loading emotion classifier: {e}")
    emotion_classifier = None

def detect_emotion(text):
    """
    Detect emotion from text using a pre-trained model.
    
    Args:
        text (str): Input text to analyze
        
    Returns:
        tuple: (emotion_label, confidence_score)
    """
    if emotion_classifier is None:
        # Provide a reasonable fallback confidence when classifier is not available
        return "neutral", 0.6
        
    if not text or text.strip() == "":
        return "neutral", 0.6
    
    try:
        # Clean the text
        cleaned_text = re.sub(r'[^\w\s]', '', text.lower()).strip()
        
        if len(cleaned_text) < 3:
            return "neutral", 0.6
        
        # Limit text length to prevent memory issues
        if len(cleaned_text) > 500:
            cleaned_text = cleaned_text[:500]
        
        # Get emotion predictions
        results = emotion_classifier(cleaned_text)
        
        # Find the emotion with highest confidence
        emotions = results[0]
        best_emotion = max(emotions, key=lambda x: x['score'])
        
        # Ensure minimum confidence threshold
        confidence = max(best_emotion['score'], 0.3)  # Minimum 30% confidence
        
        return best_emotion['label'], confidence
        
    except Exception as e:
        print(f"Error in emotion detection: {e}")
        # Provide reasonable fallback confidence
        return "neutral", 0.6

def detect_voice_stress(audio_path):
    """
    Detect voice stress from audio file using acoustic features.
    
    Args:
        audio_path (str): Path to the audio file
        
    Returns:
        dict: Stress indicators including pitch, energy, and overall stress level
    """
    try:
        # Check if file exists
        if not os.path.exists(audio_path):
            print(f"❌ Audio file not found: {audio_path}")
            return get_default_stress_data()
        
        # Check file size (limit to 100MB for processing)
        file_size = os.path.getsize(audio_path)
        if file_size > 100 * 1024 * 1024:  # 100MB
            print(f"❌ Audio file too large: {file_size / (1024*1024):.1f}MB")
            return get_default_stress_data()
        
        # Load audio file with duration limit (max 10 minutes)
        y, sr = librosa.load(audio_path, sr=None, duration=600)
        
        # Check if audio is too short
        if len(y) < sr * 0.5:  # Less than 0.5 seconds
            print("❌ Audio too short for analysis")
            return get_default_stress_data()
        
        # Extract features
        # Pitch (fundamental frequency)
        pitches, magnitudes = librosa.piptrack(y=y, sr=sr)
        pitch_values = pitches[magnitudes > np.percentile(magnitudes, 85)]
        mean_pitch = np.mean(pitch_values) if len(pitch_values) > 0 else 0
        
        # Energy (RMS)
        rms = librosa.feature.rms(y=y)[0]
        mean_energy = np.mean(rms)
        energy_variance = np.var(rms)
        
        # Spectral features
        spectral_centroids = librosa.feature.spectral_centroid(y=y, sr=sr)[0]
        mean_spectral_centroid = np.mean(spectral_centroids)
        
        # Zero crossing rate (indicates voice roughness)
        zcr = librosa.feature.zero_crossing_rate(y)[0]
        mean_zcr = np.mean(zcr)
        
        # Calculate stress indicators
        stress_indicators = {
            'pitch_mean': float(mean_pitch),
            'energy_mean': float(mean_energy),
            'energy_variance': float(energy_variance),
            'spectral_centroid': float(mean_spectral_centroid),
            'zero_crossing_rate': float(mean_zcr)
        }
        
        # Simple stress level calculation
        # Higher energy variance and zero crossing rate indicate stress
        stress_level = min(1.0, (energy_variance * 10 + mean_zcr * 5) / 2)
        stress_indicators['stress_level'] = float(stress_level)
        
        # Stress classification
        if stress_level > 0.7:
            stress_indicators['stress_category'] = 'high'
        elif stress_level > 0.4:
            stress_indicators['stress_category'] = 'medium'
        else:
            stress_indicators['stress_category'] = 'low'
            
        return stress_indicators
        
    except Exception as e:
        print(f"❌ Error in voice stress detection: {e}")
        return get_default_stress_data()

def get_default_stress_data():
    """Return default stress data when analysis fails"""
    return {
        'pitch_mean': 0.0,
        'energy_mean': 0.0,
        'energy_variance': 0.0,
        'spectral_centroid': 0.0,
        'zero_crossing_rate': 0.0,
        'stress_level': 0.0,
        'stress_category': 'unknown'
    } 