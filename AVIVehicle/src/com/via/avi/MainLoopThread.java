package com.via.avi;

import android.location.Location;
import android.os.SystemClock;
import android.util.Log;

import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.gs.UpdatableGlobalStateCopy;
import com.via.avi.messages.MessageSender;
import com.via.avi.messages.RawLocation;
import com.via.avi.utils.Util;

public class MainLoopThread extends Thread {
  private static String TAG = "MainLoopThread";

  private boolean mStarted = false;
  private boolean mPaused = false;
  private long mSleepTime;

  private MessageSender messageSender;

  public MainLoopThread(MessageSender messageSender, long sleepTime) {
    this.messageSender = messageSender;
    mSleepTime = sleepTime;
  }

  @Override
  public void run() {
    mStarted = true;
    while (mStarted) {
      if (!mPaused) {
        // clone global state
        UpdatableGlobalStateCopy localState = 
            UpdatableGlobalState.getInstance().clone();
        mainThread(localState);
      }
      SystemClock.sleep(mSleepTime);
    }
  }

  public void close() {
    mStarted = false;
  }

  public void pause() {
    Log.i(TAG, "Pausing MainEventLoop");
    mPaused = true;
  }

  public void unpause() {
    Log.i(TAG, "Unpausing MainEventLoop");
    mPaused = false;
  }

  public void mainThread(UpdatableGlobalStateCopy localState) {
    Location location = localState.getDeviceStateCopy().getCurrentLocation();

    if (location == null) {
      // can't do anything without any locations
      return;
    }

    // generate and send raw location message
    sendRawLocMessage(localState.getDeviceStateCopy(), location);
  }

  private void sendRawLocMessage(DeviceState deviceStatus, Location location) {
    RawLocation rawLoc = new RawLocation();
    rawLoc.setDeviceId(deviceStatus.getDeviceId()).setLocation(location)
    .setTime(location.getTime());

    if (!Util.MockMode) {
      messageSender.sendRawLocationMessage(rawLoc);
    } else {
      Log.d(TAG, "MockMode: not sending raw location");
    }
  }
}
