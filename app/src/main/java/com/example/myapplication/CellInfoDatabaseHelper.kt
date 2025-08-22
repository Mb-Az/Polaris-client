// In LocationDatabaseHelper.kt
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CellInfoDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "location.db"
        private const val DATABASE_VERSION = 3 // Increment the version!
        internal const val TABLE_NAME = "location_data"

        // Existing columns
        internal const val COLUMN_ID = "id"
        internal const val COLUMN_LATITUDE = "latitude"
        internal const val COLUMN_LONGITUDE = "longitude"
        internal const val COLUMN_TIMESTAMP = "timestamp"

        // New columns for cellular info
        internal const val COLUMN_SIGNAL_LEVEL = "signal_level"
        internal const val COLUMN_CARRIER = "carrier"
        internal const val COLUMN_TECHNOLOGY = "technology"
        internal const val COLUMN_TAC = "tac"
        internal const val COLUMN_PLMN_ID = "plmn_id"
        internal const val COLUMN_ARFCN = "arfcn"
        internal const val COLUMN_RSRQ = "rsrq"
        internal const val COLUMN_RSRP = "rsrp"
        internal const val COLUMN_RSCP = "rscp"
        internal const val COLUMN_ECNO = "ec_no"
        internal const val COLUMN_RXLEV = "rx_lev"
        internal const val COLUMN_SENT = "sent" // New column to track if data has been sent
    }

    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_TABLE_SQL = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_LATITUDE REAL,
                $COLUMN_LONGITUDE REAL,
                $COLUMN_TIMESTAMP TEXT,
                $COLUMN_SIGNAL_LEVEL INTEGER,
                $COLUMN_CARRIER TEXT,
                $COLUMN_TECHNOLOGY TEXT,
                $COLUMN_TAC INTEGER,
                $COLUMN_PLMN_ID TEXT,
                $COLUMN_ARFCN INTEGER,
                $COLUMN_RSRQ INTEGER,
                $COLUMN_RSRP INTEGER,
                $COLUMN_RSCP INTEGER,
                $COLUMN_ECNO INTEGER,
                $COLUMN_RXLEV INTEGER,
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
     * Inserts new location and cellular data into the database.
     */
    fun insertLocation(
        latitude: Double,
        longitude: Double,
        timestamp: String,
        cellInfo: CellInfo?
    ) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LATITUDE, latitude)
            put(COLUMN_LONGITUDE, longitude)
            put(COLUMN_TIMESTAMP, timestamp)

            // Add cell info if available
            cellInfo?.let { info ->
                put(COLUMN_SIGNAL_LEVEL, info.signalLevel)
                put(COLUMN_CARRIER, info.carrier)
                put(COLUMN_TECHNOLOGY, info.technology)
                put(COLUMN_TAC, info.tac)
                put(COLUMN_PLMN_ID, info.plmnId)
                put(COLUMN_ARFCN, info.arfcn)
                put(COLUMN_RSRQ, info.rsrq)
                put(COLUMN_RSRP, info.rsrp)
                put(COLUMN_RSCP, info.rscp)
                put(COLUMN_ECNO, info.ecNo)
                put(COLUMN_RXLEV, info.rxLev)
            }
            put(COLUMN_SENT, 0) // Mark as not sent
        }
        db.insert(TABLE_NAME, null, values)
    }

    /**
     * Retrieves all unsent cell measurement records.
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

                val latitude = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LATITUDE))
                val longitude = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                val timestamp = it.getString(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))

                val jsonObject = JSONObject().apply {
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("time", formatTimestamp(timestamp))
                    put("signal_level", it.getInt(it.getColumnIndexOrThrow(COLUMN_SIGNAL_LEVEL)))
                    put("carrier", it.getString(it.getColumnIndexOrThrow(COLUMN_CARRIER)))
                    put("technology", it.getString(it.getColumnIndexOrThrow(COLUMN_TECHNOLOGY)))
                    put("tac", it.getInt(it.getColumnIndexOrThrow(COLUMN_TAC)))
                    put("plmn_id", it.getString(it.getColumnIndexOrThrow(COLUMN_PLMN_ID)))
                    put("arfcn", it.getInt(it.getColumnIndexOrThrow(COLUMN_ARFCN)))
                    put("rsrq", it.getInt(it.getColumnIndexOrThrow(COLUMN_RSRQ)))
                    put("rsrp", it.getInt(it.getColumnIndexOrThrow(COLUMN_RSRP)))
                    put("rscp", it.getInt(it.getColumnIndexOrThrow(COLUMN_RSCP)))
                    put("ec_no", it.getInt(it.getColumnIndexOrThrow(COLUMN_ECNO)))
                    put("rx_lev", it.getInt(it.getColumnIndexOrThrow(COLUMN_RXLEV)))
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