package com.via.avi.messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

public class ExistDataSource {
  private static String TAG = "CrashDataSource";
  // Database fields
  private SQLiteDatabase database;
  private ExistDbHelper dbHelper;

  // Exist message queue and index to initially store exist messages
  private final AtomicInteger existIdAtomicInteger;
  private ConcurrentHashMap<Integer,Exist> existMessageHashMap =
      new ConcurrentHashMap<Integer,Exist>();
  private int existMessageQueueSizeLimit;

  private boolean open = false;
  private String[] allColumns = { ExistDbHelper.COLUMN_ID,
      ExistDbHelper.COLUMN_DEVICE_ID,
      ExistDbHelper.COLUMN_TS,
      ExistDbHelper.COLUMN_SENT_TIME,
      ExistDbHelper.COLUMN_APP_VERSION,
      ExistDbHelper.COLUMN_BATTERY_LEVEL,
      ExistDbHelper.COLUMN_BATTERY_STATUS,
      ExistDbHelper.COLUMN_BATTERY_PLUGGED,
      ExistDbHelper.COLUMN_BATTERY_TEMPERATURE,
      ExistDbHelper.COLUMN_BATTERY_HEALTH,
      ExistDbHelper.COLUMN_COMMUNICATION,
      ExistDbHelper.COLUMN_MQTT,
      ExistDbHelper.COLUMN_LAST_GPS_TIME,
      ExistDbHelper.COLUMN_LATITUDE,
      ExistDbHelper.COLUMN_LONGITUDE};

  public ExistDataSource(ExistDbHelper dbHelper, int existMessageQueueSizeLimit) {
    this.dbHelper = dbHelper;
    this.existMessageQueueSizeLimit = existMessageQueueSizeLimit;
    this.existIdAtomicInteger = new AtomicInteger(this.getExistIdOffset());
  }

  /**
   * Open SQLite database asynchronously.
   * 
   * @throws SQLException
   */
  public void openAsync() throws SQLException {
    // open db in separate thread:
    OpenDbAsyncTask task = new OpenDbAsyncTask();
    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
  }


  /**
   * Close SQLite database asynchronously.
   * 
   * @throws SQLException
   */
  public void closeAsync() {
    // open db in separate thread:
    CloseDbAsyncTask task = new CloseDbAsyncTask();
    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
  }

  /**
   * Insert generated but unsent Exist message into the exist message queue. If 
   * the queue has reached its maximum capacity, empty it and write all Exist 
   * messages in the queue to the database for future sending. This occurs 
   * synchronously and therefore should be executed on a background thread.
   * 
   * @param exist
   *      Exist message to be stored
   *      
   * @return
   *      Autogenerated ID of the new message in the database.
   */
  public int addNewExist(Exist ex) {

    int existId = existIdAtomicInteger.incrementAndGet();

    // Check if the existMessageHashMap has reached its size limit, 
    // if so dump the messages into the database    
    if (existMessageHashMap.size() >= existMessageQueueSizeLimit){
      flushExistMessageQueueToDb();
    }
    existMessageHashMap.put(existId,ex);
    return existId;
  }

  /**
   * Get a list of the oldest messages in the database.
   * 
   * @param maxMessages
   *      Maximum number of messages to return.
   * @return
   *      A list (possibly empty) of the oldest unsent exist messages.
   */
  public List<Exist> getOldestUnsentMessages(int maxMessages) {    
    // Dump all exist messages still in the HashMap into the database
    flushExistMessageQueueToDb();

    List<Exist> exists = new ArrayList<Exist>();
    if (maxMessages < 0) {
      return exists;
    }

    // sort by time ascending:
    String orderClause = ExistDbHelper.COLUMN_TS + " ASC";
    // limit number of messages:
    String limitClause = ""+maxMessages;

    Cursor cursor = database.query(ExistDbHelper.TABLE_EXIST,
        allColumns, null, null, null, null,
        orderClause, limitClause);

    if (cursor.moveToFirst()) {
      while (!cursor.isAfterLast()) {
        Exist exist = cursorToExist(cursor);
        exists.add(exist);
        try {
          cursor.moveToNext();
        } catch (Exception e) {
          Log.e(TAG,"Couldn't move to the next element in the cursor",e);
          break;
        }
      }
    }
    // make sure to close the cursor
    cursor.close();

    return exists;
  }

  /**
   * Gets a single unsent Exist message by its ID.
   * 
   * @param id
   *      ID of the Exist message to retrieve.
   * @return
   *      The Exist message.
   */
  public Exist getSingleExistMessage(int id) {

    Exist exist = null;

    if (existMessageHashMap.containsKey(id)) {
      exist = existMessageHashMap.get(id);
    } else {
      String whereClause = ExistDbHelper.COLUMN_ID + "=" + id;

      Cursor cursor = database.query(ExistDbHelper.TABLE_EXIST,
          allColumns, whereClause, null, null, null, null, null);
      if (cursor.moveToFirst()) {
        while (!cursor.isAfterLast()) {
          exist = cursorToExist(cursor);
          cursor.close();
          // should only be one exist message, so return it
          return exist;
        }
      }
      // make sure to close the cursor
      cursor.close();
    }

    return exist;
  }

  /**
   * Delete Exist messages from database. Call this function after messages
   * have been successfully sent.
   * 
   * @param ids
   *      List of IDs of Exist messages to be deleted.
   */
  public void removeSentMessages(List<Integer> ids) {
    // Make sure that the existMessageHashMap is emptied
    flushExistMessageQueueToDb();

    String[] idArray = new String[ids.size()];
    for (int i=0; i<ids.size(); i++) {
      idArray[i] = ""+ids.get(i);
    }

    final String whereClause = ExistDbHelper.COLUMN_ID + " in (" + 
        makePlaceholders(ids.size()) + ")";
    database.delete(ExistDbHelper.TABLE_EXIST, whereClause, idArray);
  }

  /**
   * @return
   *      Whether SQLite database is open yet.
   */
  private boolean isOpen() {
    return open;
  }

  private Exist cursorToExist(Cursor cursor) {
    Exist exist = new Exist();

    exist.setId(cursor.getInt(0));
    exist.setDeviceId(cursor.getString(1));
    exist.setTime(cursor.getLong(2));
    if (!cursor.isNull(3)) exist.setSentTime(cursor.getLong(3));
    if (!cursor.isNull(4)) exist.setAppVersion(cursor.getString(4));
    if (!cursor.isNull(5)) exist.setBatteryLevel(cursor.getDouble(5));
    if (!cursor.isNull(6)) exist.setBatteryStatus(cursor.getInt(6));
    if (!cursor.isNull(7)) exist.setBatteryPlugged(cursor.getInt(7));
    if (!cursor.isNull(8)) exist.setBatteryTemperature(cursor.getInt(8));
    if (!cursor.isNull(9)) exist.setBatteryHealth(cursor.getInt(9));
    if (!cursor.isNull(10)) exist.setCommunication(boolify(cursor.getLong(10)));
    if (!cursor.isNull(11)) exist.setMqtt(boolify(cursor.getLong(11)));
    if (!cursor.isNull(12)) exist.setLastGpsTime(cursor.getLong(12));
    if (!cursor.isNull(13)) exist.setLatitude(cursor.getDouble(13));
    if (!cursor.isNull(14)) exist.setLongitude(cursor.getDouble(14));

    return exist;
  }

  /**
   * Convert Long into boolean (for handling SQLite database). Doesn't do
   * null handling.
   * 
   * @param val
   *      Long to convert.
   * @return
   *      True if val == 1, false otherwise.
   */
  private boolean boolify(Long val) { return val ==1 ? true : false; }

  /**
   * Convert Boolean object into Integer, for use in SQLite database.
   * 
   * @param val
   *      Boolean to be converted.
   * @return
   *      0 or 1, or null if input is null.
   */
  private Integer intify(Boolean val) {
    // return null if originator is null
    if (val == null) return null;
    // otherwise, return 0 or 1
    return val ? 1 : 0;
  }

  /**
   * Open SQLite database synchronously. This should therefore only be executed
   * on a background thread.
   */
  public void open() {
    if (!isOpen()) {
      database = dbHelper.getWritableDatabase();
    }
    open = true;
  }

  /**
   * Dump all Exist objects from the ConcurrentHashMap into the database.
   * 
   * Requires DB operations -- don't run on UI thread!
   * 
   */
  public void flushExistMessageQueueToDb() {
    Log.w(TAG, "Flushing exist messages to disk");
    if (!this.isOpen()) {
      this.open();
    }
    for (Map.Entry<Integer, Exist> existMessage : existMessageHashMap.entrySet()){
      Exist exist = existMessage.getValue();

      ContentValues values = new ContentValues();

      values.put(ExistDbHelper.COLUMN_ID, existMessage.getKey());
      values.put(ExistDbHelper.COLUMN_DEVICE_ID, exist.getDeviceId());
      values.put(ExistDbHelper.COLUMN_TS, exist.getTime());
      values.put(ExistDbHelper.COLUMN_SENT_TIME, exist.getSentTime());
      values.put(ExistDbHelper.COLUMN_APP_VERSION, exist.getAppVersion());
      values.put(ExistDbHelper.COLUMN_BATTERY_LEVEL, exist.getBatteryLevel());
      values.put(ExistDbHelper.COLUMN_BATTERY_STATUS, exist.getBatteryStatus());
      values.put(ExistDbHelper.COLUMN_BATTERY_PLUGGED, exist.getBatteryPlugged());
      values.put(ExistDbHelper.COLUMN_BATTERY_TEMPERATURE, exist.getBatteryTemperature());
      values.put(ExistDbHelper.COLUMN_BATTERY_HEALTH, exist.getBatteryHealth());
      values.put(ExistDbHelper.COLUMN_COMMUNICATION, intify(exist.getCommunication()));
      values.put(ExistDbHelper.COLUMN_MQTT, intify(exist.getMqtt()));
      values.put(ExistDbHelper.COLUMN_LAST_GPS_TIME, exist.getLastGpsTime());
      values.put(ExistDbHelper.COLUMN_LATITUDE, exist.getLatitude());
      values.put(ExistDbHelper.COLUMN_LONGITUDE, exist.getLongitude());

      try {
        database.insertOrThrow(ExistDbHelper.TABLE_EXIST, null,
            values);
      } catch (SQLiteConstraintException e) {
        // This should only occur when this function is called repeatedly in
        // rapid succession (as when many exist messages are being generated).
        // As a result, swallowing the error shouldn't cause any loss of data.
        Log.e(TAG,
            "Failed to insert message with duplicate exist message ID",e);
      }

      // Remove the exist message from the ConcurrentHashMap
      existMessageHashMap.remove(existMessage.getKey());
    } 
  }

  private int getExistIdOffset(){
    int offset = 0;
    if (!this.isOpen()) {
      this.open();
    }
    // Check if there are any exist messages stored in the database
    String orderBy = ExistDbHelper.COLUMN_ID + " DESC";
    String limit = "1";
    Cursor cursor = database.query(ExistDbHelper.TABLE_EXIST,
        allColumns, null, null, null, null, orderBy, limit);
    if (cursor.moveToFirst()) {
      Exist exist = cursorToExist(cursor);
      offset = exist.getId();
    }
    // make sure to close the cursor
    cursor.close();
    return offset;
  }

  private class OpenDbAsyncTask extends AsyncTask<Void, Void, Void> {

    //    private static final String File = null;

    @Override
    protected Void doInBackground(Void... param) {
      if (!isOpen()) {
        database = dbHelper.getWritableDatabase();
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void result) {
      // record that database has been opened
      open = true;
    }
  }

  private class CloseDbAsyncTask extends AsyncTask<Void, Void, Void> {

    @Override
    protected Void doInBackground(Void... param) {
      if (isOpen()) {
        dbHelper.close();
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void result) {
      open = false;
    }
  }

  /**
   * Creates "question mark" placeholders to be used in SQLite queries.
   * 
   * @param len
   *      Number of parameters to add placeholders for.
   * @return
   *      Comma-separated list of question marks.
   */
  private String makePlaceholders(int len) {
    if (len < 1) {
      throw new RuntimeException("No placeholders.");
    } else {
      StringBuilder sb = new StringBuilder(len*2 -1);
      sb.append("?");
      for (int i=1; i<len; i++) {
        sb.append(",?");
      }
      return sb.toString();
    }
  }
}