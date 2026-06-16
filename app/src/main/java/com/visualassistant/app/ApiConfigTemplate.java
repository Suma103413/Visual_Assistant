package com.visualassistant.app;

/**
 * IMPORTANT: This is a TEMPLATE file.
 *
 * To use this app, you MUST create a file called ApiConfig.java
 * by copying this template and adding your real API keys.
 *
 * Steps:
 * 1. Copy this file and rename it to ApiConfig.java
 * 2. Replace "YOUR_ACTUAL_GEMINI_KEY" with your real Gemini API key
 * 3. Replace "YOUR_ACTUAL_SARVAM_KEY" with your real Sarvam API key
 *
 * Get your keys from:
 * - Google Gemini: https://ai.google.dev/gemini-api
 * - Sarvam AI: https://www.sarvam.ai
 */
public class ApiConfigTemplate {
    // Replace with your actual Google Gemini API key
    public static final String GEMINI_API_KEY = "YOUR_ACTUAL_GEMINI_KEY";

    // Replace with your actual Sarvam AI API key
    public static final String SARVAM_API_KEY = "YOUR_ACTUAL_SARVAM_KEY";

    // URLs (do not change these - they are correct)
    public static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;

    public static final String SARVAM_TTS_URL =
            "https://api.sarvam.ai/text-to-speech";
}
