package com.example.musicplayer50;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class Findmusic {
    public List<Music> getmusics(ContentResolver contentResolver) {
        List<Music> musics = new ArrayList<Music>();
        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.IS_MUSIC
        };
        Cursor cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        if (cursor == null) {
            return musics;
        }

        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                int isMusic = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC));
                String url = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                if (TextUtils.isEmpty(url)) {
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    url = contentUri.toString();
                }

                if (isMusic == 0 || duration < 60 * 1000 || TextUtils.isEmpty(url)) {
                    continue;
                }

                Music music = new Music();
                String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                music.setTitle(TextUtils.isEmpty(title) ? "未知歌曲" : title);
                music.setArtist(isUnknownArtist(artist) ? "未知歌手" : artist);
                music.setDuration(duration);
                music.setUrl(url);
                musics.add(music);
            }
        } finally {
            cursor.close();
        }
        return musics;
    }

    private boolean isUnknownArtist(String artist) {
        return TextUtils.isEmpty(artist) || "<unknown>".equalsIgnoreCase(artist.trim());
    }

    /*public void setListAdpter(Context context, List<Music> musics, ListView mMusicList) {
        List<HashMap<String, String>> mp3list = new ArrayList<HashMap<String, String>>();
        MusicAdapter mAdapter = new MusicAdapter(context, musics);
        mMusicList.setAdapter(mAdapter);
    }*/

}
