package com.via.avi.files;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Locale;

import com.via.avi.messages.Exist;
import com.via.avi.utils.Util;

import android.util.Log;

public class AviFileManager {
  private static String TAG = "AviFileManager";

  private File storageFile;
  private String rootDir, logDir, fullLogDir;

  private File existFile, errorFile;
  private AviFileWriter existWriter;
  
  public AviFileManager(File storageFile, String rootDir) {
    this.storageFile = storageFile;
    this.rootDir = rootDir;
    
    this.prepareFiles();
  }

  public void prepareFiles() {
    // unique log folder each time app starts
    // time string is in format YYYYmmddHHMM
    String appStartTimeString = String.format(Locale.US,
        "%04d%02d%02d%02d%02d",
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH) + 1,
        Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        Calendar.getInstance().get(Calendar.MINUTE));
    logDir = "device-" + android.os.Build.SERIAL
        + "-" + appStartTimeString;

    Log.d(TAG, "save files to: " + logDir);
    // create data folder
    fullLogDir = new File(new File(storageFile, rootDir), logDir).getAbsolutePath();
    Log.d(TAG, "Writing log files to " + fullLogDir);
    // create files for exist messages internal log
    existFile = new File(fullLogDir, "exist.txt");
    errorFile = new File(fullLogDir, "err.txt");
    existWriter = new AviFileWriter(fullLogDir, existFile);
  }
  
  public void writeExist(Exist e) {
    existWriter.writeMessage(e.toString());
  }
  
  public void writeException(Throwable ex) {
    // log exception to file
    try {
      if (!errorFile.exists()) {
        errorFile.createNewFile();
      }
      PrintWriter bwErr = new PrintWriter(new FileWriter(errorFile, true));
      bwErr.append(Util.formatTime(Util.getCurrentTimeWithGpsOffset()) + "\n");
      ex.printStackTrace(bwErr);
      bwErr.close();
    } catch (IOException e) {
      Log.e(TAG, "Error in error file writing", e);
    }
  }
  
  public void close() {
    existWriter.close();
  }
  
  public String getFilePath() {
    return fullLogDir;
  }
}
