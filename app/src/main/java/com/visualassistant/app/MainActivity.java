package com.visualassistant.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG     = "VA";
    private static final int    REQ_ALL = 100;

    // ── Views ────────────────────────────────────────────────────────────
    private PreviewView  previewView;
    private Button       btnCapture, btnLanguage, btnVoice, btnHelp;
    private TextView     tvStatus, tvResult, tvLanguage, tvMicStatus,
            tvProcessing, tvObjects, tvBattery, tvConfidence;
    private CardView     cardResult;
    private LinearLayout layoutProcessing;

    // ── Core ─────────────────────────────────────────────────────────────
    private ImageCapture     imageCapture;
    private ObjectDetector   detector;
    private SarvamTTS        tts;
    private GeminiClient     gemini;
    private SpeechRecognizer sr;
    private ExecutorService  exec;
    private Handler          handler;
    private Vibrator         vibrator;
    private CameraManager    cameraManager;
    private String           cameraId;

    // ── State ─────────────────────────────────────────────────────────────
    private AppLanguage lang        = AppLanguage.ENGLISH;
    private String      lastDesc    = "";
    private boolean     startupDone = false;
    private boolean     busy        = false;
    private boolean     langSet     = false;
    private boolean     ttsReady    = false;
    private boolean     cameraReady = false;
    private boolean     isListening = false;

    // ttsSpeaking: true while Sarvam/Google TTS audio is playing.
    // startListening() checks this so the mic never opens mid-speech.
    private boolean     ttsSpeaking = false;

    // listenForLangMode: true while we are in language-selection listening.
    // Prevents the normal command handler from consuming language words.
    private boolean     listenForLangMode = false;

    private int listenRetries = 0;
    private int lastBmpW      = 1;
    private int lastBmpH      = 1;

    interface OnSpoken { void spoken(String text); }

    // ── Translated label maps for offline fallback ───────────────────────
    // Prevents "book ಕಾಣಿಸುತ್ತಿದೆ" — labels are translated before appending
    // the native verb, so TTS says "ಪುಸ್ತಕ ಕಾಣಿಸುತ್ತಿದೆ" instead.
    private static final Map<String, String> LABELS_KANNADA;
    private static final Map<String, String> LABELS_HINDI;
    private static final Map<String, String> LABELS_TELUGU;
    private static final Map<String, String> LABELS_TAMIL;
    private static final Map<String, String> LABELS_MARATHI;
    static {
        Map<String,String> kn = new HashMap<>();
        kn.put("person","ವ್ಯಕ್ತಿ"); kn.put("book","ಪುಸ್ತಕ");
        kn.put("bottle","ಬಾಟಲಿ");  kn.put("cup","ಕಪ್");
        kn.put("chair","ಕುರ್ಚಿ");  kn.put("couch","ಸೋಫಾ");
        kn.put("bed","ಮಂಚ");       kn.put("laptop","ಲ್ಯಾಪ್‌ಟಾಪ್");
        kn.put("cell phone","ಮೊಬೈಲ್"); kn.put("tv","ಟಿವಿ");
        kn.put("dining table","ಡೈನಿಂಗ್ ಟೇಬಲ್"); kn.put("car","ಕಾರು");
        kn.put("bus","ಬಸ್"); kn.put("truck","ಟ್ರಕ್");
        kn.put("bicycle","ಸೈಕಲ್"); kn.put("motorcycle","ಮೋಟಾರ್‌ಬೈಕ್");
        kn.put("dog","ನಾಯಿ"); kn.put("cat","ಬೆಕ್ಕು");
        kn.put("backpack","ಬ್ಯಾಗ್"); kn.put("clock","ಗಡಿಯಾರ");
        LABELS_KANNADA = Collections.unmodifiableMap(kn);

        Map<String,String> hi = new HashMap<>();
        hi.put("person","व्यक्ति"); hi.put("book","किताब");
        hi.put("bottle","बोतल");   hi.put("cup","कप");
        hi.put("chair","कुर्सी");  hi.put("couch","सोफा");
        hi.put("bed","बिस्तर");    hi.put("laptop","लैपटॉप");
        hi.put("cell phone","मोबाइल"); hi.put("tv","टीवी");
        hi.put("dining table","डाइनिंग टेबल"); hi.put("car","कार");
        hi.put("bus","बस"); hi.put("truck","ट्रक");
        hi.put("bicycle","साइकिल"); hi.put("motorcycle","मोटरसाइकिल");
        hi.put("dog","कुत्ता"); hi.put("cat","बिल्ली");
        hi.put("backpack","बैग"); hi.put("clock","घड़ी");
        LABELS_HINDI = Collections.unmodifiableMap(hi);

        Map<String,String> te = new HashMap<>();
        te.put("person","వ్యక్తి"); te.put("book","పుస్తకం");
        te.put("bottle","సీసా");    te.put("cup","కప్పు");
        te.put("chair","కుర్చీ");  te.put("couch","సోఫా");
        te.put("bed","మంచం");      te.put("laptop","ల్యాప్‌టాప్");
        te.put("cell phone","మొబైల్"); te.put("tv","టీవీ");
        te.put("dining table","డైనింగ్ టేబుల్"); te.put("car","కారు");
        te.put("bus","బస్సు"); te.put("truck","ట్రక్కు");
        te.put("bicycle","సైకిల్"); te.put("motorcycle","మోటార్‌బైక్");
        te.put("dog","కుక్క"); te.put("cat","పిల్లి");
        te.put("backpack","బ్యాగ్"); te.put("clock","గడియారం");
        LABELS_TELUGU = Collections.unmodifiableMap(te);

        Map<String,String> ta = new HashMap<>();
        ta.put("person","நபர்"); ta.put("book","புத்தகம்");
        ta.put("bottle","பாட்டில்"); ta.put("cup","கோப்பை");
        ta.put("chair","நாற்காலி"); ta.put("couch","சோஃபா");
        ta.put("bed","படுக்கை"); ta.put("laptop","லேப்டாப்");
        ta.put("cell phone","மொபைல்"); ta.put("tv","தொலைக்காட்சி");
        ta.put("dining table","சாப்பிட மேசை"); ta.put("car","கார்");
        ta.put("bus","பஸ்"); ta.put("truck","லாரி");
        ta.put("bicycle","சைக்கிள்"); ta.put("motorcycle","மோட்டார் சைக்கிள்");
        ta.put("dog","நாய்"); ta.put("cat","பூனை");
        ta.put("backpack","பை"); ta.put("clock","கடிகாரம்");
        LABELS_TAMIL = Collections.unmodifiableMap(ta);

        Map<String,String> mr = new HashMap<>();
        mr.put("person","व्यक्ती"); mr.put("book","पुस्तक");
        mr.put("bottle","बाटली");   mr.put("cup","कप");
        mr.put("chair","खुर्ची");  mr.put("couch","सोफा");
        mr.put("bed","बिछाना");    mr.put("laptop","लॅपटॉप");
        mr.put("cell phone","मोबाईल"); mr.put("tv","टीव्ही");
        mr.put("dining table","जेवणाचे टेबल"); mr.put("car","कार");
        mr.put("bus","बस"); mr.put("truck","ट्रक");
        mr.put("bicycle","सायकल"); mr.put("motorcycle","मोटारसायकल");
        mr.put("dog","कुत्रा"); mr.put("cat","मांजर");
        mr.put("backpack","बॅग"); mr.put("clock","घड्याळ");
        LABELS_MARATHI = Collections.unmodifiableMap(mr);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        initViews();
        handler  = new Handler(Looper.getMainLooper());
        exec     = Executors.newSingleThreadExecutor();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        try {
            cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            cameraId      = cameraManager.getCameraIdList()[0];
        } catch (Exception e) {
            Log.w(TAG, "Torch init failed: " + e.getMessage());
        }

        // Restore saved language preference
        SharedPreferences prefs = getSharedPreferences("va", MODE_PRIVATE);
        String saved = prefs.getString("lang", null);
        if (saved != null) {
            try { lang = AppLanguage.valueOf(saved); langSet = true; }
            catch (Exception ignored) {}
        }

        gemini = new GeminiClient();

        try { detector = new ObjectDetector(this); }
        catch (Exception e) { Log.e(TAG, "Detector init: " + e.getMessage()); }

        tts = new SarvamTTS(this, () -> {
            ttsReady = true;
            Log.d(TAG, "TTS ready");
            handler.post(this::checkAndStartFlow);
        });

        // Safety timeout — if camera or TTS never becomes ready, tell the user
        handler.postDelayed(() -> {
            if (!startupDone) {
                startupDone = true;
                if (!cameraReady)
                    tts.speakWithGoogle(
                            "Camera not available. Please check permissions and restart.", false);
            }
        }, 10000);

        btnCapture.setOnClickListener(v -> captureAndDetect());
        btnLanguage.setOnClickListener(v -> triggerLanguageChange());
        btnVoice.setOnClickListener(v -> { if (!ttsSpeaking) startListening(); });
        btnHelp.setOnClickListener(v -> speakThenListen(getHelp(lang)));

        handler.postDelayed(this::checkBattery, 8000);
        requestPermissions();
    }

    private void initViews() {
        previewView      = findViewById(R.id.previewView);
        btnCapture       = findViewById(R.id.btnCapture);
        btnLanguage      = findViewById(R.id.btnLanguage);
        btnVoice         = findViewById(R.id.btnVoice);
        btnHelp          = findViewById(R.id.btnHelp);
        tvStatus         = findViewById(R.id.tvStatus);
        tvResult         = findViewById(R.id.tvResult);
        tvLanguage       = findViewById(R.id.tvLanguage);
        tvMicStatus      = findViewById(R.id.tvMicStatus);
        tvProcessing     = findViewById(R.id.tvProcessing);
        tvObjects        = findViewById(R.id.tvObjects);
        tvBattery        = findViewById(R.id.tvBattery);
        tvConfidence     = findViewById(R.id.tvConfidence);
        cardResult       = findViewById(R.id.cardResult);
        layoutProcessing = findViewById(R.id.layoutProcessing);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exec     != null) exec.shutdown();
        if (detector != null) detector.close();
        if (tts      != null) tts.shutdown();
        if (sr       != null) sr.destroy();
        setTorch(false);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  STARTUP
    // ═══════════════════════════════════════════════════════════════════

    private void checkAndStartFlow() {
        if (ttsReady && cameraReady && !startupDone) {
            startupDone = true;
            handler.postDelayed(this::startupFlow, 500);
        }
    }

    private void startupFlow() {
        Log.d(TAG, "startupFlow — langSet=" + langSet);
        vibrate(200);
        setStatus("Ready");

        if (!langSet) {
            // First launch: ask user to choose language
            firstTimeWelcome();
        } else {
            tts.setLanguage(lang);
            updateLangUI();
            // FIX (Bug 2): Only ONE greeting on launch.
            // Previously: "Visual Assistant is open." + getReady() ran together,
            // and applyLang() added a second "Visual Assistant ready." on top.
            // Now: single clean greeting that goes straight to listening.
            speakThenListen(getWelcomeBack(lang));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PERMISSIONS AND CAMERA
    // ═══════════════════════════════════════════════════════════════════

    private void onPermissionsGranted() { startCamera(); }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                provider.unbindAll();
                provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                Log.d(TAG, "Camera ready");
                cameraReady = true;
                checkAndStartFlow();
            } catch (Exception e) {
                Log.e(TAG, "Camera init failed: " + e.getMessage());
                setStatus("Camera error");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        String[] required = { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO };
        for (String p : required)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        if (needed.isEmpty()) onPermissionsGranted();
        else ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_ALL);
    }

    @Override
    public void onRequestPermissionsResult(int code,
                                           @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean ok = true;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) { ok = false; break; }
        if (ok) onPermissionsGranted();
        else tts.speakWithGoogle("Camera and microphone permissions are required.", false);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FIRST TIME WELCOME
    // ═══════════════════════════════════════════════════════════════════

    private void firstTimeWelcome() {
        setStatus("Welcome");
        setMic("Listening for language...");
        String msg = "Welcome to Visual Assistant. "
                + "I am your AI powered vision guide. "
                + "Which language do you prefer? "
                + "Say English, Hindi, Kannada, Telugu, Tamil, or Marathi.";
        // Use Google TTS (no callback) then open mic after measured delay.
        // 9000ms covers the ~8s it takes Google TTS to speak the welcome message.
        tts.speakWithGoogle(msg, false);
        handler.postDelayed(this::openLangMic, 9000);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LANGUAGE SELECTION
    //  FIX (Bug 1): Completely rewritten.
    //
    //  Root cause of the original bug:
    //  • startVoiceLanguageSelect() called speakWithGoogle() (fire-and-forget,
    //    no callback) then used a fixed 6s timer before listenForLang().
    //  • The 6s prompt is ~5s of speech. During those 5s the mic was
    //    NOT open yet. But the flow then immediately tried listenRaw()
    //    while ttsSpeaking was still false (speakWithGoogle doesn't set it),
    //    so it opened the mic while the TTS audio was still playing and
    //    picked up the TTS voice as the user's command.
    //  • Additionally, handleCommand() was calling startVoiceLanguageSelect()
    //    from inside the normal listening callback which left isListening and
    //    sr in a torn state.
    //
    //  Fix:
    //  • triggerLanguageChange() is the single entry point for language change.
    //    It stops all current audio/listening, speaks the prompt via
    //    speakThenOpenLangMic() which uses a real TTS callback, so the mic
    //    only opens AFTER the prompt has fully finished.
    //  • openLangMic() is the only place that starts language listening.
    //    listenForLangMode flag ensures the normal command handler is
    //    bypassed while we are waiting for a language word.
    // ═══════════════════════════════════════════════════════════════════

    /** Called from button click or voice command "language / change". */
    private void triggerLanguageChange() {
        // Stop whatever is happening right now
        stopListening();
        ttsSpeaking      = false;  // reset in case TTS callback was lost
        listenForLangMode = true;
        setMic("Say language name...");
        setStatus("Language selection");

        // Speak the prompt, then open mic only after speech fully finishes.
        String prompt = "Which language? Say English, Hindi, Kannada, Telugu, Tamil, or Marathi.";
        ttsSpeaking = true;
        tts.speak(prompt, false, new SarvamTTS.SpeakCallback() {
            @Override public void onDone() {
                ttsSpeaking = false;
                // Wait 800ms for audio tail to clear, then open mic
                handler.postDelayed(MainActivity.this::openLangMic, 800);
            }
        });
    }

    /** Opens the microphone specifically for language selection. */
    private void openLangMic() {
        if (!listenForLangMode) return; // mode was cancelled
        setMic("🎤 Say your language...");
        setStatus("Listening for language...");

        listenRaw("en-IN", spoken -> {
            Log.d(TAG, "Lang heard: [" + spoken + "]");
            AppLanguage detected = detectLangFromSpeech(spoken);
            if (detected != null) {
                listenForLangMode = false;
                applyLang(detected);
            } else {
                // Not recognised — tell the user and try once more
                ttsSpeaking = true;
                tts.speak("Please say English, Hindi, Kannada, Telugu, Tamil, or Marathi.",
                        false, new SarvamTTS.SpeakCallback() {
                            @Override public void onDone() {
                                ttsSpeaking = false;
                                handler.postDelayed(MainActivity.this::openLangMic, 800);
                            }
                        });
            }
        });
    }

    private AppLanguage detectLangFromSpeech(String spoken) {
        if (spoken == null) return null;
        String s = spoken.toLowerCase().trim();
        Log.d(TAG, "Detecting language from: [" + s + "]");

        // Try AppLanguage.fromSpoken() first (covers native script words)
        AppLanguage fromEnum = AppLanguage.fromSpoken(s);
        if (fromEnum != null) return fromEnum;

        // Extra phonetic variants common on Indian STT engines
        if (s.contains("english") || s.contains("inglish"))  return AppLanguage.ENGLISH;
        if (s.contains("hindi")   || s.contains("hindee"))   return AppLanguage.HINDI;
        if (s.contains("kannada") || s.contains("kannad")
                || s.contains("canada") || s.contains("kanada")
                || s.contains("kanna"))                       return AppLanguage.KANNADA;
        if (s.contains("telugu")  || s.contains("telgu")
                || s.contains("telegu"))                      return AppLanguage.TELUGU;
        if (s.contains("tamil")   || s.contains("tamizh")
                || s.contains("tameel"))                      return AppLanguage.TAMIL;
        if (s.contains("marathi") || s.contains("marati")
                || s.contains("marathee"))                    return AppLanguage.MARATHI;

        return null;
    }

    private void applyLang(AppLanguage l) {
        lang    = l;
        langSet = true;
        getSharedPreferences("va", MODE_PRIVATE)
                .edit().putString("lang", l.name()).apply();
        tts.setLanguage(l);
        updateLangUI();
        vibrate(100);
        Log.d(TAG, "Language set to: " + l.displayName);

        // FIX (Bug 2): applyLang previously said "Visual Assistant ready."
        // PLUS getReady() which repeated the same instructions, causing a
        // double announcement layered on top of startupFlow's own greeting.
        // Now it says a single short confirmation, then goes to listening.
        // On first-launch the user only hears: confirm → ready instructions → mic.
        ttsSpeaking = true;
        tts.speak(getConfirm(l), false, new SarvamTTS.SpeakCallback() {
            @Override public void onDone() {
                ttsSpeaking = false;
                // Brief pause, then single ready message + start listening
                handler.postDelayed(() -> speakThenListen(getReady(l)), 500);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  VOICE COMMANDS
    // ═══════════════════════════════════════════════════════════════════

    private void startListening() {
        // Do not start if TTS is speaking, already listening, busy processing,
        // or we are in language-selection mode (that has its own mic loop).
        if (busy || isListening || ttsSpeaking || listenForLangMode) return;
        isListening = true;
        setMic("🎤 Listening...");
        listenRaw("en-IN", spoken -> {
            isListening = false;
            Log.d(TAG, "Command heard: [" + spoken + "]");
            handleCommand(spoken);
        });
    }

    private void stopListening() {
        isListening = false;
        if (sr != null) { sr.destroy(); sr = null; }
    }

    private void handleCommand(String spoken) {
        if (spoken == null || spoken.isEmpty()) {
            handler.postDelayed(this::startListening, 400);
            return;
        }
        String s = spoken.toLowerCase().trim();
        Log.d(TAG, "Matching: [" + s + "]");

        if (lang.isCaptureCommand(s)
                || s.contains("capture") || s.contains("photo")
                || s.contains("scan")    || s.contains("picture")
                || s.contains("click")   || s.contains("take")) {
            captureAndDetect();

        } else if (lang.isRepeatCommand(s)
                || s.contains("repeat") || s.contains("again")
                || s.contains("replay")) {
            handleRepeat();

        } else if (lang.isLanguageCommand(s)
                || s.contains("language") || s.contains("change")
                || s.contains("switch")) {
            // FIX (Bug 1): was calling startVoiceLanguageSelect() which had
            // the timing / torn-state bug. Now routes through triggerLanguageChange()
            // which properly stops listening before speaking the prompt.
            triggerLanguageChange();

        } else if (lang.isHelpCommand(s)
                || s.contains("help") || s.contains("command")
                || s.contains("guide")) {
            speakThenListen(getHelp(lang));

        } else {
            Log.d(TAG, "Unknown command: [" + s + "]");
            handler.postDelayed(this::startListening, 500);
        }
    }

    private void handleRepeat() {
        if (!lastDesc.isEmpty()) {
            setMic("🔁 Repeating...");
            tts.speak(lastDesc, false, new SarvamTTS.SpeakCallback() {
                @Override public void onDone() {
                    handler.post(() -> startListening());
                }
            });
        } else {
            speakThenListen(getNoRepeat(lang));
        }
    }

    /** Speak text via Sarvam TTS, then open mic. Mic only opens after audio ends. */
    private void speakThenListen(String text) {
        ttsSpeaking = true;
        tts.speak(text, false, new SarvamTTS.SpeakCallback() {
            @Override public void onDone() {
                ttsSpeaking = false;
                handler.postDelayed(() -> startListening(), 1200);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CAPTURE AND DETECT
    // ═══════════════════════════════════════════════════════════════════

    private void captureAndDetect() {
        if (busy || imageCapture == null) return;
        busy        = true;
        isListening = false;
        vibrate(80);
        setStatus("Capturing...");
        setMic("📷 Taking photo...");
        showProcessing(true, "Taking photo...");
        btnCapture.setEnabled(false);

        imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy img) {
                        Bitmap bmp = toBitmap(img);
                        img.close();
                        if (bmp != null && isDark(bmp)) {
                            setTorch(true);
                            handler.postDelayed(() -> retakeWithTorch(), 600);
                        } else if (bmp != null) {
                            runDetection(bmp);
                        } else {
                            captureError();
                        }
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.e(TAG, "Capture error: " + e.getMessage());
                        captureError();
                    }
                });
    }

    private void retakeWithTorch() {
        if (imageCapture == null) { setTorch(false); captureError(); return; }
        imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy img) {
                        Bitmap bmp = toBitmap(img);
                        img.close();
                        setTorch(false);
                        if (bmp != null) runDetection(bmp); else captureError();
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        setTorch(false); captureError();
                    }
                });
    }

    private void captureError() {
        handler.post(() -> {
            busy = false;
            showProcessing(false, "");
            btnCapture.setEnabled(true);
            speakThenListen(getCaptureError(lang));
        });
    }

    private void runDetection(Bitmap bmp) {
        lastBmpW = bmp.getWidth();
        lastBmpH = bmp.getHeight();
        showProcessing(true, "Detecting objects...");

        exec.execute(() -> {
            List<ObjectDetector.Detection> dets;
            try {
                dets = detector.detect(bmp);
                Log.d(TAG, "Detections: " + dets.size());
            } catch (Exception e) {
                Log.e(TAG, "Detection error: " + e.getMessage());
                dets = new ArrayList<>();
            }

            final List<ObjectDetector.Detection> finalDets = dets;

            handler.post(() -> {
                btnCapture.setEnabled(true);

                if (finalDets.isEmpty()) {
                    busy = false;
                    showProcessing(false, "");
                    String msg = getNoObj(lang);
                    setResult(msg, "");
                    speakThenListen(msg);
                    return;
                }

                Map<String, Integer> counts = new HashMap<>();
                boolean hasEmerg = false;
                for (ObjectDetector.Detection d : finalDets) {
                    counts.put(d.label, counts.getOrDefault(d.label, 0) + 1);
                    if (d.isEmergency) hasEmerg = true;
                }

                StringBuilder objStr = new StringBuilder("Detected: ");
                for (Map.Entry<String, Integer> e : counts.entrySet()) {
                    objStr.append(e.getKey());
                    if (e.getValue() > 1) objStr.append("×").append(e.getValue());
                    objStr.append("  ");
                }

                showProcessing(true, "Generating description...");
                setStatus("Analyzing...");
                if (hasEmerg) vibrate(300);

                boolean fEmerg  = hasEmerg;
                String  fObjStr = objStr.toString();
                int     fBmpW   = lastBmpW;
                int     fBmpH   = lastBmpH;

                // FIX (Bug 3 & 4): Gemini was silently failing because ApiConfig.GEMINI_URL
                // contained an empty key (BuildConfig.GEMINI_API_KEY was not set in
                // local.properties). The key is now directly in ApiConfig again so the
                // URL is valid. When Gemini succeeds you get a real scene description;
                // the fallback is only reached on actual network failure.
                gemini.describeScene(
                        finalDets, counts, lang, fEmerg, fBmpW, fBmpH, detector,
                        new GeminiClient.GeminiCallback() {
                            @Override
                            public void onSuccess(String desc) {
                                handler.post(() -> {
                                    lastDesc = desc;
                                    busy     = false;
                                    showProcessing(false, "");
                                    setResult(desc, fObjStr);
                                    setStatus("Done ✓");
                                    speakThenListen(desc);
                                });
                            }
                            @Override
                            public void onError(String err) {
                                Log.e(TAG, "Gemini error: " + err);
                                String fb = fallback(counts, lang);
                                handler.post(() -> {
                                    lastDesc = fb;
                                    busy     = false;
                                    showProcessing(false, "");
                                    setResult(fb, fObjStr);
                                    setStatus("Done (offline)");
                                    speakThenListen(fb);
                                });
                            }
                        });
            });
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  SPEECH RECOGNIZER
    // ═══════════════════════════════════════════════════════════════════

    private void listenRaw(String locale, OnSpoken cb) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SR not available");
            handler.postDelayed(() -> listenRaw(locale, cb), 2000);
            return;
        }
        if (sr != null) { sr.destroy(); sr = null; }

        sr = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,            locale);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale);
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L);
        intent.putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L);
        intent.putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 400L);

        sr.setRecognitionListener(new RecognitionListener() {

            @Override public void onReadyForSpeech(Bundle p) {
                Log.d(TAG, "Mic open");
                setMic("🎤 Listening...");
                listenRetries = 0;
            }
            @Override public void onBeginningOfSpeech() {
                setMic("🎤 Hearing...");
            }
            @Override public void onEndOfSpeech() {
                setMic("⏳ Processing...");
            }

            @Override public void onResults(Bundle r) {
                isListening   = false;
                listenRetries = 0;
                ArrayList<String> matches =
                        r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                Log.d(TAG, "Heard: " + matches);
                if (matches != null && !matches.isEmpty()) {
                    cb.spoken(matches.get(0));
                } else {
                    if (!busy && !listenForLangMode)
                        handler.postDelayed(() -> startListening(), 500);
                }
            }

            @Override public void onError(int error) {
                isListening = false;
                Log.d(TAG, "SR err " + error + getErrorDesc(error));
                setMic("🎤 Say command...");

                if (busy) return;

                // If we are in language-selection mode, errors just re-open the mic.
                // We don't want to fall back to the normal command startListening().
                if (listenForLangMode) {
                    if (error == 8) {
                        if (sr != null) { sr.destroy(); sr = null; }
                        handler.postDelayed(MainActivity.this::openLangMic, 2500);
                    } else {
                        handler.postDelayed(MainActivity.this::openLangMic, 800);
                    }
                    return;
                }

                switch (error) {
                    case 8:
                        if (sr != null) { sr.destroy(); sr = null; }
                        handler.postDelayed(() -> startListening(), 2500);
                        break;
                    case 6:
                    case 7:
                        listenRetries++;
                        if (listenRetries <= 3)
                            handler.postDelayed(() -> startListening(), 800);
                        else { listenRetries = 0; setMic("Tap mic to speak"); }
                        break;
                    case 1:
                    case 2:
                        setMic("No internet. Tap mic to retry.");
                        break;
                    default:
                        handler.postDelayed(() -> startListening(), 700);
                }
            }

            @Override public void onPartialResults(Bundle b) {
                ArrayList<String> p =
                        b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (p != null && !p.isEmpty()) Log.d(TAG, "Partial: " + p.get(0));
            }

            @Override public void onRmsChanged(float v)    {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEvent(int t, Bundle b)  {}
        });

        sr.startListening(intent);
        Log.d(TAG, "SR started (" + locale + ")");
    }

    private String getErrorDesc(int e) {
        switch (e) {
            case 1: return ": Network timeout";
            case 2: return ": No internet";
            case 3: return ": Audio error";
            case 5: return ": Client error";
            case 6: return ": No speech";
            case 7: return ": No match";
            case 8: return ": Recognizer busy";
            case 9: return ": Mic permission denied";
            default: return ": Unknown " + e;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FLASHLIGHT
    // ═══════════════════════════════════════════════════════════════════

    private boolean isDark(Bitmap bmp) {
        int brightness = 0, total = 0;
        int step = Math.max(bmp.getWidth() / 10, 1);
        for (int x = 0; x < bmp.getWidth(); x += step)
            for (int y = 0; y < bmp.getHeight(); y += step) {
                int px = bmp.getPixel(x, y);
                brightness += ((px>>16&0xFF) + (px>>8&0xFF) + (px&0xFF)) / 3;
                total++;
            }
        return total > 0 && (brightness / total) < 60;
    }

    private void setTorch(boolean on) {
        try { if (cameraManager != null && cameraId != null)
            cameraManager.setTorchMode(cameraId, on);
        } catch (Exception e) { Log.w(TAG, "Torch: " + e.getMessage()); }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BATTERY
    // ═══════════════════════════════════════════════════════════════════

    private void checkBattery() {
        try {
            Intent bi = registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bi == null) return;
            int level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (scale <= 0) return;
            int pct = (level * 100) / scale;
            setBattery((pct > 50 ? "🔋" : pct > 20 ? "🪫" : "⚠️") + " " + pct + "%");
            if (pct <= 15 && !busy) { speakThenListen(getBatteryWarn(lang, pct)); return; }
        } catch (Exception e) { Log.w(TAG, "Battery: " + e.getMessage()); }
        handler.postDelayed(this::checkBattery, 120_000);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BITMAP HELPER
    // ═══════════════════════════════════════════════════════════════════

    private Bitmap toBitmap(ImageProxy img) {
        try {
            ByteBuffer buf   = img.getPlanes()[0].getBuffer();
            byte[]     bytes = new byte[buf.remaining()];
            buf.get(bytes);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) { Log.e(TAG, "BitmapFactory returned null"); return null; }
            int rotation = img.getImageInfo().getRotationDegrees();
            if (rotation == 0) return bmp;
            Matrix m = new Matrix();
            m.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            bmp.recycle();
            return rotated;
        } catch (Exception e) { Log.e(TAG, "toBitmap: " + e.getMessage()); return null; }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  OFFLINE FALLBACK — uses translated labels
    // ═══════════════════════════════════════════════════════════════════

    private String translateLabel(String label, AppLanguage l) {
        switch (l) {
            case KANNADA: return LABELS_KANNADA.getOrDefault(label, label);
            case HINDI:   return LABELS_HINDI.getOrDefault(label, label);
            case TELUGU:  return LABELS_TELUGU.getOrDefault(label, label);
            case TAMIL:   return LABELS_TAMIL.getOrDefault(label, label);
            case MARATHI: return LABELS_MARATHI.getOrDefault(label, label);
            default:      return label;
        }
    }

    private String fallback(Map<String, Integer> counts, AppLanguage l) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String lbl = translateLabel(e.getKey(), l);
            int    cnt = e.getValue();
            String c   = cnt > 1 ? cnt + " " : "";
            switch (l) {
                case KANNADA: sb.append(c).append(lbl).append(" ಕಾಣಿಸುತ್ತಿದೆ. "); break;
                case HINDI:   sb.append(c).append(lbl).append(" दिख रहा है. ");    break;
                case TELUGU:  sb.append(c).append(lbl).append(" కనిపిస్తోంది. ");  break;
                case TAMIL:   sb.append(c).append(lbl).append(" தெரிகிறது. ");      break;
                case MARATHI: sb.append(c).append(lbl).append(" दिसत आहे. ");       break;
                default:      sb.append(c).append(lbl).append(" detected. ");
            }
        }
        return sb.toString().trim();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private void setStatus(String s)  { handler.post(() -> tvStatus.setText(s)); }
    private void setMic(String s)     { handler.post(() -> tvMicStatus.setText(s)); }
    private void setBattery(String s) { handler.post(() -> tvBattery.setText(s)); }

    private void setResult(String desc, String objs) {
        handler.post(() -> {
            tvResult.setText(desc);
            tvObjects.setText(objs);
            tvConfidence.setText("Gemini AI ✓");
            cardResult.setVisibility(View.VISIBLE);
        });
    }

    private void showProcessing(boolean show, String msg) {
        handler.post(() -> {
            layoutProcessing.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) { tvProcessing.setText(msg); cardResult.setVisibility(View.GONE); }
        });
    }

    private void updateLangUI() {
        handler.post(() -> tvLanguage.setText(lang.name().substring(0, 2)));
    }

    private void vibrate(int ms) {
        try { if (vibrator != null)
            vibrator.vibrate(VibrationEffect.createOneShot(ms,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LANGUAGE STRINGS
    // ═══════════════════════════════════════════════════════════════════

    // Spoken once on every launch when language is already set.
    // Contains all 4 commands so the user always knows what they can do.
    private String getWelcomeBack(AppLanguage l) {
        switch (l) {
            case KANNADA:
                return "Visual Assistant ready ಆಗಿದೆ. "
                        + "Photo ತೆಗೆಯಲು capture ಎಂದು ಹೇಳಿ. "
                        + "ಭಾಷೆ ಬದಲಿಸಲು language ಎಂದು ಹೇಳಿ. "
                        + "ಮತ್ತೆ ಕೇಳಲು repeat ಎಂದು ಹೇಳಿ. "
                        + "ಎಲ್ಲಾ commands ತಿಳಿಯಲು help ಎಂದು ಹೇಳಿ.";
            case HINDI:
                return "Visual Assistant ready hai. "
                        + "Photo ke liye capture bolein. "
                        + "Bhaasha badalne ke liye language bolein. "
                        + "Dobara sunne ke liye repeat bolein. "
                        + "Saare commands ke liye help bolein.";
            case TELUGU:
                return "Visual Assistant ready ga undi. "
                        + "Photo ki capture anandi. "
                        + "Bhaasha maarkkaniki language anandi. "
                        + "Malli vinataniki repeat anandi. "
                        + "Anni commands ki help anandi.";
            case TAMIL:
                return "Visual Assistant ready aagividdu. "
                        + "Photo ku capture sollunga. "
                        + "Mozhi maarka language sollunga. "
                        + "Meeendum ketka repeat sollunga. "
                        + "Ella commands ku help sollunga.";
            case MARATHI:
                return "Visual Assistant ready aahe. "
                        + "Photo sathi capture mhana. "
                        + "Bhasha badlanya sathi language mhana. "
                        + "Puna aaikanya sathi repeat mhana. "
                        + "Sarva commands sathi help mhana.";
            default:
                return "Visual Assistant is ready. "
                        + "Say capture to take a photo. "
                        + "Say language to change language. "
                        + "Say repeat to hear the last result. "
                        + "Say help for all commands.";
        }
    }

    // Spoken after first-time language selection (via applyLang).
    // Same 4 commands as getWelcomeBack so first-time users get the full guide.
    private String getReady(AppLanguage l) {
        switch (l) {
            case KANNADA:
                return "Photo ತೆಗೆಯಲು capture ಎಂದು ಹೇಳಿ. "
                        + "ಭಾಷೆ ಬದಲಿಸಲು language ಎಂದು ಹೇಳಿ. "
                        + "ಮತ್ತೆ ಕೇಳಲು repeat ಎಂದು ಹೇಳಿ. "
                        + "ಎಲ್ಲಾ commands ತಿಳಿಯಲು help ಎಂದು ಹೇಳಿ.";
            case HINDI:
                return "Photo ke liye capture bolein. "
                        + "Bhaasha badalne ke liye language bolein. "
                        + "Dobara sunne ke liye repeat bolein. "
                        + "Saare commands ke liye help bolein.";
            case TELUGU:
                return "Photo ki capture anandi. "
                        + "Bhaasha maarkkaniki language anandi. "
                        + "Malli vinataniki repeat anandi. "
                        + "Anni commands ki help anandi.";
            case TAMIL:
                return "Photo ku capture sollunga. "
                        + "Mozhi maarka language sollunga. "
                        + "Meeendum ketka repeat sollunga. "
                        + "Ella commands ku help sollunga.";
            case MARATHI:
                return "Photo sathi capture mhana. "
                        + "Bhasha badlanya sathi language mhana. "
                        + "Puna aaikanya sathi repeat mhana. "
                        + "Sarva commands sathi help mhana.";
            default:
                return "Say capture to take a photo. "
                        + "Say language to change language. "
                        + "Say repeat to hear the last result. "
                        + "Say help for all commands.";
        }
    }

    private String getConfirm(AppLanguage l) {
        switch (l) {
            case KANNADA: return "ಕನ್ನಡ ಆಯ್ಕೆ ಆಗಿದೆ.";
            case HINDI:   return "Hindi set ho gayi.";
            case TELUGU:  return "Telugu set ayindi.";
            case TAMIL:   return "Tamil set aanathu.";
            case MARATHI: return "Marathi set zhali.";
            default:      return "English is set.";
        }
    }

    private String getHelp(AppLanguage l) {
        switch (l) {
            case KANNADA:
                return "Capture — photo ತೆಗೆಯಲು. "
                        + "Repeat — ಮತ್ತೆ ಕೇಳಲು. "
                        + "Language — ಭಾಷೆ ಬದಲಿಸಲು. "
                        + "Help — ಈ message ಮತ್ತೆ ಕೇಳಲು.";
            case HINDI:
                return "Capture — photo ke liye. "
                        + "Repeat — dobara sunne ke liye. "
                        + "Language — bhaasha badalne ke liye. "
                        + "Help — yeh message sunne ke liye.";
            case TELUGU:
                return "Capture — photo ki. "
                        + "Repeat — malli vinataniki. "
                        + "Language — bhaasha maarkkaniki. "
                        + "Help — ee message ki.";
            case TAMIL:
                return "Capture — photo ku. "
                        + "Repeat — meeendum ketka. "
                        + "Language — mozhi maarka. "
                        + "Help — ee message ku.";
            case MARATHI:
                return "Capture — photo sathi. "
                        + "Repeat — puna aaikanya sathi. "
                        + "Language — bhasha badlanya sathi. "
                        + "Help — haa message sathi.";
            default:
                return "Say capture to scan surroundings. "
                        + "Say repeat to hear last result. "
                        + "Say language to change language. "
                        + "Say help to hear this message.";
        }
    }

    private String getNoObj(AppLanguage l) {
        switch (l) {
            case KANNADA: return "ಯಾವ ವಸ್ತುವೂ ಕಾಣಿಸಲಿಲ್ಲ. Camera ಸರಿಯಾಗಿ ತೋರಿಸಿ ಮತ್ತೆ try ಮಾಡಿ.";
            case HINDI:   return "Koi cheez nahi dikhi. Camera sahi karein aur dobara try karein.";
            case TELUGU:  return "Emi kanipinchaledhu. Camera sarigga pattukoni malli try cheyyandi.";
            case TAMIL:   return "Ethuvum theriyavillai. Camera sariyaaga vaithu meeendum try pannunga.";
            case MARATHI: return "Kahi disle nahi. Camera neet dhara ani puna try kara.";
            default:      return "Nothing detected. Point the camera at objects and try again.";
        }
    }

    private String getNoRepeat(AppLanguage l) {
        switch (l) {
            case KANNADA: return "ಇನ್ನೂ ಏನೂ detect ಆಗಿಲ್ಲ. Capture ಎಂದು ಹೇಳಿ photo ತೆಗೆಯಿರಿ.";
            case HINDI:   return "Abhi kuch detect nahi hua. Capture bolkar photo lo.";
            case TELUGU:  return "Inka emi detect kaaledhu. Capture ani photo tegavandi.";
            case TAMIL:   return "Innum ethuvaum detect aagavillai. Capture sollu photo edukka.";
            case MARATHI: return "Ajun kahi detect zale nahi. Capture mhana photo kadhayala.";
            default:      return "Nothing to repeat yet. Say capture to take a photo first.";
        }
    }

    private String getCaptureError(AppLanguage l) {
        switch (l) {
            case KANNADA: return "Photo ತೆಗೆಯಲಾಗಲಿಲ್ಲ. ಮತ್ತೆ try ಮಾಡಿ.";
            case HINDI:   return "Photo nahi li gayi. Dobara try karein.";
            case TELUGU:  return "Photo teeyaledhu. Malli try cheyyandi.";
            case TAMIL:   return "Photo edukkavillai. Meeendum try pannunga.";
            case MARATHI: return "Photo kadhata aali nahi. Puna try kara.";
            default:      return "Capture failed. Please try again.";
        }
    }

    private String getBatteryWarn(AppLanguage l, int pct) {
        switch (l) {
            case KANNADA: return "Battery " + pct + " percent. Dayavittu charge maadi.";
            case HINDI:   return "Battery sirf " + pct + " percent. Kripaya charge karein.";
            case TELUGU:  return "Battery " + pct + " percent. Dayachesi charge cheyyandi.";
            case TAMIL:   return "Battery " + pct + " percent. Thayavu seithu charge seyyunga.";
            case MARATHI: return "Battery fakt " + pct + " percent. Krupaya charge kara.";
            default:      return "Battery is at " + pct + " percent. Please charge soon.";
        }
    }
}