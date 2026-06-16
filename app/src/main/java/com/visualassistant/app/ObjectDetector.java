package com.visualassistant.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ObjectDetector {

    private static final String TAG         = "ObjectDetector";
    private static final String MODEL_FILE  = "yolov8n_float16.tflite";
    private static final String LABELS_FILE = "labels_coco.txt";
    private static final int    INPUT_SIZE  = 640;

    // Confidence threshold — 0.25 is correct for YOLOv8n float16.
    // The nano + float16 model produces lower raw scores than the full model;
    // 0.40 was silently discarding real detections.
    private static final float CONF = 0.25f;

    // IoU threshold for Non-Maximum Suppression
    private static final float IOU = 0.45f;

    // ── Detection result ────────────────────────────────────────────────
    public static class Detection {
        public String  label;
        public float   confidence;
        public RectF   boundingBox;
        public boolean isEmergency;

        public Detection(String l, float c, RectF b, boolean e) {
            label       = l;
            confidence  = c;
            boundingBox = b;
            isEmergency = e;
        }
    }

    private Interpreter  interpreter;
    private List<String> labels;
    private int          numClasses;

    private static final String[] EMERGENCY = {
            "car", "bus", "truck", "train",
            "motorcycle", "traffic light", "bicycle"
    };

    // ── Constructor ─────────────────────────────────────────────────────
    public ObjectDetector(Context ctx) throws IOException {
        loadLabels(ctx);
        loadModel(ctx);
    }

    // ── Label loading ───────────────────────────────────────────────────
    private void loadLabels(Context ctx) throws IOException {
        labels = new ArrayList<>();
        BufferedReader r = new BufferedReader(
                new InputStreamReader(ctx.getAssets().open(LABELS_FILE)));
        String line;
        while ((line = r.readLine()) != null)
            if (!line.trim().isEmpty())
                labels.add(line.trim());
        r.close();
        numClasses = labels.size();
        Log.d(TAG, "Labels loaded: " + numClasses);
    }

    // ── Model loading ───────────────────────────────────────────────────
    private void loadModel(Context ctx) throws IOException {
        android.content.res.AssetFileDescriptor a =
                ctx.getAssets().openFd(MODEL_FILE);
        FileInputStream fis =
                new FileInputStream(a.getFileDescriptor());
        FileChannel fc = fis.getChannel();
        MappedByteBuffer buf = fc.map(
                FileChannel.MapMode.READ_ONLY,
                a.getStartOffset(),
                a.getDeclaredLength());
        fis.close();

        Interpreter.Options opts = new Interpreter.Options();
        opts.setNumThreads(4);
        interpreter = new Interpreter(buf, opts);

        // Log the actual output tensor shape at runtime so you can
        // confirm it matches the array layout used in detect().
        // For this model it should print: [1, 84, 8400]
        int[] outShape = interpreter.getOutputTensor(0).shape();
        Log.d(TAG, "Model loaded. Output tensor shape: "
                + Arrays.toString(outShape));
        // outShape[1] = 84   (4 bbox values + 80 class scores)
        // outShape[2] = 8400 (anchor candidates)
    }

    // ── Inference ───────────────────────────────────────────────────────
    public List<Detection> detect(Bitmap bitmap) {

        // Step 1 — resize to 640×640
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap, INPUT_SIZE, INPUT_SIZE, true);

        // Step 2 — fill input tensor [1, 640, 640, 3], values normalised 0-1
        float[][][][] in = new float[1][INPUT_SIZE][INPUT_SIZE][3];
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int px = scaled.getPixel(x, y);
                in[0][y][x][0] = ((px >> 16) & 0xFF) / 255f; // R
                in[0][y][x][1] = ((px >> 8)  & 0xFF) / 255f; // G
                in[0][y][x][2] = ( px        & 0xFF) / 255f; // B
            }
        }

        // Step 3 — allocate output tensor and run inference.
        //
        // CONFIRMED LAYOUT for yolov8n_float16.tflite (verified by loading
        // the model in Python and printing interpreter.get_output_details()):
        //
        //   Shape  : [1, 84, 8400]   — channel-first
        //   dim 0  : batch  = 1
        //   dim 1  : 84 channels
        //              channels 0-3  → cx, cy, w, h  (normalised 0-1)
        //              channels 4-83 → class scores for 80 COCO classes
        //   dim 2  : 8400 anchor candidates
        //
        // Java access pattern:
        //   out[0][channel][anchor]
        //   e.g. out[0][0][i] = cx of anchor i
        //        out[0][4+c][i] = score of class c for anchor i
        //
        // *** Do NOT transpose this to [1][8400][84]. ***
        // That layout produces near-zero scores for every anchor
        // (you'd be reading bbox bytes as class scores) and causes
        // "no objects detected" for every frame.
        float[][][] out = new float[1][4 + numClasses][8400];
        interpreter.run(in, out);

        // Step 4 — decode raw output into Detection objects
        return parseOutput(out, bitmap.getWidth(), bitmap.getHeight());
    }

    // ── Output decoding ─────────────────────────────────────────────────
    private List<Detection> parseOutput(float[][][] o, int imgW, int imgH) {
        List<Detection> list = new ArrayList<>();
        int rawCount = 0;

        for (int i = 0; i < 8400; i++) {

            // Read bounding box — channels 0-3, anchor i
            float cx = o[0][0][i];
            float cy = o[0][1][i];
            float bw = o[0][2][i];
            float bh = o[0][3][i];

            // Find best class — channels 4 to 4+numClasses-1, anchor i
            float maxScore  = 0f;
            int   bestClass = -1;
            for (int c = 0; c < numClasses; c++) {
                float s = o[0][4 + c][i];
                if (s > maxScore) {
                    maxScore  = s;
                    bestClass = c;
                }
            }

            // Skip anchors below confidence threshold or with invalid class
            if (maxScore < CONF || bestClass < 0
                    || bestClass >= labels.size()) continue;

            rawCount++;

            // Convert centre-format (cx, cy, w, h) → corner-format (l, t, r, b)
            // Coordinates are normalised 0-1 → multiply by image dimensions
            RectF box = new RectF(
                    (cx - bw / 2f) * imgW,
                    (cy - bh / 2f) * imgH,
                    (cx + bw / 2f) * imgW,
                    (cy + bh / 2f) * imgH);

            String lbl = labels.get(bestClass);
            list.add(new Detection(lbl, maxScore, box, isEmergency(lbl)));
        }

        Log.d(TAG, "Anchors above threshold (before NMS): " + rawCount);
        List<Detection> result = nms(list);
        Log.d(TAG, "Final detections (after NMS): " + result.size());
        return result;
    }

    // ── Non-Maximum Suppression ─────────────────────────────────────────
    // Removes overlapping duplicate boxes, keeping the highest-confidence one.
    private List<Detection> nms(List<Detection> dets) {
        dets.sort((a, b) -> Float.compare(b.confidence, a.confidence));

        List<Detection> kept       = new ArrayList<>();
        boolean[]       suppressed = new boolean[dets.size()];

        for (int i = 0; i < dets.size(); i++) {
            if (suppressed[i]) continue;
            kept.add(dets.get(i));
            for (int j = i + 1; j < dets.size(); j++) {
                if (suppressed[j]) continue;
                if (iou(dets.get(i).boundingBox,
                        dets.get(j).boundingBox) > IOU) {
                    suppressed[j] = true;
                }
            }
        }
        return kept;
    }

    // ── IoU helper ──────────────────────────────────────────────────────
    private float iou(RectF a, RectF b) {
        float iL = Math.max(a.left,   b.left);
        float iT = Math.max(a.top,    b.top);
        float iR = Math.min(a.right,  b.right);
        float iB = Math.min(a.bottom, b.bottom);
        if (iR <= iL || iB <= iT) return 0f;
        float inter = (iR - iL) * (iB - iT);
        float aArea = (a.right - a.left) * (a.bottom - a.top);
        float bArea = (b.right - b.left) * (b.bottom - b.top);
        return inter / (aArea + bArea - inter);
    }

    // ── Emergency check ─────────────────────────────────────────────────
    private boolean isEmergency(String label) {
        for (String e : EMERGENCY)
            if (e.equalsIgnoreCase(label)) return true;
        return false;
    }

    // ── Proximity helper ────────────────────────────────────────────────
    // Returns a spoken proximity string based on bounding-box area
    // relative to the image. Called by GeminiClient for spatial context.
    public String proximity(RectF box, int imgW, int imgH) {
        float area = ((box.right - box.left) * (box.bottom - box.top))
                / (float) (imgW * imgH);
        if (area > 0.30f) return "very close";
        if (area > 0.10f) return "nearby";
        if (area > 0.03f) return "in the distance";
        return "far away";
    }

    // ── Horizontal side helper ──────────────────────────────────────────
    // Returns "left", "center", or "right" based on box centre position.
    // Called by GeminiClient to tell blind users which direction to face.
    public String horizontalSide(RectF box, int imgW) {
        float cx = box.centerX() / imgW;
        if (cx < 0.38f) return "left";
        if (cx > 0.62f) return "right";
        return "center";
    }

    // ── Cleanup ─────────────────────────────────────────────────────────
    public void close() {
        if (interpreter != null) interpreter.close();
    }
}