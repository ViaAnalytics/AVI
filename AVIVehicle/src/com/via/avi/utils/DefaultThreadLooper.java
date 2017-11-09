package com.via.avi.utils;

import android.os.Handler;
import android.util.Log;

abstract public class DefaultThreadLooper implements ThreadLooper {
  private String TAG;
  private long loopMillis;
  private Runnable mRunnable = null;
  private Handler mHandler = null;
  private Boolean looping = false;

  public DefaultThreadLooper(String looperName, long loopMillis) {
    TAG = looperName;
    this.loopMillis = loopMillis;
  }

  abstract public void runOnce();

  @Override
  public boolean isLooping() { return looping; };

  @Override
  public void setLoopMillis(long loopMillis) {
    this.loopMillis = loopMillis;
  }

  @Override
  public void startLooping() {
    Log.d(TAG, "Starting loop");

    if (mHandler == null) {
      mHandler = new Handler();
    }

    if (mRunnable == null) {
      mRunnable = new Runnable() {
        public void run() {
          // call the actual work function
          if (looping) {
            runOnce();
          }

          // We might have stopped animating while in the middle of this 
          // evaluation, so check the global variable again.
          if (looping) {
            mHandler.postDelayed(mRunnable, loopMillis);
          }

        }
      };
    }

    // remove all old callbacks, just in case
    mHandler.removeCallbacksAndMessages(null);
    looping = true;
    mHandler.post(mRunnable);
  }

  @Override
  public void stopLooping() {
    Log.d(TAG, "Stopping loop");
    if (mHandler != null && looping) {
      mHandler.removeCallbacks(mRunnable);
      looping = false;
    }
  }

}
