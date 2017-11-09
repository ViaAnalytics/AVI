package com.via.avi.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

public class RepeatingAlarm {
  private static String TAG = "RepeatingAlarm";

  private String alarmIntent;
  private Class<?> alarmClass;
  private Long repeatPeriod;
  private Long initialDelay;

  public RepeatingAlarm(String alarmIntent, Long repeatPeriod,
      Long initialDelay) {
    this.alarmIntent = alarmIntent;
    this.repeatPeriod = repeatPeriod;
    this.initialDelay = initialDelay;
  }

  public RepeatingAlarm(Class<?> alarmClass, Long repeatPeriod,
      Long initialDelay) {
    this.alarmClass = alarmClass;
    this.repeatPeriod = repeatPeriod;
    this.initialDelay = initialDelay;
  }

  public void setRepeatPeriod(Long repeatPeriod) {
    this.repeatPeriod = repeatPeriod;
  }

  public void setInitialDelay(Long initialDelay) {
    this.initialDelay = initialDelay;
  }

  public void setAlarm(Context context){
    AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent i = generateIntent(context);
    PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
    Log.i(TAG, "Setting repeating alarm with intent " + i);
    long tStart = SystemClock.elapsedRealtime() + initialDelay;
    am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
        tStart, this.repeatPeriod, pi);
  }

  public void cancelAlarm(Context context){
    Intent i = generateIntent(context);
    PendingIntent sender = PendingIntent.getBroadcast(context, 0, i, 0);
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(
        Context.ALARM_SERVICE);
    alarmManager.cancel(sender);
  }

  private Intent generateIntent(Context context) {
    Intent i = null;
    if (this.alarmIntent != null) {
      i = new Intent(this.alarmIntent);
    } else {
      i = new Intent(context, alarmClass);
    }
    return i;
  }

}
