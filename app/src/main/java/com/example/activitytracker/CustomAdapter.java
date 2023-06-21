package com.example.activitytracker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class CustomAdapter extends BaseAdapter {
    Context context;
    ArrayList<String> activities;
    ArrayList<Integer> icons;
    ArrayList<String> timeStamps;
    ArrayList<String> confidences;
    LayoutInflater inflater;

    public CustomAdapter(Context applicationContext)  {
        this.context = applicationContext;
        this.activities = new ArrayList<>();
        this.icons = new ArrayList<>();
        this.timeStamps = new ArrayList<>();
        this.confidences = new ArrayList<>();

        inflater = (LayoutInflater.from(applicationContext));
    }

    @Override
    public int getCount() {
        return activities.size();
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        view = inflater.inflate(R.layout.listview, null);
        TextView description = (TextView) view.findViewById(R.id.text_description);
        TextView time = (TextView) view.findViewById(R.id.text_time);
        TextView confidence = (TextView) view.findViewById(R.id.text_confidence);
        ImageView icon = (ImageView) view.findViewById(R.id.icon);
        description.setText(activities.get(i));
        icon.setImageResource(icons.get(i));
        time.setText(timeStamps.get(i));
        confidence.setText(confidences.get(i));
        return view;
    }

    public void updateItems(ArrayList<String> activities, ArrayList<Integer> icons,
                            ArrayList<String> timeStamps, ArrayList<String> confidences) {
        this.activities.clear();
        this.activities.addAll(activities);
        this.icons.clear();
        this.icons.addAll(icons);
        this.timeStamps.clear();
        this.timeStamps.addAll(timeStamps);
        this.confidences.clear();
        this.confidences.addAll(confidences);
        this.notifyDataSetChanged();
    }

}
