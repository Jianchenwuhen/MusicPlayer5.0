package com.example.musicplayer50;

import android.content.Context;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class LyricAdapter extends ArrayAdapter<LrcLine> {
    private int currentLine = -1;

    public LyricAdapter(Context context, List<LrcLine> lyrics) {
        super(context, android.R.layout.simple_list_item_1, lyrics);
    }

    public void setCurrentLine(int currentLine) {
        this.currentLine = currentLine;
        notifyDataSetChanged();
    }

    public void clearHighlight() {
        currentLine = -1;
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView textView;
        if (convertView instanceof TextView) {
            textView = (TextView) convertView;
        } else {
            textView = new TextView(getContext());
            textView.setGravity(Gravity.CENTER);
            textView.setPadding(20, 10, 20, 10);
        }

        LrcLine line = getItem(position);
        textView.setText(line == null ? "" : line.getText());

        if (position == currentLine) {
            textView.setTextColor(ContextCompat.getColor(getContext(), R.color.accent_primary));
            textView.setTypeface(Typeface.DEFAULT_BOLD);
            textView.setTextSize(18);
        } else {
            textView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_tertiary));
            textView.setTypeface(Typeface.DEFAULT);
            textView.setTextSize(15);
        }

        return textView;
    }
}
