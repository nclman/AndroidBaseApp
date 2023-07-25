package com.example.notesrecorder2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import org.sqlite.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.stream.Stream;

public class DatabaseManager {

    static {
        System.loadLibrary("sqliteX");
    }

    private DatabaseHelper dbHelper;
    private Context context;
    private SQLiteDatabase database;

    public DatabaseManager(Context c) {
        context = c;
    }

    public DatabaseManager open() throws SQLException {
        dbHelper = new DatabaseHelper(context);
        database = dbHelper.getWritableDatabase();
        /*
        String QUERY_VERSION = "SELECT sqlite_version();";

        Cursor c = database.rawQuery(QUERY_VERSION, new String[] {});
        if (c.moveToFirst()) {
            Log.v("DATABASE", c.getString(0));
        }*/

        return this;
    }

    public void close() {
        dbHelper.close();
    }

    public void insert(String text_note, String audio_note) {
        ContentValues contentValue = new ContentValues();
        contentValue.put(DatabaseHelper.TEXT_NOTE, text_note);
        contentValue.put(DatabaseHelper.AUDIO_NOTE, audio_note);
        database.insert(DatabaseHelper.TABLE_NAME, null, contentValue);
    }

    public Cursor fetch() {
        String[] columns = new String[] { DatabaseHelper._ID, DatabaseHelper.TEXT_NOTE, DatabaseHelper.AUDIO_NOTE };
        Cursor cursor = database.query(DatabaseHelper.TABLE_NAME, columns, null, null, null, null, null);
        return cursor;
    }

    public int update(long _id, String text_note, String audio_note) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DatabaseHelper.TEXT_NOTE, text_note);
        contentValues.put(DatabaseHelper.AUDIO_NOTE, audio_note);
        int i = database.update(DatabaseHelper.TABLE_NAME, contentValues, DatabaseHelper._ID + " = " + _id, null);
        return i;
    }

    public void delete(long _id) {
        database.delete(DatabaseHelper.TABLE_NAME, DatabaseHelper._ID + "=" + _id, null);
    }
}
