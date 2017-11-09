package com.via.avi;

import android.os.Handler;
import android.util.Log;

import com.via.avi.WakeStateMachine.WakeAction;
import com.via.avi.WakeStateMachine.WakeState;
import com.via.avi.config.ConfigValues;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.gs.UpdatableGlobalStateCopy;

public class WakeStateManager {
  private WakeStateMachine wakeStateMachine;
  private Runnable wakeManagerRunnable;
  private Handler wakeManagerHandler;

  private DeviceManager dm;
  private String TAG = "WakeStateManager";

  private boolean stopped = true;

  public WakeStateManager(DeviceManager dm, ConfigValues cv, String timeZone) {
    this.dm = dm;

    wakeStateMachine = new WakeStateMachine(cv, timeZone);

    wakeManagerHandler = new Handler();
    final long wakeCadence = cv.WakeCadence();
    wakeManagerRunnable = new Runnable() {
      public void run() {
        manageInteractions();

        if (wakeStateMachine.getWakeState() != WakeState.ASLEEP) {
          // redundant but still:
          stopped = false;
          wakeManagerHandler.postDelayed(wakeManagerRunnable, wakeCadence);
        } else {
          // don't keep the interactions going if we're asleep
          Log.i(TAG, "Asleep: stopping WakeState loop.");
          stopped = true;
        }

      }
    };
  }

  public void startHandling(long delayMillis) {
    if (stopped) {
      // Only post a new runnable if we're stopped. This prevents multiple 
      // runnables from being posted at once.
      stopped = false;
      wakeManagerHandler.postDelayed(wakeManagerRunnable, delayMillis);
    }
  }

  public void manageInteractions() {
    UpdatableGlobalStateCopy localState = UpdatableGlobalState.getInstance().clone();
    WakeAction action = wakeStateMachine.stateMachineTransition(localState);

    if (action == WakeAction.ENABLE_WAKE_TASKS) {
      Log.d(TAG,"Enable Connectivity and Screen");
      dm.enableWakeTasks();
    } else if (action == WakeAction.ENABLE_LOCATIONS) {
      Log.d(TAG,"Enable Locations");
      dm.enableLocations();
    } else if (action == WakeAction.DISABLE_WAKE_TASKS) {
      Log.d(TAG,"Disable Connectivity and Screen");
      dm.disableWakeTasks();
    } else if (action == WakeAction.DISABLE_LOCATIONS) {
      Log.d(TAG,"Disable Locations");
      dm.disableLocations();
    } else if (action == WakeAction.DISABLE_AIRPLANE_MODE) {
      Log.d(TAG,"Disable Airplane Mode");
      dm.disableAirplaneMode();
    } else if (action == WakeAction.ENABLE_AIRPLANE_MODE) {
      Log.d(TAG,"Enable Airplane Mode");
      dm.enableAirplaneMode();
    } else if (action == WakeAction.UNKNOWN_TO_AWAKE) {
      // ensure that everything else is off, consistent w/ our current state
      dm.disableLocations();
      dm.disableWakeTasks();

      // then disable airplane mode, on the way to waking up 
      dm.disableAirplaneMode();
    } else if (action == WakeAction.UNKNOWN_TO_SLEEP) {
      // ensure that everything else is on, consistent w/ our current state
      dm.disableAirplaneMode();
      dm.enableWakeTasks();

      // then disable locations, on the way to sleeping 
      dm.disableLocations();
    }
  }

  public void stopHandling() {
    wakeManagerHandler.removeCallbacksAndMessages(null);
  }

}
