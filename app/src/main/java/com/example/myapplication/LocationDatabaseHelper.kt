package com.example.myapplication

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class LocationDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "solaris.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS location_data (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "latitude REAL, " +
                    "longitude REAL, " +
                    "timestamp TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
//        db.execSQL("DROP TABLE IF EXISTS location_data")
//        onCreate(db)
    }

    fun insertLocation(latitude: Double, longitude: Double, timestamp: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("timestamp", timestamp)
        }
        db.insert("location_data", null, values)
    }
}
