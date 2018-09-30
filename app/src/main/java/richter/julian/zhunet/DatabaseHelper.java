package richter.julian.zhunet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.Calendar;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";

    private static final String DATABASE_NAME = "zhunet.db";

    private static final String CYCLE_TABLE = "cycles";
    private static final String CYCLE_COL0  = "id";
    private static final String CYCLE_COL1  = "cycle_number";
    private static final String CYCLE_COL2  = "date_of_creation";

    private static final String RECORD_TABLE = "records";
    private static final String RECORD_COL0  = "id";
    private static final String RECORD_COL1  = "day";
    private static final String RECORD_COL2  = "temperature";
    private static final String RECORD_COL3  = "is_ignored";    // False = 0, True = 1
    private static final String RECORD_COL4  = "cycle_id";

    public DatabaseHelper(Context context) {

        super(context, DATABASE_NAME, null, 1);
    }

// ###########################
// ####  Lifecycle:       ####
// ###########################

    /**
     * Creates the Cycles and the Records Tables.
     *
     * @param sqLiteDatabase
     */
    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        String query;

        query = "CREATE TABLE " + CYCLE_TABLE                            +
                "("             +
                CYCLE_COL0      + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                CYCLE_COL1      + " INTEGER,  "                          +
                CYCLE_COL2      + " INTEGER  "                           +
                ")";
        sqLiteDatabase.execSQL(query);

        query = "CREATE TABLE " + RECORD_TABLE                           +
                "("             +
                RECORD_COL0     + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                RECORD_COL1     + " REAL, "                              +
                RECORD_COL2     + " REAL, "                              +
                RECORD_COL3     + " INTEGER, "                           +
                RECORD_COL4     + " INTEGER, "                           +
                "FOREIGN KEY("  +   RECORD_COL4                          + ") " +
                "REFERENCES "   +   CYCLE_TABLE + "(" + CYCLE_COL0       + ") " +
                "ON DELETE CASCADE"                                      +
                ")";
        sqLiteDatabase.execSQL(query);
    }

    /**
     * Drops the Cycles and Records Tables and calls onCreate.
     *
     * @param sqLiteDatabase
     * @param i
     * @param i1
     */
    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

        String query;

        query = "DROP TABLE IF EXISTS " + CYCLE_TABLE;
        sqLiteDatabase.execSQL(query);

        query = "DROP TABLE IF EXISTS " + RECORD_TABLE;
        sqLiteDatabase.execSQL(query);

        onCreate(sqLiteDatabase);
    }

    /**
     * Sqlite disable foreign key constrain by default, so you need to enable it.
     * onOpen gets called every time the Database it accessed.
     *
     * @param db
     */
    @Override
    public void onOpen(SQLiteDatabase db){
        super.onOpen(db);
        db.execSQL("PRAGMA foreign_keys=1");
    }

// ###########################
// ####  Cycles Table:    ####
// ###########################

    /**
     * Creates a new Cycle with given Number and returns the ID.
     *
     * @param cycle_number          Number to be added.
     * @return id                   Valid ID on success, -1 on fail.
     */
    public long addCycle(int cycle_number) {
        SQLiteDatabase db;
        ContentValues content_values;
        Calendar current_time;
        long id;

        db = getWritableDatabase();
        content_values = new ContentValues();
        content_values.put(CYCLE_COL1, cycle_number);
        current_time = Calendar.getInstance();
        content_values.put(CYCLE_COL2, current_time.getTimeInMillis());

        id = db.insert(CYCLE_TABLE, null, content_values);

        return id;
    }

    /**
     * Deletes a Cycle with given number from the table.
     *
     * @param cycle_number          Number to be deleted.
     * @return deleted_rows         Number of rows deleted.
     */
    public long deleteCycle(int cycle_number) {
        SQLiteDatabase db;
        String where_clause;
        long deleted_rows;

        db = getWritableDatabase();
        where_clause = CYCLE_COL1 + " = " + cycle_number;
        deleted_rows = db.delete(CYCLE_TABLE, where_clause, null);

        return deleted_rows;
    }

    /**
     * Deletes all Cycles from the Table.
     *
     * @return deletec_rows         Number of rows deleted.
     */
    public long deleteAllCycles() {
        SQLiteDatabase db;
        String where_clause;
        long deletec_rows;

        db = getWritableDatabase();
        where_clause = "1"; // Returns number of rows deleted.
        deletec_rows = db.delete(CYCLE_TABLE, where_clause, null);

        return deletec_rows;
    }

    public Cursor getAllCycles() {
        SQLiteDatabase db;
        String query;
        Cursor data;

        db = getWritableDatabase();
        query = "SELECT * "   +
                "FROM "       + CYCLE_TABLE;
        data = db.rawQuery(query, null);

        return data;
    }

    public Cursor getCycle(int cycle_number) {
        SQLiteDatabase db;
        String query;
        Cursor data;

        db = getWritableDatabase();
        query = "SELECT * "   +
                "FROM "       + CYCLE_TABLE + " " +
                "WHERE "      + CYCLE_COL1  + "=" + cycle_number;
        data = db.rawQuery(query, null);

        return data; // TODO: Error if no match was found.
    }

    /**
     * Returns the Count of all Cycles in the table.
     *
     * @return cycle_count          Number of rows counted.
     */
    public int getCycleCount() {
        SQLiteDatabase db;
        String query;
        Cursor data;
        int cycle_count;

        db = getWritableDatabase();
        query = "SELECT COUNT(*) " +
                "FROM "            + CYCLE_TABLE;
        data = db.rawQuery(query, null);

        // Aggregation should always return a valid Cursor.
        if ((data != null) && (data.getCount() > 0)) {
            data.moveToFirst();
            cycle_count = data.getInt(0);
            data.close();
        }
        else {
            cycle_count = 0;
        }

        return cycle_count;
    }

    /**
     * Returns the highest Cycle Number in the table.
     *
     * @return highest_cycle        Number of highest Cycle.
     */
    public int getHighestCycle() {
        SQLiteDatabase db;
        String query;
        Cursor data;
        int highest_cycle;

        db = getWritableDatabase();
        query = "SELECT MAX(" + CYCLE_COL1   + ") " +
                "FROM "       + CYCLE_TABLE;
        data = db.rawQuery(query, null);

        // Aggregation should always return a valid Cursor.
        if ((data != null) && (data.getCount() > 0)) {
            data.moveToFirst();
            highest_cycle = data.getInt(0);
            data.close();
        }
        else {
            highest_cycle = 0;
        }

        return highest_cycle;
    }

    /**
     * Returns the ID of a Cycle with given cycle_number.
     * If not Cycle with that number exists, a new Cycle is added
     * and that ID is returned.
     *
     * @param cycle_number          Number to be searched.
     * @return id                   ID of Cycle.
     */
    public long getCycleId(int cycle_number) {
        SQLiteDatabase db;
        String query;
        Cursor data;
        long id;

        db = getWritableDatabase();
        query = "SELECT " + CYCLE_COL0  + " " +
                "FROM "   + CYCLE_TABLE + " " +
                "WHERE "  + CYCLE_COL1  + "=" + cycle_number;
        data = db.rawQuery(query, null);

        if ((data != null) && (data.getCount() > 0)) {
            data.moveToFirst();
            id = data.getLong(0);
            data.close();
        }
        // No Cycle with given number found.
        else {
            id = addCycle(cycle_number);
        }

        return id;
    }

    /**
     * Returns the date_of_creation of a Cycle with given number in Milliseconds.
     *
     * @param cycle_number          Number of the Cycle.
     * @return date                 Date of Cycle Creation.
     */
    public long getCycleDate(int cycle_number) {
        SQLiteDatabase db;
        String query;
        Cursor data;
        long date;

        db = getWritableDatabase();
        query = "SELECT " + CYCLE_COL2  + " " +
                "FROM "   + CYCLE_TABLE + " " +
                "WHERE "  + CYCLE_COL0  + "=" + getCycleId(cycle_number);
        data = db.rawQuery(query, null);

        if ((data != null) && (data.getCount() > 0)) {
            data.moveToFirst();
            date = data.getLong(0);
            data.close();
        }
        // No Cycle with given number found.
        else {
            date = 0;
        }

        return date;
    }

    /**
     * Updates a Cycle Number from a given old_cycle_number to a new new_cycle_number.
     *
     * @param old_cycle_number      Old Number to be changed.
     * @param new_cycle_number      New Number to be changed to.
     * @return changed_rows         Number of rows affected.
     */
    public long updateCycleNumber(int old_cycle_number, int new_cycle_number) {
        SQLiteDatabase db;
        ContentValues content_values;
        String where_clause;
        long changed_rows;

        db = getWritableDatabase();
        content_values = new ContentValues();
        content_values.put(CYCLE_COL1, new_cycle_number);
        where_clause = CYCLE_COL0 + " = " + getCycleId(old_cycle_number);
        changed_rows = db.update(CYCLE_TABLE, content_values, where_clause, null);

        return changed_rows;
    }

    /**
     * Updates the date_of_creation of a Cycle with given number in Milliseconds.
     *
     * @param cycle_number              Number to be updated.
     * @param date_of_creation          New date to be changed to.
     * @return changed_rows             Number of rows affected.
     */
    public long updateCycleDate(int cycle_number, long date_of_creation) {
        SQLiteDatabase db;
        ContentValues content_values;
        String where_clause;
        long changed_rows;

        db = getWritableDatabase();
        content_values = new ContentValues();
        content_values.put(CYCLE_COL2, date_of_creation);
        where_clause = CYCLE_COL0 + " = " + getCycleId(cycle_number);
        changed_rows = db.update(CYCLE_TABLE, content_values, where_clause, null);

        return changed_rows;
    }

// ###########################
// ####  Records Table:   ####
// ###########################

    /**
     * Adds a Record with given day, temperature and cycle_number to the table.
     *
     * @param day                       Day of the new Record.
     * @param temperature               Temperature of the new Record.
     * @param cycle_number              Cycle Number containing the new Record.
     * @return id                       ID of the new created Record.
     */
    public long addRecord(double day, double temperature, int cycle_number) {
        SQLiteDatabase db;
        ContentValues content_values;
        long id;

        db = getWritableDatabase();
        content_values = new ContentValues();
        content_values.put(RECORD_COL1, day);
        content_values.put(RECORD_COL2, temperature);
        content_values.put(RECORD_COL3, 0);
        content_values.put(RECORD_COL4, getCycleId(cycle_number));

        id = db.insert(RECORD_TABLE, null, content_values);

        return id;
    }

    /**
     * Deletes a Record with given day and cycle_number from the table.
     *
     * @param day                       Day to be deleted.
     * @param cycle_number              Cycle from which Record should be removed.
     * @return rows_deleted             Number of rows deleted.
     */
    public long deleteRecord(double day, int cycle_number) {
        SQLiteDatabase db;
        String where_clause;
        long rows_deleted;

        db = getWritableDatabase();
        where_clause = RECORD_COL1 + " = " + day + " AND " +
                RECORD_COL4 + " = " + getCycleId(cycle_number);
        rows_deleted = db.delete(RECORD_TABLE, where_clause, null);

        return rows_deleted;
    }

    public long deleteCycleRecords(int cycle_number) {
        SQLiteDatabase db;
        String where_clause;
        long rows_deleted;

        db = getWritableDatabase();
        where_clause = RECORD_COL4 + " = " + getCycleId(cycle_number);
        rows_deleted = db.delete(RECORD_TABLE, where_clause, null);

        return rows_deleted;
    }

    /**
     * Deletes all Records from the table.
     *
     * @return rows_deleted             Number of rows deleted.
     */
    public long deleteAllRecords() {
        SQLiteDatabase db;
        String where_clause;
        long rows_deleted;

        db = getWritableDatabase();
        where_clause = "1"; // Returns number of rows deleted.
        rows_deleted = db.delete(RECORD_TABLE, where_clause, null);

        return rows_deleted;
    }

    /**
     * Counts all Records in a given Cycle Number.
     *
     * @param cycle_number              Cycle Number for Records to be counted.
     * @return records_count            Number of Records counted.
     */
    public int getRecordCount(int cycle_number) {
        SQLiteDatabase db;
        String query;
        Cursor data;
        int records_count;

        db = getWritableDatabase();
        query = "SELECT COUNT(*) " +
                "FROM "            + RECORD_TABLE + " " +
                "WHERE "           + RECORD_COL4  + "=" + getCycleId(cycle_number);
        data = db.rawQuery(query, null);

        // Aggregation should always return a valid Cursor.
        if ((data != null) && (data.getCount() > 0)) {
            data.moveToFirst();
            records_count = data.getInt(0);
            data.close();
        }
        else {
            records_count = 0;
        }

        return records_count;
    }

    /**
     * Counts all Records in the table.
     *
     * @return records_count            Number of Records counted.
     */
    public int getAllRecordCount() {
        SQLiteDatabase db;
        String query;
        Cursor data;
        int records_count;

        db = getWritableDatabase();
        query = "SELECT COUNT(*) " +
                "FROM "            + RECORD_TABLE;
        data = db.rawQuery(query, null);

        // Aggregation should always return a valid Cursor.
        if ((data != null) && (data.getCount() > 0)) {
            data.moveToFirst();
            records_count = data.getInt(0);
            data.close();
        }
        else {
            records_count = 0;
        }

        return records_count;
    }

    public Cursor getRecord(double day, int cycle_number) {
        SQLiteDatabase db;
        String query;
        Cursor data;

        db = getWritableDatabase();
        query = "SELECT * "   +
                "FROM "       + RECORD_TABLE + " " +
                "WHERE "      + RECORD_COL1  + "=" + day + " AND " +
                RECORD_COL4  + "=" + getCycleId(cycle_number);
        data = db.rawQuery(query, null);

        return data;
    }

    public Cursor getRecords(int cycle_number) {
        SQLiteDatabase db;
        String query;
        Cursor data;

        db = getWritableDatabase();
        query = "SELECT * "   +
                "FROM "       + RECORD_TABLE + " " +
                "WHERE "      + RECORD_COL4  + "=" + getCycleId(cycle_number);
        data = db.rawQuery(query, null);

        return data;
    }

    /**
     * Returns the Temperature of a Record with given day and cycle_number
     *
     * @param day                       Day of the Record.
     * @param cycle_number              Cycle containing the record.
     * @return temperature              Temperature of the Record.
     */
    public double getRecordTemperature(double day, int cycle_number) {
        SQLiteDatabase db;
        String query;
        Cursor data;
        double temperature;

        db = getWritableDatabase();
        query = "SELECT " + RECORD_COL2  + " " +
                "FROM "   + RECORD_TABLE + " " +
                "WHERE "  + RECORD_COL1  + "=" + day + " AND " +
                            RECORD_COL4  + "=" + getCycleId(cycle_number);
        data = db.rawQuery(query, null);

        if ((data != null) && (data.getCount() > 0)) {
            data.moveToFirst();
            temperature = data.getDouble(0);
            data.close();
        }
        // No Record found.
        else {
            temperature = -1;
        }

        return temperature;
    }

    /**
     * Returns the is_ignored of a Record with given day and cycle_number
     *
     * @param day                       Day of the Record.
     * @param cycle_number              Cycle containing the record.
     * @return ignored                  Ignored value of the Record.
     */
    public double getRecordIgnored(double day, int cycle_number) {
        SQLiteDatabase db;
        String query;
        Cursor data;
        int ignored;

        db = getWritableDatabase();
        query = "SELECT " + RECORD_COL3  + " " +
                "FROM "   + RECORD_TABLE + " " +
                "WHERE "  + RECORD_COL1  + "=" + day + " AND " +
                            RECORD_COL4  + "=" + getCycleId(cycle_number);
        data = db.rawQuery(query, null);

        if ((data != null) && (data.getCount() > 0)) {
            data.moveToFirst();
            ignored = data.getInt(0);
            data.close();
        }
        // No Record found.
        else {
            ignored = 0;
        }

        return ignored;
    }

    /**
     * Updates the day of a Record with given old_day, new_day and cycle_number.
     *
     * @param old_day                   Old Day of the Record.
     * @param new_day                   New Day to be changed to.
     * @param cycle_number              Cycle containing this Record.
     * @return changed_rows             Number of affected rows.
     */
    public long updateRecordDay(double old_day, double new_day, int cycle_number) {
        SQLiteDatabase db;
        ContentValues content_values;
        String where_clause;
        long changed_rows;

        db = getWritableDatabase();
        content_values = new ContentValues();
        content_values.put(RECORD_COL1, new_day);

        where_clause = RECORD_COL1 + "=" + old_day + " AND " +
                       RECORD_COL4 + "=" + getCycleId(cycle_number);
        changed_rows = db.update(RECORD_TABLE, content_values, where_clause, null);

        return changed_rows;
    }
    /**
     * Updates the Temperature of a Record with given day, temperature and cycle_number.
     *
     * @param day                       Day of the Record.
     * @param temperature               New Temperature of the Record.
     * @param cycle_number              Cycle containing this Record.
     * @return changed_rows             Number of affected rows.
     */
    public long updateRecordTemperature(double day, double temperature, int cycle_number) {
        SQLiteDatabase db;
        ContentValues content_values;
        String where_clause;
        long changed_rows;

        db = getWritableDatabase();
        content_values = new ContentValues();
        content_values.put(RECORD_COL2, temperature);

        where_clause = RECORD_COL1 + "=" + day + " AND " +
                RECORD_COL4 + "=" + getCycleId(cycle_number);
        changed_rows = db.update(RECORD_TABLE, content_values, where_clause, null);

        return changed_rows;
    }

    /**
     * Updates the is_ignored of a Record with given day, ignored and cycle_number.
     *
     * @param day                       Day of the Record.
     * @param ignored                   New ignored value of the Record.
     * @param cycle_number              Cycle containing this Record.
     * @return changed_rows             Number of affected rows.
     */
    public long updateRecordIgnored(double day, int ignored, int cycle_number) {
        SQLiteDatabase db;
        ContentValues content_values;
        String where_clause;
        long changed_rows;

        db = getWritableDatabase();
        content_values = new ContentValues();
        content_values.put(RECORD_COL3, ignored);

        where_clause = RECORD_COL1 + "=" + day + " AND " +
                       RECORD_COL4 + "=" + getCycleId(cycle_number);
        changed_rows = db.update(RECORD_TABLE, content_values, where_clause, null);

        return changed_rows;
    }

}
