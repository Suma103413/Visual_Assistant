# 👁️ Visual Assistant - AI-Powered Assistive Vision System

> **An offline-first Android application that uses AI to describe the surrounding world in real-time via voice, supporting 6 Indian languages.**

<div align="center">

![Android](https://img.shields.io/badge/Android-8.0%2B-green)
![Language](https://img.shields.io/badge/Java-17%2B-orange)
![License](https://img.shields.io/badge/License-MIT-blue)
![API](https://img.shields.io/badge/Gemini-1.5%20Flash-yellow)
![TTS](https://img.shields.io/badge/TTS-Sarvam%20AI-purple)

</div>

---

## 📱 Project Overview

**Visual Assistant** is an innovative Android application designed to empower visually impaired individuals with real-time environmental awareness. It uses on-device object detection to identify objects in the user's surroundings and provides voice descriptions in their preferred language.

### 🎯 The Problem We Solve

- **285+ million** visually impaired people worldwide face difficulties in understanding their surroundings
- Existing solutions are either **expensive** (₹2,00,000+ for OrCam MyEye), **internet-dependent** (Google Lookout), or **language-restricted** (English-only)
- **Rural India** lacks reliable internet connectivity and regional language support

### 💡 Our Solution

-  **Use 2 tier model** - online mode and offline mode
-  **6 Indian languages** - English, Hindi, Kannada, Telugu, Tamil, Marathi
-  **Privacy-first** - Images stay on your device, never uploaded
-  **Zero cost** - Completely free, no subscriptions
-  **Voice-first** - Hands-free interaction for blind users

---

## ✨ Key Features

### 🎤 Voice Commands
| Command | Action |
|---------|--------|
| **"Capture"** | Take a photo and describe the scene |
| **"Repeat"** | Hear the last description again |
| **"Language"** | Change the app's language |
| **"Help"** | Listen to all available commands |

### 🔍 Object Detection
- **80+ classes** from COCO dataset (people, vehicles, furniture, etc.)

### 🌐 Two-Tier Execution

| Mode | Description | Dependencies |
|------|-------------|--------------|
| **Online** | Uses Gemini 1.5 Flash for rich descriptions + Sarvam AI for natural TTS | Internet |
| **Offline** | Rule-based descriptions + Google TTS | None |

### 🔒 Privacy & Security
- **No images uploaded** - All processing stays on-device
- **No permanent storage** - Photos are processed in real-time and discarded
- **Minimal permissions** - Only Camera and Microphone required

### 🧩 Additional Features
- 🔋 **Battery Monitoring** - Warns when battery drops below 15%
- 📳 **Haptic Feedback** - Vibrations for system status, capture confirmation, and emergency alerts

---

### Component Details

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Voice Input** | Android SpeechRecognizer | Captures voice commands |
| **Camera** | Android CameraX API | Captures images |
| **Object Detection** | YOLOv8 Nano + TFLite | Identifies objects in real-time |
| **Description (Online)** | Google Gemini 1.5 Flash | Generates natural descriptions |
| **TTS (Online)** | Sarvam AI Bulbul v2 | High-quality Indic language speech |
| **TTS (Offline)** | Google TTS | Basic offline fallback |

---

## 🛠️ Tech Stack

### Machine Learning & AI
| Technology | Purpose |
|------------|---------|
| **YOLOv8 Nano** | On-device object detection (73-layer CNN) |
| **TensorFlow Lite** | Mobile inference framework (Float16 quantization) |
| **Google Gemini 1.5 Flash** | Natural language scene description |
| **Sarvam AI Bulbul v2** | Indic language Text-to-Speech |
| **Google TTS** | Offline fallback TTS |

### Android Native Stack
| Technology | Purpose |
|------------|---------|
| **Java** | Primary programming language |
| **Android Studio** | Development environment |
| **CameraX API** | Frame capture and rotation handling |
| **SpeechRecognizer** | Voice command processing |
| **OkHttp3** | REST API communication |
| **SharedPreferences** | Language preference persistence |

---

## ⚙️ Setup Instructions

### Prerequisites
- Android Studio (latest version)
- Android SDK with API level 26+
- A physical Android device (8.0+) with camera and microphone
- Google Gemini API key
- Sarvam AI API key (for online mode TTS)

## 📸 Screenshots

### Main Interface
![Main Interface](screenshot_main.jpg)

*The app's main screen with voice command buttons and status indicators.*

---

### Detection Results

| Scenario | Screenshot |
|----------|------------|
| **Objects Detected** (Chairs & Table) | ![Chairs](screenshot_detection_chairs.jpg) |
| **Objects Detected** (Cars & Persons) | ![Cars](screenshot_detection_cars.jpg) |
| **Offline Mode** | ![Offline](screenshot_offline.jpg) |

---

### Multilingual Support

| Language | Screenshot |
|----------|------------|
| **Tamil** | ![Tamil](screenshot_telagu.jpg) |
| **Language Selection** | ![Language](screenshot_language.jpg) |

---

### Features in Action

- ✅ **Real-time object detection** - Identifies chairs, tables, cars, persons
- ✅ **Multilingual support** - Works in Tamil, Kannada, Hindi, English, Telugu, Marathi
- ✅ **Offline capability** - Continues working without internet
- ✅ **Voice-first interface** - Hands-free operation
- ✅ **Gemini AI integration** - Rich scene descriptions

> **Note:** These screenshots showcase the app's core features including voice commands, object detection, multilingual support, and offline functionality.

### Step 1: Clone the Repository
```bash
git clone https://github.com/Suma103413/Visual_Assistant.git
cd Visual_Assistant
