package com.example.musicplayer50;

import android.net.Uri;

public final class PlaylistContract {
    public static final String AUTHORITY = "com.example.musicplayer50.playlistprovider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/playlist");

    public static final String TABLE_NAME = "login";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_ARTIST = "artist";
    public static final String COLUMN_URL = "url";

    private PlaylistContract() {
    }
}
