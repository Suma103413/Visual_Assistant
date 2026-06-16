package com.visualassistant.app;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiClient {

    private static final String TAG  = "GeminiClient";
    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;

    public interface GeminiCallback {
        void onSuccess(String description);
        void onError(String error);
    }

    public GeminiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS) // slightly extended for Indian networks
                .build();
    }

    /**
     * FIX: Now accepts the full Detection list so we can use
     * proximity() and horizontalSide() to give Gemini spatial context.
     * This produces descriptions like "there is a chair very close on
     * your left" instead of just "a chair is detected."
     *
     * @param dets       full detection list (with bounding boxes)
     * @param counts     label→count map (for quick lookup)
     * @param lang       chosen language
     * @param hasEmergency true if any vehicle/hazard was detected
     * @param imgW       original image width (for proximity calculation)
     * @param imgH       original image height
     * @param detector   ObjectDetector instance (for proximity helper)
     * @param cb         result callback
     */
    public void describeScene(
            List<ObjectDetector.Detection> dets,
            Map<String, Integer> counts,
            AppLanguage lang,
            boolean hasEmergency,
            int imgW,
            int imgH,
            ObjectDetector detector,
            GeminiCallback cb) {

        // FIX: Build a richer context string that includes spatial information.
        // Before: "1x bottle, 2x person"  (Gemini has nothing to work with)
        // After:  "bottle (very close, center), person (nearby, left),
        //          person (nearby, right)"
        StringBuilder spatialCtx = new StringBuilder();
        for (ObjectDetector.Detection d : dets) {
            String prox = detector.proximity(d.boundingBox, imgW, imgH);
            String side = detector.horizontalSide(d.boundingBox, imgW);
            spatialCtx.append(d.label)
                    .append(" (").append(prox)
                    .append(", ").append(side).append("), ");
        }

        String prompt = buildPrompt(
                spatialCtx.toString(), lang, hasEmergency);

        new Thread(() -> {
            try {
                String result = callGemini(prompt);
                cb.onSuccess(result);
            } catch (Exception e) {
                Log.e(TAG, "Gemini error: " + e.getMessage());
                cb.onError(e.getMessage());
            }
        }).start();
    }

    private String buildPrompt(String spatialContext,
                               AppLanguage lang,
                               boolean emergency) {
        // FIX (CRITICAL): The previous prompt said "mixed with English as Indians speak"
        // which caused Gemini to keep English object names and just append native endings,
        // e.g. "book patteyagide" (English noun + Kannada verb) instead of
        // "ಪುಸ್ತಕ ಕಾಣಿಸುತ್ತಿದೆ" (fully Kannada).
        //
        // The new prompt explicitly forbids English object names for non-English languages
        // and gives a concrete good/bad example for each language family.

        String langInstruction;
        String exampleWrong;
        String exampleRight;

        switch (lang) {
            case KANNADA:
                langInstruction = "ಕನ್ನಡ (Kannada)";
                exampleWrong    = "book patteyagide, person kaaṇisuttide";
                exampleRight    = "ಒಬ್ಬ ವ್ಯಕ್ತಿ ಪುಸ್ತಕ ಓದುತ್ತಿದ್ದಾರೆ.";
                break;
            case HINDI:
                langInstruction = "Hindi (हिंदी)";
                exampleWrong    = "bottle dikh raha hai";
                exampleRight    = "एक बोतल पास में दिख रही है।";
                break;
            case TELUGU:
                langInstruction = "Telugu (తెలుగు)";
                exampleWrong    = "bottle kanipistundi";
                exampleRight    = "దగ్గరలో ఒక సీసా కనిపిస్తోంది.";
                break;
            case TAMIL:
                langInstruction = "Tamil (தமிழ்)";
                exampleWrong    = "bottle theriyuthu";
                exampleRight    = "அருகில் ஒரு பாட்டில் தெரிகிறது.";
                break;
            case MARATHI:
                langInstruction = "Marathi (मराठी)";
                exampleWrong    = "bottle disat aahe";
                exampleRight    = "जवळ एक बाटली दिसत आहे.";
                break;
            default:
                langInstruction = "English";
                exampleWrong    = "bottle detected";
                exampleRight    = "There is a bottle right in front of you.";
                break;
        }

        return "You are a helpful assistant for a visually impaired person.\n\n"
                + "Objects detected with their positions:\n"
                + spatialContext + "\n\n"
                + "STRICT RULES — follow every rule exactly:\n"
                + "1. Respond ONLY in " + langInstruction + ".\n"
                + "2. For " + lang.displayName + ": translate ALL object names into "
                + lang.displayName + ". NEVER keep English words for objects.\n"
                + "   WRONG example: \"" + exampleWrong + "\"\n"
                + "   RIGHT example: \"" + exampleRight + "\"\n"
                + "3. Describe the SCENE and what is happening — do NOT list objects.\n"
                + "4. Use the position info (left/right/center, very close/nearby/far)\n"
                + "   to tell the user WHERE things are. This is critical for navigation.\n"
                + (emergency
                ? "5. Start with a clear safety warning about the vehicle — say it is dangerous.\n"
                : "")
                + "6. Count naturally: say 'two people' not 'person, person'.\n"
                + "7. Maximum 2-3 short sentences. Be concise.\n"
                + "8. Speak like a caring friend, not a robot reading a list.\n\n"
                + "Only output the description. No preamble, no labels, no English object names.";
    }

    private String callGemini(String prompt) throws Exception {
        String body;
        try {
            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);

            JSONArray parts = new JSONArray();
            parts.put(textPart);

            JSONObject content = new JSONObject();
            content.put("parts", parts);

            JSONArray contents = new JSONArray();
            contents.put(content);

            JSONObject cfg = new JSONObject();
            cfg.put("temperature",    0.6);  // slightly lower = more consistent translations
            cfg.put("maxOutputTokens", 180); // a touch more room for longer language sentences
            cfg.put("topP",           0.9);

            JSONObject req = new JSONObject();
            req.put("contents",        contents);
            req.put("generationConfig", cfg);
            body = req.toString();
        } catch (Exception e) {
            throw new Exception("Build failed: " + e.getMessage());
        }

        Log.d(TAG, "Calling Gemini. Prompt length: " + prompt.length());

        RequestBody rb = RequestBody.create(body, JSON);
        Request rq = new Request.Builder()
                .url(ApiConfig.GEMINI_URL)
                .post(rb)
                .build();

        try (Response rs = client.newCall(rq).execute()) {
            String responseBody = rs.body().string();
            Log.d(TAG, "HTTP " + rs.code());

            if (!rs.isSuccessful())
                throw new IOException("HTTP " + rs.code()
                        + " — " + responseBody);

            return parse(responseBody);
        }
    }

    private String parse(String json) throws Exception {
        try {
            String text = new JSONObject(json)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim();

            // Remove any accidental markdown formatting Gemini sometimes adds
            text = text.replaceAll("\\*+", "").trim();
            return text;
        } catch (Exception e) {
            throw new Exception("Parse failed: " + e.getMessage()
                    + " | Response: " + json);
        }
    }
}