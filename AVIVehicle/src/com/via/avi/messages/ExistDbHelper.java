package com.via.avi.messages;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class ExistDbHelper extends SQLiteOpenHelper {
  static final String TAG = "ExistDbHelper";
  public static final String TABLE_EXIST = "exist";
  public static final String COLUMN_ID = "_id";

  public static final String COLUMN_DEVICE_ID = "device_id";

  public static final String COLUMN_TS = "ts";
  public static final String COLUMN_SENT_TIME = "sent_time";

  public static final String COLUMN_APP_VERSION = "app_version";

  // battery status info
  public static final String COLUMN_BATTERY_LEVEL = "battery_level";
  public static final String COLUMN_BATTERY_STATUS = "battery_status";
  public static final String COLUMN_BATTERY_PLUGGED = "battery_plugged";
  public static final String COLUMN_BATTERY_TEMPERATURE = "battery_temperature";
  public static final String COLUMN_BATTERY_HEALTH = "battery_health";

  // peripheral statuses
  public static final String COLUMN_COMMUNICATION = "communication";
  public static final String COLUMN_MQTT = "mqtt";
  public static final String COLUMN_LAST_GPS_TIME = "last_gps_time";

  // location
  public static final String COLUMN_LATITUDE = "latitude";
  public static final String COLUMN_LONGITUDE = "longitude";

  public static final String DATABASE_NAME = "exist.db";
  private static final int DATABASE_VERSION = 1;

  //Database creation sql statement
  private static final String DATABASE_CREATE = "create table "
      + TABLE_EXIST + "(" + COLUMN_ID + " integer primary key autoincrement, "
      + COLUMN_DEVICE_ID + " text not null,"
      + COLUMN_TS + " integer not null,"
      + COLUMN_SENT_TIME + " integer,"
      + COLUMN_APP_VERSION + " text,"
      + COLUMN_BATTERY_LEVEL + " real,"
      + COLUMN_BATTERY_STATUS + " integer,"
      + COLUMN_BATTERY_PLUGGED + " integer,"
      + COLUMN_BATTERY_TEMPERATURE + " integer,"
      + COLUMN_BATTERY_HEALTH + " integer,"
      + COLUMN_COMMUNICATION + " integer,"
      + COLUMN_MQTT + " integer,"
      + COLUMN_LAST_GPS_TIME + " integer,"
      + COLUMN_LATITUDE + " real,"
      + COLUMN_LONGITUDE + " real);";

  public ExistDbHelper(Context context) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(DATABASE_CREATE);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
    Log.i(TAG, "Upgrading database from version "
        + oldVer + " to " + newVer
        + ", which will destroy all old data");
    db.execSQL("DROP TABLE IF EXISTS " + TABLE_EXIST);
    onCreate(db);
  }
}