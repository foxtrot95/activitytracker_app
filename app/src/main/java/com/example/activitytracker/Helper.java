package com.example.activitytracker;

import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

public class Helper extends AppCompatActivity {

    public static double[] convertFloatsToDoubles(float[] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }

    public static double[] createDoubleArray(ArrayList<Float> list) {
        double[] array = new double[list.size()];
        int index = 0;
        for(final Float value: list)
            array[index++] = value;
        return array;
    }

    public static float[] createFloatArray(ArrayList<Float> list) {
        float[] array = new float[list.size()];
        int index = 0;
        for(final Float value: list)
            array[index++] = value;
        return array;
    }

    public static ArrayList<Float> createFloatList(float[] array) {
        ArrayList<Float> list = new ArrayList<>();
        for(float value: array)
            list.add(value);
        return list;
    }

    public static ArrayList<Integer> createIntegerList(int[] array) {
        ArrayList<Integer> list = new ArrayList<>();
        for(int value: array)
            list.add(value);
        return list;
    }

    public static StringBuilder readFile(File file) {
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return text;
    }

    public static ArrayList<float[]> getFeaturesFromJSON(String text) throws JSONException {
        JSONObject jObject = new JSONObject(text);
        JSONArray jArray = jObject.getJSONArray("x_test");
        ArrayList<float[]> features = new ArrayList<>();
        for( int i = 0; i < jArray.length(); i++) {
            float[] feature = new float[15];
            try {
                JSONArray jArrayFeature = jArray.getJSONArray(i);

                for(int j = 0; j < jArrayFeature.length(); j++) {
                    feature[j] = Float.valueOf(jArrayFeature.get(j).toString());
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            features.add(feature);
        }
        return features;
    }

    public static ArrayList<Integer> getLabels(String text) throws JSONException {
        JSONObject jObject = new JSONObject(text);
        JSONArray jLabels = jObject.getJSONArray("y_test");
        ArrayList<Integer> labels = new ArrayList<>();

        for( int i = 0; i < jLabels.length(); i++) {
            labels.add((Integer) jLabels.get(i));
        }
        return labels;
    }

    public static float[] getCategoricalLabel(Integer label) {
        float[] categoricalLabel = new float[]{0,0,0,0,0,0};
        categoricalLabel[label] = 1;
        return categoricalLabel;
    }

    public static ArrayList<float[]> getCategoricalLabelList(ArrayList<Integer> labels) {
        ArrayList<float[]> categoricalLabels = new ArrayList<>();
        for(final Integer label: labels){
            float[] categoricalLabel = getCategoricalLabel(label);
            categoricalLabels.add(categoricalLabel);
        }
        return categoricalLabels;
    }

    public static int getArgMax(float[] array) {
        int argmax = 0;
        for(int i = 0; i < array.length; i++) {
            if(array[i] > array[argmax])
                argmax = i;
        }
        return argmax;
    }

    public static void writeJsonFile(File path, String fileName,
                                     int selectedActivity, ArrayList<float[]> accelerationData,
                                     ArrayList<Long> timeStamps) throws IOException {

        File file = new File(path, fileName);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        FileWriter fileWriter = new FileWriter(file);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

        Data data = new Data(selectedActivity, accelerationData, timeStamps);
        String json = gson.toJson(data);

        // writer bugfix
        String[] splitJson = json.split("\n");
        for (String s : splitJson) {
            bufferedWriter.write(s);
            bufferedWriter.newLine();
        }

        bufferedWriter.flush();
        bufferedWriter.close();
    }

    public static void writeFile(ArrayList<Float> list, String path) throws IOException {
        BufferedWriter b = new BufferedWriter(new FileWriter(path));
        for(Float f: list) {
            b.write(String.valueOf(f));
            b.newLine();
        }
        b.close();
    }

    public static boolean areElementsEqual(ArrayList<String> list, int n) {
        boolean isEqual = true;
        if(n < 2)
            return isEqual;
        String lastElement = list.get(list.size() - 1);
        for(int i = list.size() - 2; i >= list.size() - n; i--) {
            String currentElement = list.get(i);
            if(!Objects.equals(lastElement, currentElement)) {
                isEqual = false;
                break;
            }
            lastElement = currentElement;
        }
        return isEqual;
    }

    public static ArrayList<Integer> getShuffledIndexList(int size) {
        int[] indices = IntStream.range(0, size).toArray();
        ArrayList<Integer> indexList = createIntegerList(indices);
        Collections.shuffle(indexList, new Random(42));
        return indexList;
    }

    public static float[] computeFeatures(ArrayList<Float> accX,
                                    ArrayList<Float> accY,
                                    ArrayList<Float> accZ) {
        // min
        float minX = Collections.min(accX);
        float minY = Collections.min(accY);
        float minZ = Collections.min(accZ);

        // max
        float maxX = Collections.max(accX);
        float maxY = Collections.max(accY);
        float maxZ = Collections.max(accZ);

        // average
        float meanX = accX.stream().reduce(0f, Float::sum)
                / accX.size();
        float meanY = accY.stream().reduce(0f, Float::sum)
                / accY.size();
        float meanZ = accZ.stream().reduce(0f, Float::sum)
                / accZ.size();

        // variance
        float varX = accX.stream()
                .map(a -> (a-meanX) * (a-meanX))
                .reduce(0f, Float::sum) / (accX.size() -1);

        float varY = accY.stream()
                .map(a -> (a-meanY) * (a-meanY))
                .reduce(0f, Float::sum) / (accY.size() -1);

        float varZ = accZ.stream()
                .map(a -> (a-meanZ) * (a-meanZ))
                .reduce(0f, Float::sum) / (accZ.size() -1);

        // correlation
        float corrXY = (float) new PearsonsCorrelation()
                .correlation(Helper.createDoubleArray(accX),
                        Helper.createDoubleArray(accY));

        float corrXZ = (float) new PearsonsCorrelation()
                .correlation(Helper.createDoubleArray(accX),
                        Helper.createDoubleArray(accZ));

        float corrYZ = (float) new PearsonsCorrelation()
                .correlation(Helper.createDoubleArray(accY),
                        Helper.createDoubleArray(accZ));

        float[] feature = new float[]{
                maxX, maxY, maxZ,
                minX, minY, minZ,
                meanX, meanY, meanZ,
                varX, varY, varZ,
                corrXY, corrXZ, corrYZ};
        return feature;
    }
}

