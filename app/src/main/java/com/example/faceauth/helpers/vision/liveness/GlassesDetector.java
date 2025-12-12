package com.example.faceauth.helpers.vision.liveness;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class GlassesDetector {
    private Interpreter interpreter;
    private static final String MODEL_FILE = "glasses_cnn.tflite";
    private static final int INPUT_SIZE = 96; // Matches your Python IMG_SIZE
    private static final int PIXEL_SIZE = 3; // RGB
    private static final int BYTES_PER_CHANNEL = 4; // Float32

    public GlassesDetector(Context context) {
        try {
            interpreter = new Interpreter(loadModelFile(context));
            Log.d("GlassesDetector", "Model loaded successfully");
        } catch (IOException e) {
            Log.e("GlassesDetector", "Error loading model", e);
        }
    }

    public float detect(Bitmap faceBitmap) {
        if (interpreter == null) return -1f;

        // 1. Preprocess: Resize to 96x96
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);

        // 2. Preprocess: Normalize (MobileNetV2 style: [-1, 1])
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);

        // 3. Output buffer (1x1 float)
        float[][] output = new float[1][1];

        // 4. Run inference
        interpreter.run(inputBuffer, output);

        // Return prediction (0=no glasses, 1=glasses)
        return output[0][0];
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE * BYTES_PER_CHANNEL);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                final int val = intValues[pixel++];

                // MobileNetV2 preprocessing: (pixel / 127.5) - 1
                // This maps [0, 255] to [-1, 1]
                byteBuffer.putFloat((((val >> 16) & 0xFF) / 127.5f) - 1.0f); // Red
                byteBuffer.putFloat((((val >> 8) & 0xFF) / 127.5f) - 1.0f);  // Green
                byteBuffer.putFloat(((val & 0xFF) / 127.5f) - 1.0f);         // Blue
            }
        }
        return byteBuffer;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }
}
