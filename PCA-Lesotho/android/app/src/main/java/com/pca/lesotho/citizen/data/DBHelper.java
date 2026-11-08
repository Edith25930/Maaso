package com.pca.lesotho.citizen.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Local SQLite store, used for offline-first behaviour:
 *  - complaint drafts / queued-for-submission complaints
 *  - a lightweight cache of the last-synced complaint list (for offline viewing)
 *  - cached Learn/FAQ content so it works with no connection
 *  - the sync queue mirrored from the server-side `sync_queue` table
 *
 * This mirrors the shape of the relevant tables in the central 3NF MySQL
 * schema (see /database/pca_mobile_schema.sql) at a reduced, device-local grain.
 */
public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "pca_lesotho.db";
    private static final int DB_VERSION = 7;

    public static final String TABLE_DRAFTS = "complaint_drafts";
    public static final String TABLE_CACHED_COMPLAINTS = "cached_complaints";
    public static final String TABLE_CACHED_ARTICLES = "cached_articles";
    public static final String TABLE_SYNC_QUEUE = "sync_queue";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_DRAFTS + " (" +
                "local_uuid TEXT PRIMARY KEY," +
                "complainant_name TEXT," +
                "complainant_phone TEXT," +
                "complainant_id_number TEXT," +
                "complainant_location TEXT," +
                "complainant_gender TEXT," +
                "complainant_district_id INTEGER," +
                "category_id INTEGER," +
                "category_name TEXT," +
                "category_unknown INTEGER DEFAULT 0," +
                "incident_date TEXT," +
                "incident_time TEXT," +
                "district_id INTEGER," +
                "incident_location_text TEXT," +
                "incident_station_name TEXT," +
                "incident_station_id INTEGER," +
                "latitude REAL," +
                "longitude REAL," +
                "location_shared INTEGER DEFAULT 0," +
                "description TEXT," +
                "officer_unknown INTEGER DEFAULT 0," +
                "officer_name TEXT," +
                "officer_rank TEXT," +
                "badge_number TEXT," +
                "officer_station_name TEXT," +
                "witnesses_json TEXT," +
                "evidence_json TEXT," +
                "declaration_confirmed INTEGER DEFAULT 0," +
                "sync_status TEXT DEFAULT 'draft'," +          // draft | pending_submission | submitted | submission_failed
                "gcr_number TEXT," +
                "updated_at INTEGER" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_CACHED_COMPLAINTS + " (" +
                "complaint_id INTEGER PRIMARY KEY," +
                "gcr_number TEXT," +
                "category_name_en TEXT," +
                "category_name_st TEXT," +
                "status_code TEXT," +
                "status_label_en TEXT," +
                "status_label_st TEXT," +
                "last_synced_at INTEGER" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_CACHED_ARTICLES + " (" +
                "article_id INTEGER PRIMARY KEY," +
                "category_name_en TEXT," +
                "title_en TEXT," +
                "title_st TEXT," +
                "definition_en TEXT," +
                "definition_st TEXT" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_SYNC_QUEUE + " (" +
                "queue_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "entity_type TEXT," +          // complaint | evidence_file
                "local_reference TEXT," +
                "status TEXT DEFAULT 'queued'," +
                "retry_count INTEGER DEFAULT 0," +
                "created_at INTEGER" +
                ")");

        db.execSQL("CREATE TABLE report_form_draft (" +
                "draft_id INTEGER PRIMARY KEY CHECK (draft_id = 1)," +
                "owner_user_id INTEGER NOT NULL DEFAULT 0," +
                "form_state TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_DRAFTS + " ADD COLUMN complainant_name TEXT");
            db.execSQL("ALTER TABLE " + TABLE_DRAFTS + " ADD COLUMN complainant_phone TEXT");
            db.execSQL("ALTER TABLE " + TABLE_DRAFTS + " ADD COLUMN complainant_id_number TEXT");
            db.execSQL("ALTER TABLE " + TABLE_DRAFTS + " ADD COLUMN complainant_location TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_DRAFTS + " ADD COLUMN complainant_gender TEXT");
            db.execSQL("ALTER TABLE " + TABLE_DRAFTS + " ADD COLUMN complainant_district_id INTEGER");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + TABLE_DRAFTS + " ADD COLUMN incident_station_name TEXT");
            db.execSQL("ALTER TABLE " + TABLE_DRAFTS + " ADD COLUMN officer_station_name TEXT");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE " + TABLE_DRAFTS + " ADD COLUMN category_name TEXT");
        }
        if (oldVersion < 6) {
            db.execSQL("CREATE TABLE report_form_draft (" +
                    "draft_id INTEGER PRIMARY KEY CHECK (draft_id = 1)," +
                    "owner_user_id INTEGER NOT NULL DEFAULT 0," +
                    "form_state TEXT NOT NULL," +
                    "updated_at INTEGER NOT NULL" +
                    ")");
        }
        if (oldVersion == 6) {
            db.execSQL("ALTER TABLE report_form_draft ADD COLUMN owner_user_id INTEGER NOT NULL DEFAULT 0");
        }
    }

    public void saveReportFormState(String formState, long ownerUserId) {
        ContentValues values = new ContentValues();
        values.put("draft_id", 1);
        values.put("owner_user_id", ownerUserId);
        values.put("form_state", formState);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(
                "report_form_draft", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getReportFormState(long ownerUserId) {
        try (Cursor cursor = getReadableDatabase().query(
                "report_form_draft", new String[]{"form_state"}, "draft_id = 1 AND owner_user_id = ?",
                new String[]{Long.toString(ownerUserId)}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    public void clearReportFormState(long ownerUserId) {
        getWritableDatabase().delete("report_form_draft",
                "draft_id = 1 AND owner_user_id = ?", new String[]{Long.toString(ownerUserId)});
    }

    /** Insert or update a draft as the user moves through the report-complaint stepper. */
    public void saveDraft(ComplaintDraft draft) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = draft.toContentValues();
        cv.put("updated_at", System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_DRAFTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Cursor getDraft(String localUuid) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_DRAFTS, null, "local_uuid = ?", new String[]{localUuid}, null, null, null);
    }

    public Cursor getAllPendingDrafts() {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_DRAFTS, null, "sync_status != ?", new String[]{"submitted"}, null, null, "updated_at DESC");
    }

    public void markDraftSubmitted(String localUuid, String gcrNumber) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("sync_status", "submitted");
        cv.put("gcr_number", gcrNumber);
        db.update(TABLE_DRAFTS, cv, "local_uuid = ?", new String[]{localUuid});
    }

    public void enqueueForSync(String entityType, String localReference) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("entity_type", entityType);
        cv.put("local_reference", localReference);
        cv.put("created_at", System.currentTimeMillis());
        db.insert(TABLE_SYNC_QUEUE, null, cv);
    }
}
