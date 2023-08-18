package com.example.notesrecorder2;

import android.util.Log;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import org.sqlite.database.sqlite.SQLiteDatabase;

public class DatabaseManager {
    private static final String TAG = "DataBaseManager";

    static {
        System.loadLibrary("sqliteX");
    }

    private static volatile DatabaseManager instance;
    private DatabaseHelper dbHelper;
    private final Context context;
    private SQLiteDatabase database;
    private CloudDataBaseManager cloudDb;

    public DatabaseManager(Context c) {
        this.context = c;
        this.cloudDb = new CloudDataBaseManager(c);
        this.instance = this;
    }

    public static DatabaseManager getInstance(Context c) {
        if (instance == null) {
            instance = new DatabaseManager(c);
        }
        return instance;
    }

    public void open() throws SQLException {
        this.dbHelper = new DatabaseHelper(context);
        this.database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public long insertDbOnly(String text_note, String audio_note, String doc_id) {
        ContentValues contentValue = new ContentValues();
        contentValue.put(DatabaseHelper.TEXT_NOTE, text_note);
        contentValue.put(DatabaseHelper.AUDIO_NOTE, audio_note);
        contentValue.put(DatabaseHelper.DOC_ID, doc_id);
        long id = this.database.insert(DatabaseHelper.TABLE_NAME, null, contentValue);
        return id;
    }

    public void insert(String text_note, String audio_note) {
        this.cloudDb.insert(text_note, audio_note);
    }

    public Cursor fetch() {
        String[] columns = new String[] { DatabaseHelper._ID, DatabaseHelper.TEXT_NOTE, DatabaseHelper.AUDIO_NOTE };
        return this.database.query(DatabaseHelper.TABLE_NAME, columns, null, null, null, null, null);
    }

    public int update(long _id, String text_note, String audio_note) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DatabaseHelper.TEXT_NOTE, text_note);
        contentValues.put(DatabaseHelper.AUDIO_NOTE, audio_note);
        return this.database.update(DatabaseHelper.TABLE_NAME, contentValues, DatabaseHelper._ID + " = " + _id, null);
    }

    public void delete(long _id) {
        String columns[] = new String[] { DatabaseHelper._ID, DatabaseHelper.AUDIO_NOTE, DatabaseHelper.DOC_ID };
        Cursor cursor = this.database.query(DatabaseHelper.TABLE_NAME, columns, DatabaseHelper._ID + " = " + _id, null, null, null, null);
        cursor.moveToFirst();
        //Log.d(TAG, "UID " + cursor.getString(1));
        // remove audio file from cloud store
        this.cloudDb.deleteFile(cursor.getString(1));
        // remove entry from cloud db
        this.cloudDb.delete(cursor.getString(2));
        // remove entry from local db
        this.database.delete(DatabaseHelper.TABLE_NAME, DatabaseHelper._ID + "=" + _id, null);
    }

    public void sync() {
        // Check that local and cloud databases are matching. If not, sync them
        Cursor cursor = fetch();

        // We start with a simple way. If SQL db is empty, attempt to propagate from cloud
        if (cursor.getCount() == 0) {
            Log.i("TAG", "Fetching from cloud...");
            cloudDb.sync();
        }
    }
}
