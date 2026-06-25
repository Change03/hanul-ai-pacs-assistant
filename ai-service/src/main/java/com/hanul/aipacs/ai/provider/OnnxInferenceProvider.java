package com.hanul.aipacs.ai.provider;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.hanul.aipacs.ai.domain.ProcessedDicom;
import com.hanul.aipacs.ai.domain.ProviderResult;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class OnnxInferenceProvider implements InferenceProvider {
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;

    public OnnxInferenceProvider(Path modelPath) {
        try {
            if (!Files.exists(modelPath)) {
                throw new IllegalStateException("MODEL_PATH does not exist: " + modelPath);
            }
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(modelPath.toString(), new OrtSession.SessionOptions());
            this.inputName = session.getInputNames().iterator().next();
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to initialize ONNX Runtime", e);
        }
    }

    @Override
    public String name() {
        return "ONNX";
    }

    @Override
    public ProviderResult infer(ProcessedDicom processedDicom) {
        float[] normalized = new float[processedDicom.resized().length];
        for (int i = 0; i < processedDicom.resized().length; i++) {
            normalized[i] = Byte.toUnsignedInt(processedDicom.resized()[i]) / 255.0f;
        }
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(normalized), new long[] {1, 1, 224, 224});
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            Object output = result.get(0).getValue();
            double raw = firstScalar(output);
            double score = 1.0 / (1.0 + Math.exp(-raw));
            String label = score >= 0.5 ? "Opacity demo" : "No acute finding demo";
            return new ProviderResult(name(), label, round(score), List.of(), List.of());
        } catch (OrtException e) {
            throw new IllegalStateException("ONNX inference failed", e);
        }
    }

    private static double firstScalar(Object value) {
        if (value instanceof float[][][][] f4) {
            return f4[0][0][0][0];
        }
        if (value instanceof float[][] f2) {
            return f2[0][0];
        }
        if (value instanceof float[] f1) {
            return f1[0];
        }
        if (value instanceof double[][] d2) {
            return d2[0][0];
        }
        if (value instanceof double[] d1) {
            return d1[0];
        }
        throw new IllegalStateException("Unsupported ONNX output shape");
    }

    private static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
