package com.example.musicplayer50;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * 歌词列表适配器，支持高亮当前播放行
 */
public class LyricAdapter extends ArrayAdapter<LrcLine> {

    private int currentLine = -1;
    private int normalColor = Color.parseColor("#88FFFFFF");   // 半透明白
    private int highlightColor = Color.parseColor("#FF6EE8FF"); // 亮青色
    private float normalSize = 15f;
    private float highlightSize = 18f;

    public LyricAdapter(Context context, List<LrcLine> lines) {
        super(context, R.layout.lyric_item, lines);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.lyric_item, parent, false);
            holder = new ViewHolder();
            holder.textView = (TextView) convertView.findViewById(R.id.lyricLineText);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        LrcLine line = getItem(position);
        holder.textView.setText(line.getText());

        if (position == currentLine) {
            holder.textView.setTextColor(highlightColor);
            holder.textView.setTextSize(highlightSize);
            holder.textView.setAlpha(1.0f);
        } else {
            holder.textView.setTextColor(normalColor);
            holder.textView.setTextSize(normalSize);
            holder.textView.setAlpha(0.7f);
        }

        return convertView;
    }

    /**
     * 设置当前高亮行，-1 表示无高亮
     */
    public void setCurrentLine(int position) {
        if (position != currentLine) {
            currentLine = position;
            notifyDataSetChanged();
        }
    }

    /**
     * 清除高亮
     */
    public void clearHighlight() {
        if (currentLine != -1) {
            currentLine = -1;
            notifyDataSetChanged();
        }
    }

    static class ViewHolder {
        TextView textView;
    }
}
