package com.example.activitytracker;

import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.activitytracker.Helper;

public class Inference {

    public static Prediction doFeatureNNInference(float[] input, Interpreter modelInterpreter) {
        float[][] result = new float[1][6];

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("x_input", input);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", result);

        modelInterpreter.runSignature(inputs, outputs, "serving_default");

        float[] prediction = ((float[][]) outputs.get("output"))[0];

        int argmax = Helper.getArgMax(prediction);
        double confidence = softmax(prediction[argmax], Helper.convertFloatsToDoubles(prediction));
        return new Prediction(argmax, confidence);
    }

    public static Prediction doKNNInference(float[] input, Interpreter modelInterpreter) {
        int[] result = new int[1];

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("tensor", input);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("result", result);

        modelInterpreter.runSignature(inputs, outputs, "predict");

        int prediction = ((int[]) outputs.get("result"))[0];
        return new Prediction(prediction, 0);
    }

    public static Prediction doRawNNInference(float[][][] input, Interpreter modelInterpreter) {

        float[][] result = new float[1][6];

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("inputs", input);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output_0", result);

        modelInterpreter.runSignature(inputs, outputs, "input");

        float[] prediction = ((float[][]) outputs.get("output_0"))[0];

        int argmax = Helper.getArgMax(prediction);
        double confidence = softmax(prediction[argmax], Helper.convertFloatsToDoubles(prediction));
        return new Prediction(argmax, confidence);
    }

    public static Prediction doTLInference(float[] feature, Interpreter modelInterpreter) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("features", feature);

        float[][] outputs = new float[1][6];
        Map<String, Object> output = new HashMap<>();
        output.put("output", outputs);

        modelInterpreter.runSignature(inputs, output, "infer");

        float[][] prediction = (float[][]) output.get("output");

        int argmax = Helper.getArgMax(prediction[0]);
        Log.v("LIST", String.valueOf(argmax));
        double confidence = softmax(prediction[0][argmax], Helper.convertFloatsToDoubles(prediction[0]));
        return new Prediction(argmax, confidence);
    }
    private static double softmax(double input, double[] values) {
        double sum = Arrays.stream(values).map(Math::exp).sum();
        return Math.exp(input) / sum;
    }

}
