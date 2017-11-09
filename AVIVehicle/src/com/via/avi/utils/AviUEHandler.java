package com.via.avi.utils;

import com.via.avi.AviActivity;
import com.via.avi.files.AviFileManager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AviUEHandler implements Thread.UncaughtExceptionHandler {
  private static String TAG = "AviUEHandler";

  private Context appContext;
  private AviFileManager fileManager;

  public AviUEHandler(Context appContext, AviFileManager fileManager) {
    this.appContext = appContext;
    this.fileManager = fileManager;
  }

  @Override
  public void uncaughtException(Thread thread, Throwable ex) {

    Long timeStart = Util.getCurrentTimeWithGpsOffset(); 

    Log.e(TAG,"Uncaught exception at time = " + timeStart, ex);
    
    fileManager.writeException(ex);

    PendingIntent myActivity = PendingIntent.getActivity(appContext, 0,
        new Intent(appContext, AviActivity.class),
        PendingIntent.FLAG_ONE_SHOT);
    AlarmManager alarmManager = (AlarmManager) 
        appContext.getSystemService(Context.ALARM_SERVICE);
    alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 5000,
        myActivity);

    System.exit(2);

    // re-throw critical exception further to the os (important)
    Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, ex);
  }
}
