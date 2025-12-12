package com.example.faceauth.helpers.vision.liveness;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.example.faceauth.helpers.vision.FaceGraphic;
import com.example.faceauth.helpers.vision.GraphicOverlay;
import com.example.faceauth.helpers.vision.VisionBaseProcessor;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.HashMap;
import java.util.List;

public class FaceLivenessDetectorProcessor extends VisionBaseProcessor<List<Face>> {

    private static final String BLINK_LOG = "BlinkDetector";
    private static final String GLASSES_LOG = "GlassesDetector";
    private static final String MANUAL_TESTING_LOG = "LivenessDetector";

    private final FaceDetector detector;
    private final GraphicOverlay graphicOverlay;
    private final HashMap<Integer, FaceLiveness> livenessHashMap = new HashMap<>();

    private final HashMap<Integer, Integer> blinkCountMap = new HashMap<>();
    private final HashMap<Integer, Boolean> isEyesClosedMap = new HashMap<>();

    // NEW: Glasses Detector
    private final GlassesDetector glassesDetector;

    public FaceLivenessDetectorProcessor(GraphicOverlay graphicOverlay) {
        this.graphicOverlay = graphicOverlay;

        // Initialize Glasses Detector
        this.glassesDetector = new GlassesDetector(graphicOverlay.getContext());

        FaceDetectorOptions faceDetectorOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build();
        detector = FaceDetection.getClient(faceDetectorOptions);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public Task<List<Face>> detectInImage(ImageProxy imageProxy, Bitmap originalCameraBitmap, int rotationDegrees) {
        InputImage inputImage = InputImage.fromMediaImage(imageProxy.getImage(), rotationDegrees);

        // Setup for drawing...
        int rotation = rotationDegrees;
        boolean reverseDimens = rotation == 90 || rotation == 270;
        int width;
        int height;
        if (reverseDimens) {
            width = imageProxy.getHeight();
            height =  imageProxy.getWidth();
        } else {
            width = imageProxy.getWidth();
            height = imageProxy.getHeight();
        }

        // Note: originalCameraBitmap might need rotation if it's raw YUV converted.
        // For simplicity, we assume originalCameraBitmap is upright or we use coordinates carefully.
        // Ideally, in VideoHelperActivity, ensure 'toBitmap' handles rotation or use the bitmap provided by MLKit if available differently.
        // A common issue: The bitmap from YUV might be rotated differently than the MLKit InputImage.
        // If your crops look wrong, you might need to rotate 'originalCameraBitmap' by 'rotationDegrees' before cropping.

        return detector.process(inputImage)
                .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                    @Override
                    public void onSuccess(List<Face> faces) {
                        graphicOverlay.clear();

                        if (faces.size() != 1) {
                            if (faces.size() > 1) Log.d(BLINK_LOG, "Multiple faces detected.");
                            return;
                        }

                        Face face = faces.get(0);

                        // --------------------------------------------------------
                        // 1. GLASSES DETECTION LOGIC
                        // --------------------------------------------------------

                        boolean glassesCheckPassed = false;

                        // Crop face from bitmap
                        Rect bounds = face.getBoundingBox();

                        // Ensure bounds are within bitmap limits to avoid crash
                        int safeX = Math.max(0, bounds.left);
                        int safeY = Math.max(0, bounds.top);
                        int safeWidth = Math.min(originalCameraBitmap.getWidth() - safeX, bounds.width());
                        int safeHeight = Math.min(originalCameraBitmap.getHeight() - safeY, bounds.height());

                        if (safeWidth > 0 && safeHeight > 0) {
                            Bitmap faceBitmap = Bitmap.createBitmap(originalCameraBitmap, safeX, safeY, safeWidth, safeHeight);

                            // 0 = no glasses, 1 = glasses
                            float glassesPrediction = glassesDetector.detect(faceBitmap);

                            // Decide logic: Do you REQUIRE glasses or NO glasses?
                            // Example: Require NO glasses to pass
                            if (glassesPrediction < 0.5) {
                                Log.d(GLASSES_LOG, "No Glasses Detected (" + glassesPrediction + "). Proceeding.");
                                glassesCheckPassed = true;
                            } else {
                                Log.d(GLASSES_LOG, "Glasses Detected (" + glassesPrediction + "). Please remove glasses.");
                                // Optionally draw a warning on screen here
                            }
                        }

                        // --------------------------------------------------------
                        // 2. BLINK LOGIC (Only runs if Glasses Check Passed)
                        // --------------------------------------------------------

                        if (glassesCheckPassed) {
                            if (!livenessHashMap.containsKey(face.getTrackingId())) {
                                FaceLiveness faceLiveness = new FaceLiveness();
                                livenessHashMap.put(face.getTrackingId(), faceLiveness);
                                blinkCountMap.put(face.getTrackingId(), 0);
                                isEyesClosedMap.put(face.getTrackingId(), false);
                            }

                            Float leftEyeOpen = face.getLeftEyeOpenProbability();
                            Float rightEyeOpen = face.getRightEyeOpenProbability();
                            float BLINK_THRESHOLD = 0.3f;

                            if (leftEyeOpen != null && rightEyeOpen != null) {
                                int faceId = face.getTrackingId();
                                boolean areBothEyesClosed = (leftEyeOpen < BLINK_THRESHOLD && rightEyeOpen < BLINK_THRESHOLD);
                                boolean wasClosedPreviously = Boolean.TRUE.equals(isEyesClosedMap.get(faceId));

                                if (wasClosedPreviously && !areBothEyesClosed) {
                                    int currentCount = blinkCountMap.getOrDefault(faceId, 0);
                                    int nextCount = (currentCount % 3) + 1;
                                    blinkCountMap.put(faceId, nextCount);
                                    Log.d(BLINK_LOG, "Blink Count: " + nextCount);
                                    if (nextCount == 3) {
                                        Log.d(BLINK_LOG, "✅ Liveness Check Passed (Cycle Complete)!");
                                    }
                                }
                                isEyesClosedMap.put(faceId, areBothEyesClosed);
                            }
                        }

                        // Draw Graphic (Pass generic false for isDrowsy to simplify)
                        FaceGraphic faceGraphic = new FaceGraphic(graphicOverlay, face, false, width, height);
                        graphicOverlay.add(faceGraphic);
                    }
                })
                .addOnFailureListener(e -> Log.e(MANUAL_TESTING_LOG, "Detection failed", e));
    }

    @Override
    public void stop() {
        detector.close();
        if (glassesDetector != null) {
            glassesDetector.close();
        }
    }
}
