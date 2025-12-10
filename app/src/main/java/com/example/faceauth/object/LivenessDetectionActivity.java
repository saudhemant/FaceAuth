package com.example.faceauth.object;

import android.os.Bundle;

import androidx.camera.core.CameraSelector;

import com.example.faceauth.helpers.VideoHelperActivity;
import com.example.faceauth.helpers.vision.liveness.FaceLivenessDetectorProcessor;
import com.example.faceauth.helpers.vision.VisionBaseProcessor;

public class LivenessDetectionActivity extends VideoHelperActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    protected VisionBaseProcessor setProcessor() {
        return new FaceLivenessDetectorProcessor(graphicOverlay);
    }

    @Override
    protected int getLensFacing() {
        return CameraSelector.LENS_FACING_FRONT;
    }
}