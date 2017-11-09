package com.via.avi.utils;

import android.app.Activity;

abstract public class UIThreadLooper extends DefaultThreadLooper {
  private Activity activity;

  public UIThreadLooper(String looperName, long loopMillis, 
      Activity activity) {
    super(looperName, loopMillis);
    this.activity = activity;
  }

  private void superStartLooping() {
    super.startLooping();
  }

  @Override
  public void startLooping() {
    Runnable startLoopRunnable = new Runnable() {
      @Override
      public void run() {
        superStartLooping();
      }
    };
    activity.runOnUiThread(startLoopRunnable);
  }

  private void superStopLooping() {
    super.stopLooping();
  }

  @Override
  public void stopLooping() {
    Runnable stopLoopRunnable = new Runnable() {

      @Override
      public void run() {
        superStopLooping();
      }
    };
    activity.runOnUiThread(stopLoopRunnable);
  }
}
