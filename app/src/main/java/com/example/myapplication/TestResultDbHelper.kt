// In TestResultDatabaseHelper.kt
import CellInfoDatabaseHelper.Companion.COLUMN_LATITUDE
import CellInfoDatabaseHelper.Companion.COLUMN_LONGITUDE
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TestResultDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "test_result.db"
        private const val DATABASE_VERSION = 3
        internal const val TABLE_NAME = "test_result"

        // Existing columns
        internal const val COLUMN_ID = "id"
        internal const val COLUMN_LATITUDE = "latitude"
        internal const val COLUMN_LONGITUDE = "longitude"
        internal const val COLUMN_TIMESTAMP = "timestamp"

        // New columns for test Results
        internal const val COLUMN_PING = "ping"
        internal const val COLUMN_DNS = "dns"
        internal const val COLUMN_THROUGHPUT = "throughput"
        internal const val COLUMN_WEB = "web"
        internal const val COLUMN_SMS = "sms"
        internal const val COLUMN_SENT = "sent" // New column to track if data has been sent
    }

    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_TABLE_SQL = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_LATITUDE REAL,
                $COLUMN_LONGITUDE REAL,
                $COLUMN_TIMESTAMP TEXT,
                $COLUMN_PING INTEGER,
                $COLUMN_DNS INTEGER,
                $COLUMN_THROUGHPUT INTEGER,
                $COLUMN_WEB INTEGER,
                $COLUMN_SMS INTEGER,
                $COLUMN_SENT INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(CREATE_TABLE_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_SENT INTEGER DEFAULT 0")
        }
    }

    /**
     * Inserts new test result data into the database.
     */
    fun insertTestResult(
        timestamp: String,
        latitude: Double?,
        longitude: Double?,
        testResult: TestResult
    ) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_LATITUDE, latitude)
            put(COLUMN_LONGITUDE, longitude)
            put(COLUMN_PING, testResult.ping)
            put(COLUMN_DNS, testResult.dns)
            put(COLUMN_THROUGHPUT, testResult.throughput)
            put(COLUMN_WEB, testResult.web)
            put(COLUMN_SMS, testResult.sms)
            put(COLUMN_SENT, 0) // Mark as not sent
        }
        db.insert(TABLE_NAME, null, values)
    }

    /**
     * Retrieves all unsent test result records.
     */
    fun getUnsentRecords(): Pair<List<JSONObject>, List<Int>> {
        val db = readableDatabase
        val unsentRecords = mutableListOf<JSONObject>()
        val idsToUpdate = mutableListOf<Int>()
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COLUMN_SENT = 0", null)
        cursor.use {
            while (it.moveToNext()) {
                val recordId = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID))
                idsToUpdate.add(recordId)

                val timestamp = it.getString(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val jsonObject = JSONObject().apply {
                    put("time", formatTimestamp(timestamp))
                    put("latitude", it.getLong(it.getColumnIndexOrThrow(COLUMN_LATITUDE)))
                    put("longitude", it.getLong(it.getColumnIndexOrThrow(COLUMN_LONGITUDE)))
                    put("ping", it.getInt(it.getColumnIndexOrThrow(COLUMN_PING)))
                    put("dns", it.getInt(it.getColumnIndexOrThrow(COLUMN_DNS)))
                    put("throughput", it.getInt(it.getColumnIndexOrThrow(COLUMN_THROUGHPUT)))
                    put("web", it.getInt(it.getColumnIndexOrThrow(COLUMN_WEB)))
                    put("sms", it.getInt(it.getColumnIndexOrThrow(COLUMN_SMS)))
                }
                unsentRecords.add(jsonObject)
            }
        }
        return Pair(unsentRecords, idsToUpdate)
    }

    /**
     * Marks a list of records as sent.
     */
    fun markRecordsAsSent(ids: List<Int>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        val idList = ids.joinToString(",")
        db.execSQL("UPDATE $TABLE_NAME SET $COLUMN_SENT = 1 WHERE $COLUMN_ID IN ($idList)")
    }

    private fun formatTimestamp(timestamp: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        return outputFormat.format(inputFormat.parse(timestamp) ?: Date())
    }
}