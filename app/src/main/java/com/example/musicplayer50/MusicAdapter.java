package com.example.musicplayer50;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class MusicAdapter extends ArrayAdapter<Music> {

    public MusicAdapter(Context context, int textViewResourceId, List<Music> musics) {
        super(context, textViewResourceId, musics);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.musicitem, parent, false);
            holder = new ViewHolder();
            holder.musicImage = (ImageView) convertView.findViewById(R.id.imageView);
            holder.songName = (TextView) convertView.findViewById(R.id.songname);
            holder.singer = (TextView) convertView.findViewById(R.id.singer);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Music music = getItem(position);
        holder.musicImage.setImageResource(R.drawable.black);
        if (music != null) {
            holder.songName.setText(music.getTitle());
            holder.singer.setText(music.getArtist());
        } else {
            holder.songName.setText("");
            holder.singer.setText("");
        }
        return convertView;
    }

    private static class ViewHolder {
        ImageView musicImage;
        TextView songName;
        TextView singer;
    }
}
