#!/usr/bin/env python3
"""
Test script to verify TimeRewind backend setup
"""

import sys
import os

def test_imports():
    """Test if all required packages can be imported"""
    print("�� Testing imports...")
    
    try:
        import flask
        print("✅ Flask imported successfully")
    except ImportError as e:
        print(f"❌ Flask import failed: {e}")
        return False
    
    try:
        import whisper
        print("✅ Whisper imported successfully")
    except ImportError as e:
        print(f"❌ Whisper import failed: {e}")
        return False
    
    try:
        import librosa
        print("✅ Librosa imported successfully")
    except ImportError as e:
        print(f"❌ Librosa import failed: {e}")
        return False
    
    try:
        from transformers import pipeline
        print("✅ Transformers imported successfully")
    except ImportError as e:
        print(f"❌ Transformers import failed: {e}")
        return False
    
    try:
        import faiss
        print("✅ FAISS imported successfully")
    except ImportError as e:
        print(f"❌ FAISS import failed: {e}")
        return False
    
    try:
        from sentence_transformers import SentenceTransformer
        print("✅ Sentence Transformers imported successfully")
    except ImportError as e:
        print(f"❌ Sentence Transformers import failed: {e}")
        return False
    
    return True

def test_models():
    """Test if models can be loaded"""
    print("\n🔍 Testing model loading...")
    
    try:
        import whisper
        model = whisper.load_model("base")
        print("✅ Whisper model loaded successfully")
    except Exception as e:
        print(f"❌ Whisper model loading failed: {e}")
        return False
    
    try:
        from transformers import pipeline
        emotion_classifier = pipeline(
            "text-classification",
            model="j-hartmann/emotion-english-distilroberta-base",
            return_all_scores=True
        )
        print("✅ Emotion classifier loaded successfully")
    except Exception as e:
        print(f"❌ Emotion classifier loading failed: {e}")
        return False
    
    try:
        from sentence_transformers import SentenceTransformer
        embed_model = SentenceTransformer('all-MiniLM-L6-v2')
        print("✅ Sentence Transformer model loaded successfully")
    except Exception as e:
        print(f"❌ Sentence Transformer model loading failed: {e}")
        return False
    
    return True

def test_ffmpeg():
    """Test if FFmpeg is available"""
    print("\n🔍 Testing FFmpeg...")
    
    try:
        import subprocess
        result = subprocess.run(['ffmpeg', '-version'], 
                              capture_output=True, text=True, timeout=10)
        if result.returncode == 0:
            print("✅ FFmpeg is available")
            return True
        else:
            print("❌ FFmpeg returned error")
            return False
    except FileNotFoundError:
        print("❌ FFmpeg not found. Please install FFmpeg.")
        return False
    except subprocess.TimeoutExpired:
        print("❌ FFmpeg test timed out")
        return False
    except Exception as e:
        print(f"❌ FFmpeg test failed: {e}")
        return False

def test_emotion_detector():
    """Test emotion detection functionality"""
    print("\n🔍 Testing emotion detection...")
    
    try:
        from emotion_detector import detect_emotion
        
        # Test with sample text
        test_text = "I am so happy today!"
        emotion, confidence = detect_emotion(test_text)
        
        print(f"✅ Emotion detection works: '{test_text}' -> {emotion} ({confidence:.2f})")
        return True
    except Exception as e:
        print(f"❌ Emotion detection test failed: {e}")
        return False

def main():
    """Run all tests"""
    print("�� TimeRewind Backend Setup Test")
    print("=" * 40)
    
    tests = [
        ("Imports", test_imports),
        ("Models", test_models),
        ("FFmpeg", test_ffmpeg),
        ("Emotion Detection", test_emotion_detector)
    ]
    
    passed = 0
    total = len(tests)
    
    for test_name, test_func in tests:
        if test_func():
            passed += 1
        else:
            print(f"⚠️  {test_name} test failed")
    
    print("\n" + "=" * 40)
    print(f"📊 Test Results: {passed}/{total} tests passed")
    
    if passed == total:
        print("🎉 All tests passed! Your setup is ready.")
        print("\nTo start the server, run:")
        print("python app.py")
    else:
        print("❌ Some tests failed. Please fix the issues above.")
        print("\nCommon solutions:")
        print("1. Install missing packages: pip install -r requirements.txt")
        print("2. Install FFmpeg: https://ffmpeg.org/download.html")
        print("3. Check your internet connection for model downloads")
    
    return passed == total

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)