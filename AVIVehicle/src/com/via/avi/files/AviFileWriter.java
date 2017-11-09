package com.via.avi.files;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.util.Log;

public class AviFileWriter {
  private static String TAG = "AviFileWriter";
  private File dir;
  private File file;
  private BufferedWriter bw;
  private boolean fileExists = false;

  public AviFileWriter(String fullDirPath, File file) {
    this.dir = new File(fullDirPath);

    this.file = file;
    initialize();
  }

  private void initialize() {
    if (!dir.exists()) {
      dir.mkdirs();
      Log.d(TAG, "folder " + dir.getName() + " created");
    }

    if (!file.exists()) {
      ensureFileExists();
    } else {
      fileExists = true;
    }

    if (fileExists) {
      initializeBufferedWriter();
    }
  }

  private void ensureFileExists() {
    try {
      file.createNewFile();
      fileExists = true;
    } catch (IOException e) {
      Log.e(TAG, "Failed to open " + file + " for writing.", e);
      fileExists = false;
    }
    if (fileExists) {
      Log.d(TAG, "new file " + file + 
          " created in directory " + dir);
    }
  }

  private void initializeBufferedWriter() {
    try {
      bw = new BufferedWriter(new FileWriter(file, true));
    } catch (IOException e) {
      Log.e(TAG, "Failed to open BufferedWriter for " 
          + file + " in " + dir, e);
      bw = null;
    }
  }

  public void writeMessage(String message) {
    if (bw == null) {
      initialize();
    }
    if (bw != null) {
      try {
        bw.write(message);
        bw.flush();
      } catch (IOException e) {
        Log.e(TAG, "Failed to open " + file + " inside writeMessage().", e);
      }
    } else {
      Log.w(TAG, "BufferedWriter uninitialized -- can't write message.");
    }
  }

  public void close() {
    if (bw != null) {
      try {
        bw.close();
      } catch (IOException e) {
        Log.e(TAG, "Failed to close BufferedWriter for " 
            + file + " in " + dir, e);
        bw = null;
      }
    }
  }
}
