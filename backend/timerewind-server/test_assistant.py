#!/usr/bin/env python3
"""
Test script to debug Hugging Face Assistant API
"""

import requests
import json
import os

# Configuration
HUGGINGFACE_API_KEY = "hfHF_TOKEN = os.getenv("HF_TOKEN")"  # Get from environment variable
HUGGINGFACE_API_URL = "https://api-inference.huggingface.co/models/mistralai/Mistral-7B-Instruct-v0.2"
FALLBACK_API_URL = "https://api-inference.huggingface.co/models/mistralai/Mistral-7B-Instruct-v0.2"

def test_huggingface_connection():
    """Test Hugging Face API connection"""
    
    print("🔍 Testing Hugging Face API Connection...")
    print(f"API Key: {'✅ Set' if HUGGINGFACE_API_KEY else '❌ Not set'}")
    
    if not HUGGINGFACE_API_KEY:
        print("\n❌ ERROR: No API key found!")
        print("Please set your Hugging Face API key:")
        print("1. Get your token from https://huggingface.co/settings/tokens")
        print("2. Set it as environment variable: export HUGGINGFACE_API_KEY='hf_your_token'")
        print("3. Or add it directly in app.py: HUGGINGFACE_API_KEY = 'hf_your_token'")
        return False
    
    # Test with a simple prompt
    prompt = "<s>[INST] You are a helpful AI assistant. Say hello in a friendly way. [/INST]"
    
    headers = {
        "Authorization": f"Bearer {HUGGINGFACE_API_KEY}",
        "Content-Type": "application/json"
    }
    
    payload = {
        "inputs": prompt,
        "parameters": {
            "max_new_tokens": 100,
            "temperature": 0.7,
            "top_p": 0.9,
            "do_sample": True,
            "return_full_text": False
        }
    }
    
    try:
        print("📡 Making request to Hugging Face...")
        response = requests.post(HUGGINGFACE_API_URL, headers=headers, json=payload, timeout=60)
        
        print(f"📊 Response Status: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            print("✅ SUCCESS! API is working.")
            if isinstance(result, list) and len(result) > 0:
                reply = result[0].get('generated_text', '').strip()
                if reply.startswith(prompt):
                    reply = reply[len(prompt):].strip()
                print(f"🤖 AI Response: {reply}")
            return True
            
        elif response.status_code == 401:
            print("❌ ERROR: Invalid API key!")
            print("Please check your Hugging Face API key.")
            return False
            
        elif response.status_code == 503:
            print("⚠️ Model is loading, trying fallback...")
            fallback_response = requests.post(FALLBACK_API_URL, headers=headers, json=payload, timeout=30)
            if fallback_response.status_code == 200:
                result = fallback_response.json()
                if isinstance(result, list) and len(result) > 0:
                    reply = result[0].get('generated_text', '').strip()
                    if reply.startswith(prompt):
                        reply = reply[len(prompt):].strip()
                    print(f"✅ SUCCESS with fallback! AI Response: {reply}")
                    return True
            print("❌ Both models are loading. Try again in 30 seconds.")
            return False
            
        elif response.status_code == 429:
            print("❌ ERROR: Rate limit exceeded!")
            print("You've hit the free tier limit. Wait a few minutes.")
            return False
            
        else:
            print(f"❌ ERROR: {response.status_code} - {response.text}")
            return False
            
    except requests.exceptions.Timeout:
        print("❌ ERROR: Request timed out!")
        return False
    except requests.exceptions.RequestException as e:
        print(f"❌ ERROR: Network error - {e}")
        return False
    except Exception as e:
        print(f"❌ ERROR: Unexpected error - {e}")
        return False

def test_app_endpoint():
    """Test the Flask app assistant endpoint"""
    
    print("\n🔍 Testing Flask App Assistant Endpoint...")
    
    # Test data
    test_data = {
        "question": "What memories do I have?",
        "context": "Dec 15, 2023: [happy, stress=0.2] — Had a great day at work today!"
    }
    
    try:
        response = requests.post("http://localhost:5000/assistant", json=test_data, timeout=30)
        print(f"📊 Flask Response Status: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            print("✅ Flask endpoint working!")
            print(f"🤖 Response: {result.get('reply', 'No reply')}")
            return True
        else:
            print(f"❌ Flask endpoint error: {response.status_code}")
            print(f"Response: {response.text}")
            return False
            
    except requests.exceptions.ConnectionError:
        print("❌ ERROR: Cannot connect to Flask server!")
        print("Make sure your Flask server is running: python app.py")
        return False
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return False

if __name__ == "__main__":
    print("🚀 Hugging Face Assistant Debug Tool")
    print("=" * 50)
    
    # Test 1: Direct Hugging Face API
    hf_success = test_huggingface_connection()
    
    # Test 2: Flask app endpoint
    flask_success = test_app_endpoint()
    
    print("\n" + "=" * 50)
    print("📋 SUMMARY:")
    print(f"Hugging Face API: {'✅ Working' if hf_success else '❌ Failed'}")
    print(f"Flask App: {'✅ Working' if flask_success else '❌ Failed'}")
    
    if not hf_success:
        print("\n🔧 TO FIX:")
        print("1. Get your API key from https://huggingface.co/settings/tokens")
        print("2. Add it to app.py: HUGGINGFACE_API_KEY = 'hf_your_token'")
        print("3. Restart your Flask server")
    
    if not flask_success and hf_success:
        print("\n🔧 TO FIX:")
        print("1. Make sure Flask server is running: python app.py")
        print("2. Check if port 5000 is available") 