package com.example.musicplayer50;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

public class PlaylistProvider extends ContentProvider {
    private static final int PLAYLIST = 1;
    private static final int PLAYLIST_ID = 2;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(PlaylistContract.AUTHORITY, "playlist", PLAYLIST);
        uriMatcher.addURI(PlaylistContract.AUTHORITY, "playlist/#", PLAYLIST_ID);
    }

    private TabledatabaseHelper dbHelper;

    @Override
    public boolean onCreate() {
        dbHelper = new TabledatabaseHelper(getContext(), "login.db", null, 1);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor;

        switch (uriMatcher.match(uri)) {
            case PLAYLIST:
                cursor = db.query(PlaylistContract.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case PLAYLIST_ID:
                String id = uri.getLastPathSegment();
                cursor = db.query(PlaylistContract.TABLE_NAME, projection, "id = ?", new String[]{id}, null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Unknown Uri: " + uri);
        }

        if (getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (uriMatcher.match(uri) != PLAYLIST) {
            throw new IllegalArgumentException("Unknown Uri: " + uri);
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long id = db.insert(PlaylistContract.TABLE_NAME, null, values);
        Uri resultUri = ContentUris.withAppendedId(PlaylistContract.CONTENT_URI, id);
        notifyChange(uri);
        return resultUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;

        switch (uriMatcher.match(uri)) {
            case PLAYLIST:
                count = db.delete(PlaylistContract.TABLE_NAME, selection, selectionArgs);
                break;
            case PLAYLIST_ID:
                String id = uri.getLastPathSegment();
                count = db.delete(PlaylistContract.TABLE_NAME, "id = ?", new String[]{id});
                break;
            default:
                throw new IllegalArgumentException("Unknown Uri: " + uri);
        }

        notifyChange(uri);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;

        switch (uriMatcher.match(uri)) {
            case PLAYLIST:
                count = db.update(PlaylistContract.TABLE_NAME, values, selection, selectionArgs);
                break;
            case PLAYLIST_ID:
                String id = uri.getLastPathSegment();
                count = db.update(PlaylistContract.TABLE_NAME, values, "id = ?", new String[]{id});
                break;
            default:
                throw new IllegalArgumentException("Unknown Uri: " + uri);
        }

        notifyChange(uri);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)) {
            case PLAYLIST:
                return "vnd.android.cursor.dir/vnd.com.example.musicplayer50.playlist";
            case PLAYLIST_ID:
                return "vnd.android.cursor.item/vnd.com.example.musicplayer50.playlist";
            default:
                throw new IllegalArgumentException("Unknown Uri: " + uri);
        }
    }

    private void notifyChange(Uri uri) {
        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
    }
}
