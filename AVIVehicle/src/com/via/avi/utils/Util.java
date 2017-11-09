package com.via.avi.utils;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;
import com.via.avi.messages.AviMessages.ExistMessage;

public class Util {

  private static String TAG = "Util";

  // debug flag disables updater, anr watch dog, ueh, and modifies battery
  // thresholds
  public static boolean Debug = false;

  public static boolean MockMode = false;

  // ********************** Intent values ********************
  // File cleaning alarm
  public static final String OLD_FILES_CHECK_INTENT = "old_files_check";
  // Exist message alarm
  public static final String EXIST_MESSAGE_ALARM_INTENT = "exist_message";
  // Safety net check
  public static final String SAFETY_NET_CHECK_INTENT = "safety_net_check";

  // ******************** Safety net info *********************
  public static final String SafetyNetPackage = "com.avi.via.safetynet";
  public static final String SafetyNetService = "SafetyNetService";
  public static final String SafetyNetApk = "SafetyNet.apk";

  private static long SystemGPSTimeOffset = 0L;

  private Util() {
    // private constructor to prevent instantiation (only static methods)
  }

  public static byte[] changeExistSentTime(byte[] existMessage, Long newSentTime) {
    try {
      ExistMessage oldMessage = ExistMessage.parseFrom(existMessage);
      ExistMessage.Builder newMessage = ExistMessage.newBuilder(oldMessage);
      newMessage.setSentTime(newSentTime);
      return newMessage.build().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG,"Malformed exist protobuf!", e);
      return null;
    }
  }

  // convert the time into the following format
  // YYYY/MM/DD HH:MM:SS.sss
  // For example, 1317891600000 => 2011/10/06 21:00:00.000
  public static String formatTime(Long time) {

    String newTime = "";
    Calendar ourTime = Calendar.getInstance();
    ourTime.setTimeInMillis(time);
    newTime = Integer.toString(ourTime.get(Calendar.YEAR)) + "/"
        + Integer.toString(ourTime.get(Calendar.MONTH) + 1) + "/"
        + Integer.toString(ourTime.get(Calendar.DAY_OF_MONTH)) + " "
        + String.format("%02d", ourTime.get(Calendar.HOUR_OF_DAY)) + ":"
        + String.format("%02d", ourTime.get(Calendar.MINUTE)) + ":"
        + String.format("%02d", ourTime.get(Calendar.SECOND)) + "."
        + Integer.toString(ourTime.get(Calendar.MILLISECOND));
    // System.out.println(newTime); // Display the string.
    return newTime;
  }

  public static void setGPSTimeOffset(long offset) {
    SystemGPSTimeOffset = offset;
  }

  public static long getCurrentTimeWithGpsOffset(){
    return System.currentTimeMillis() - SystemGPSTimeOffset;
  }

  public static String joinWithDelim(List<String> els, String delim) {
    StringBuilder sb = new StringBuilder();

    String loopDelim = "";
    for(String s : els) {
      sb.append(loopDelim);
      sb.append(s);            

      loopDelim = delim;
    }

    return sb.toString();
  }

  /**
   * @param timeZone
   * @param time
   *      Epoch millis.
   * @param zeroHour
   *      Hour before which it is considered to be the previous day.
   * @return
   *      Beginning of current day in epoch millis.
   */
  public static Long getBeginningOfDay(String timeZone, long time,
      int zeroHour) {
    Calendar currentTimeCalendar = Calendar.getInstance();
    currentTimeCalendar.setTimeZone(TimeZone.getTimeZone(timeZone));
    currentTimeCalendar.setTimeInMillis(time - zeroHour*60*60*1000);
    int year = currentTimeCalendar.get(Calendar.YEAR);
    int month = currentTimeCalendar.get(Calendar.MONTH);
    int day= currentTimeCalendar.get(Calendar.DAY_OF_MONTH);

    Calendar beginningOfDay = Calendar.getInstance();
    beginningOfDay.setTimeZone(TimeZone.getTimeZone(timeZone));
    beginningOfDay.set(year, month, day, 0,0,0);
    beginningOfDay.set(Calendar.MILLISECOND, 0);
    return beginningOfDay.getTimeInMillis();  
  }
}
