package com.via.avi.files;

import java.io.File;
import java.util.concurrent.ExecutorService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

public class OldFilesCleaner extends BroadcastReceiver {
  private static String TAG = "OldFilesCleaner";
  private ExecutorService pool;

  private String rootDir;
  private String fileDir;
  private Long maxFileAgeMillis;

  public OldFilesCleaner(ExecutorService pool, String rootDir, String fileDir,
      Long maxFileAgeMillis) {
    this.pool = pool;

    this.rootDir = rootDir;
    this.fileDir = fileDir;
    this.maxFileAgeMillis = maxFileAgeMillis;
  }

  @Override
  public void onReceive(final Context context, Intent i) {
    Log.d(TAG, "OldFilesCheckAlarm intent received");
    // requires accessing file database: do on background thread
    pool.execute(new Runnable() {
      @Override
      public void run() {
        // Acquire a wake lock to guarantee that the code runs
        PowerManager pm = (PowerManager) context
            .getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();
        try {
          // Check files in the VIA folder and eliminate those older than two weeks
          File parentDir = new File(rootDir, fileDir);
          File[] files = parentDir.listFiles();
          for (File file : files) {
            Log.d(TAG, "File name " + file.getName());
            Log.d(TAG, "File last modified: " + file.lastModified());
            if (System.currentTimeMillis() - file.lastModified() > maxFileAgeMillis) {
              Log.d(TAG, "Old File, eliminate.");
              if (file.isDirectory()) {
                String[] children = file.list();
                for (int i = 0; i < children.length; i++) {
                  new File(file, children[i]).delete();
                }
              }
              boolean deleted = file.delete();
              Log.d(TAG, "File deleted? " + deleted);
            } else if (file.getName().equals(fileDir)) {
              Log.d(TAG, "this is the latest file folder");
            }
          }
        } catch (Exception e) {
          Log.e(TAG, "Exception in old files check!", e);
        } finally {
          // Release the wakelock
          wl.release();
        }
      }
    });


  }

}
