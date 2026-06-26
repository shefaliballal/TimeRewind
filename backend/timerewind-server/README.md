# TimeRewind - Enhanced with Emotion Detection & Voice Stress Analysis

## Overview

TimeRewind is an Android app that records audio, transcribes it using Whisper, and provides semantic search capabilities for your memories. The app now includes advanced emotion detection and voice stress analysis features.

## New Features Added

### 1. Emotion Detection

- **Text-based emotion analysis** using DistilRoBERTa model
- Detects emotions: joy, sadness, anger, fear, surprise, disgust, neutral
- Provides confidence scores for each emotion detection

### 2. Voice Stress Analysis

- **Acoustic feature analysis** using Librosa
- Analyzes pitch, energy, spectral features, and voice roughness
- Categorizes stress levels: low, medium, high
- Provides detailed stress indicators

### 3. Enhanced UI

- Displays emotion and stress information in the memory list
- Shows emotion confidence percentages
- Visual stress indicators with emojis
- Enhanced transcript display with emotion and stress data

## Backend Changes

### Updated `/transcribe` Endpoint

- Now returns emotion and voice stress data along with transcript
- Response format:

```json
{
  "transcript": "Your transcribed text",
  "emotion": "joy",
  "confidence": 0.85,
  "voice_stress": {
    "pitch_mean": 220.5,
    "energy_mean": 0.3,
    "stress_level": 0.6,
    "stress_category": "medium"
  }
}
```

### Enhanced `/save_transcript` Endpoint

- Stores emotion and stress data with memories
- Enables searching memories with emotional context

## Android App Changes

### Updated Data Models

- `TranscriptionResponse`: Added emotion, confidence, and voice_stress fields
- `MemoryItem`: Added emotion and stress data fields
- `VoiceStressData`: New data class for stress analysis results

### Enhanced UI Components

- `MemoryAdapter`: Displays emotion and stress indicators in memory list
- `MainActivity`: Shows emotion and stress data in transcript view

## Setup Instructions

1. **Install Python Dependencies**:

   ```bash
   pip install -r requirements.txt
   ```

2. **Start the Flask Backend**:

   ```bash
   python app.py
   ```

3. **Update Android App**:
   - Build and run the Android app
   - The app will automatically use the new emotion and stress features

## Dependencies

### Backend Dependencies

- Flask: Web framework
- Whisper: Audio transcription
- Transformers: Emotion detection
- Librosa: Audio analysis
- FAISS: Semantic search
- Sentence Transformers: Text embeddings

### Android Dependencies

- Retrofit: API communication
- Gson: JSON parsing
- RecyclerView: Memory list display

## API Endpoints

- `POST /transcribe`: Audio transcription with emotion and stress analysis
- `POST /save_transcript`: Save transcript with emotional context
- `POST /search_memory`: Semantic search through memories

## Usage

1. **Record Audio**: Tap the record button to start recording
2. **View Analysis**: After transcription, view emotion and stress data
3. **Search Memories**: Use the search feature to find memories by content or emotional context
4. **Browse History**: View all memories with emotion and stress indicators

The app now provides a comprehensive emotional memory system that captures not just what you said, but how you felt when you said it!
