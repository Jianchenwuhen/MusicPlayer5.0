package com.example.musicplayer50;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by 惠中 on 2016/12/15.
 */
public class TabledatabaseHelper extends SQLiteOpenHelper {

    public static final String sql = "create table if not exists " + PlaylistContract.TABLE_NAME + "("
            + PlaylistContract.COLUMN_ID + " integer primary key autoincrement,"
            + PlaylistContract.COLUMN_TITLE + " text,"
            + PlaylistContract.COLUMN_ARTIST + " text,"
            + PlaylistContract.COLUMN_URL + " text)";

    public TabledatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version){
        super(context,name,factory,version);
    }

    @Override
    public void onCreate(SQLiteDatabase db){
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        db.execSQL("drop table if exists " + PlaylistContract.TABLE_NAME);
        onCreate(db);
    }
}
