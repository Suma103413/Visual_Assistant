package com.visualassistant.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SarvamTTS {

    private static final String TAG  = "SarvamTTS";
    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    public interface ReadyCallback {
        void onReady();
    }

    public interface SpeakCallback {
        void onDone();
        default void onError() { onDone(); }
    }

    private final Context      context;
    private final OkHttpClient client;
    private TextToSpeech       googleTTS;
    private boolean            googleReady = false;
    private AppLanguage        lang = AppLanguage.ENGLISH;
    private MediaPlayer        player;
    private ReadyCallback      readyCallback;

    public SarvamTTS(Context ctx, ReadyCallback onReady) {
        context           = ctx;
        this.readyCallback = onReady;
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS) // FIX: was 15s — bumped for slow networks
                .build();

        googleTTS = new TextToSpeech(ctx, status -> {
            if (status == TextToSpeech.SUCCESS) {
                googleReady = true;
                googleTTS.setSpeechRate(0.85f);
                Log.d(TAG, "Google TTS ready");
                if (readyCallback != null)
                    readyCallback.onReady();
            } else {
                Log.e(TAG, "Google TTS init failed: " + status);
                // Still fire readyCallback so app doesn't hang
                if (readyCallback != null)
                    readyCallback.onReady();
            }
        });
    }

    public void setLanguage(AppLanguage l) {
        lang = l;
        if (googleReady) {
            int result = googleTTS.setLanguage(l.ttsLocale);
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale " + l.ttsLocale + " not supported, falling back to English");
                googleTTS.setLanguage(Locale.ENGLISH);
            }
        }
    }

    /**
     * Speak text using Sarvam AI (primary) with Google TTS as fallback.
     * The SpeakCallback.onDone() fires only AFTER audio finishes playing,
     * so the mic never opens while TTS is still speaking.
     */
    public void speak(String text, boolean emergency, SpeakCallback cb) {
        if (text == null || text.isEmpty()) {
            if (cb != null) cb.onDone();
            return;
        }
        new Thread(() -> {
            try {
                sarvamSpeak(text, emergency, cb);
            } catch (Exception e) {
                Log.w(TAG, "Sarvam failed, using Google TTS: " + e.getMessage());
                // FIX (CRITICAL): was calling googleSpeak() then cb.onDone() immediately.
                // That fires the callback before Google TTS finishes, so the mic opens
                // mid-sentence and picks up TTS audio as a voice command.
                // Now we use googleSpeakWithCallback() which only fires onDone()
                // after the utterance actually completes.
                googleSpeakWithCallback(text, emergency, cb);
            }
        }).start();
    }

    private void sarvamSpeak(String text, boolean emergency, SpeakCallback cb)
            throws Exception {
        String body;
        try {
            JSONArray inputs = new JSONArray();
            inputs.put(text);
            JSONObject req = new JSONObject();
            req.put("inputs",               inputs);
            req.put("target_language_code", lang.sarvamCode);
            req.put("speaker",              getSpeaker());
            req.put("pitch",                emergency ? 1 : 0);
            req.put("pace",                 emergency ? 1.1 : 0.85);
            req.put("loudness",             emergency ? 1.5 : 1.0);
            // FIX: was 8000 (telephone quality). 22050 Hz is standard speech quality.
            req.put("speech_sample_rate",   22050);
            req.put("enable_preprocessing", true);
            req.put("model",                "bulbul:v1");
            body = req.toString();
        } catch (Exception e) {
            throw new Exception("Build body failed: " + e.getMessage());
        }

        RequestBody rb = RequestBody.create(body, JSON);
        Request rq = new Request.Builder()
                .url(ApiConfig.SARVAM_TTS_URL)
                .post(rb)
                .addHeader("api-subscription-key", ApiConfig.SARVAM_API_KEY)
                .build();

        try (Response rs = client.newCall(rq).execute()) {
            if (!rs.isSuccessful())
                throw new Exception("Sarvam HTTP " + rs.code()
                        + ": " + rs.body().string());
            String json = rs.body().string();
            String audio;
            try {
                audio = new JSONObject(json)
                        .getJSONArray("audios")
                        .getString(0);
            } catch (Exception e) {
                throw new Exception("Parse audio failed: " + e.getMessage());
            }
            playAudio(Base64.decode(audio, Base64.DEFAULT), cb);
        }
    }

    /**
     * FIX (CRITICAL): Correct Kannada speaker name.
     * "amol" is NOT a Kannada voice in Sarvam's bulbul:v1 model —
     * it is a Hindi/Marathi voice. Using it for Kannada causes wrong
     * pronunciation (words spoken with Hindi phonetics).
     * Correct Kannada voices: "ananya" (female) or "neel" (male).
     */
    private String getSpeaker() {
        switch (lang) {
            case HINDI:   return "meera";
            case KANNADA: return "ananya"; // was "amol" — WRONG voice, fixed
            case TELUGU:  return "pavithra";
            case TAMIL:   return "anushka";
            case MARATHI: return "arvind";
            default:      return "meera";  // English uses meera (en-IN)
        }
    }

    private void playAudio(byte[] bytes, SpeakCallback cb) {
        try {
            File tmp = File.createTempFile(
                    "va_audio", ".wav", context.getCacheDir());
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(bytes);
            fos.close();

            // Release any previously playing audio
            if (player != null) {
                try { player.stop(); } catch (Exception ignored) {}
                player.release();
                player = null;
            }

            player = new MediaPlayer();
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build();
            player.setAudioAttributes(aa);
            player.setDataSource(tmp.getAbsolutePath());
            player.prepare();
            player.setOnCompletionListener(mp -> {
                tmp.delete();
                if (cb != null) cb.onDone();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + "/" + extra);
                tmp.delete();
                if (cb != null) cb.onError();
                return true;
            });
            player.start();
        } catch (Exception e) {
            Log.e(TAG, "playAudio failed: " + e.getMessage());
            if (cb != null) cb.onError();
        }
    }

    /**
     * FIX (CRITICAL): Google TTS fallback now fires callback AFTER
     * the utterance finishes, not immediately.
     * Previous code called cb.onDone() right after googleTTS.speak(),
     * causing the mic to open while TTS audio was still playing —
     * the TTS voice was then picked up as a voice command.
     */
    private void googleSpeakWithCallback(String text, boolean emergency,
                                         SpeakCallback cb) {
        if (!googleReady) {
            Log.w(TAG, "Google TTS not ready");
            if (cb != null) cb.onDone();
            return;
        }

        String uttId = "utt_" + System.currentTimeMillis();

        googleTTS.setOnUtteranceProgressListener(
                new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        Log.d(TAG, "Google TTS started: " + utteranceId);
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        Log.d(TAG, "Google TTS done: " + utteranceId);
                        if (cb != null) cb.onDone();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.e(TAG, "Google TTS error: " + utteranceId);
                        if (cb != null) cb.onDone(); // graceful degradation
                    }
                });

        googleTTS.setSpeechRate(emergency ? 1.05f : 0.85f);
        Bundle params = new Bundle();
        googleTTS.speak(text, TextToSpeech.QUEUE_FLUSH, params, uttId);
    }

    /**
     * Speak using Google TTS without a completion callback.
     * Only used for initial welcome/language prompts where we use
     * a fixed timer delay instead of a callback.
     */
    public void speakWithGoogle(String text, boolean emergency) {
        if (!googleReady) return;
        googleTTS.setSpeechRate(emergency ? 1.05f : 0.85f);
        googleTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "utt_nowait_" + System.currentTimeMillis());
    }

    public void stop() {
        if (player != null && player.isPlaying()) {
            try { player.stop(); } catch (Exception ignored) {}
        }
        if (googleReady) googleTTS.stop();
    }

    public void shutdown() {
        stop();
        if (player != null) {
            player.release();
            player = null;
        }
        if (googleTTS != null) {
            googleTTS.shutdown();
            googleTTS = null;
        }
    }
}