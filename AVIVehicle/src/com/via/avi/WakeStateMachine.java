package com.via.avi;

import android.location.Location;
import android.os.BatteryManager;
import android.util.Log;

import com.via.avi.config.ConfigValues;
import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalStateCopy;
import com.via.avi.utils.Util;

public class WakeStateMachine {
  public static final String TAG = "WakeStateMachine";

  public static enum WakeState {
    UNKNOWN,
    AWAKE,
    ASLEEP,
    INTERMEDIATE_WITHOUT_LOC,
    INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS
  }

  public static enum WakeAction {
    NOTHING,
    ENABLE_WAKE_TASKS,
    DISABLE_WAKE_TASKS,
    ENABLE_LOCATIONS,
    DISABLE_LOCATIONS,
    ENABLE_AIRPLANE_MODE,
    DISABLE_AIRPLANE_MODE,
    UNKNOWN_TO_SLEEP,
    UNKNOWN_TO_AWAKE
  }

  private WakeState wakeState;
  private ConfigValues cv;
  private String timeZone;
  private Long lastGpsResetTime;

  public WakeStateMachine(ConfigValues cv, String timeZone) {
    this.cv = cv;
    this.timeZone = timeZone;
    wakeState = WakeState.UNKNOWN;
    updateLastGpsResetTime();
  }

  public WakeAction handleFirstWakeState(UpdatableGlobalStateCopy localState) {
    // Unknown WakeState
    // If the battery can be checked then transition 
    // to either AWAKE or ASLEEP accordingly to force
    // the activation/deactivation of all sensors involved
    if (isBatteryAvailable(localState.getDeviceStateCopy())){
      // Enough battery
      if (isBatteryGood(localState.getDeviceStateCopy())) {
        // Force leave airplane mode to go into AWAKE state
        wakeState = WakeState.INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS;
        return WakeAction.UNKNOWN_TO_AWAKE;
      } else {
        // Force turn off GPS to go into ASLEEP state
        wakeState = WakeState.INTERMEDIATE_WITHOUT_LOC;
        return WakeAction.UNKNOWN_TO_SLEEP;
      }
    } else {
      Log.d(TAG,"Battery measurements not available, remain in UNKNOWN WakeState.");
      return WakeAction.NOTHING;   
    }
  }

  public WakeAction stateMachineTransition(UpdatableGlobalStateCopy localState) {
    Log.d(TAG, "Transitioning WakeStateMachine");
    Log.d(TAG, "Previous wakeState: " + wakeState);
    if (wakeState == WakeState.UNKNOWN) {
      return handleFirstWakeState(localState);
    }

    boolean battGood = isBatteryGood(localState.getDeviceStateCopy());
    boolean forcedSleep = forcedSleep();
    boolean targetAwake = battGood && !forcedSleep;
    if (targetAwake) {
      // good battery
      if (wakeState == WakeState.AWAKE) {
        if (isLocationOld(localState.getDeviceStateCopy().getCurrentLocation()) 
            && enoughTimeSinceLastGpsReset()) {
          Log.d(TAG,"Awake state without a GPS fix for an extended period of time, trying to reset GPS");
          // if GPS info is old, try to reset the GPS
          wakeState = WakeState.INTERMEDIATE_WITHOUT_LOC;
          return WakeAction.DISABLE_LOCATIONS;
        } else {
          return WakeAction.NOTHING;
        }
      } else if (wakeState == WakeState.INTERMEDIATE_WITHOUT_LOC) {
        // turn on gps
        wakeState = WakeState.AWAKE;
        updateLastGpsResetTime();
        return WakeAction.ENABLE_LOCATIONS;
      } else if (wakeState == WakeState.INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS){
        // turn on connectivity
        wakeState = WakeState.INTERMEDIATE_WITHOUT_LOC;
        return WakeAction.ENABLE_WAKE_TASKS;
      } else if (wakeState == WakeState.ASLEEP) {
        // leave airplane mode
        wakeState = WakeState.INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS;
        return WakeAction.DISABLE_AIRPLANE_MODE;
      }
    } else {
      Log.d(TAG, "Sleepy time");
      // low battery -- time to sleep
      if (wakeState == WakeState.ASLEEP) {
        return WakeAction.NOTHING;
      } else if (wakeState == WakeState.INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS) {
        wakeState = WakeState.ASLEEP;
        return WakeAction.ENABLE_AIRPLANE_MODE;
      } else if (wakeState == WakeState.INTERMEDIATE_WITHOUT_LOC) {
        // turn off connectivity
        wakeState = WakeState.INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS;
        return WakeAction.DISABLE_WAKE_TASKS;
      } else if (wakeState == WakeState.AWAKE) {
        // turn off GPS
        wakeState = WakeState.INTERMEDIATE_WITHOUT_LOC;
        return WakeAction.DISABLE_LOCATIONS;
      }
    }
    return WakeAction.NOTHING;
  }

  private boolean isBatteryAvailable(DeviceState deviceState){
    int scale = deviceState.getBatteryScale();
    int level = deviceState.getBatteryLevel();
    float batteryPercentage = level / (float) scale;
    return batteryPercentage != Float.NaN;
  }

  private boolean isBatteryGood(DeviceState deviceState) {
    return validateBatteryStatus(deviceState.getBatteryChargingStatus(),
        deviceState.getBatteryPlugStatus(), deviceState.getBatteryLevel(),
        deviceState.getBatteryScale());
  }

  private boolean forcedSleep() {
    Long time = Util.getCurrentTimeWithGpsOffset();
    Long dayStart = Util.getBeginningOfDay(timeZone, time, 0);
    long dayTime = time - dayStart;
    return dayTime >= cv.ForcedSleepStart() && dayTime <= cv.ForcedSleepEnd();
  }

  public WakeState getWakeState() {
    return wakeState;
  }

  private boolean validateBatteryStatus(int status, int plug, int level,
      int scale) {
    float batteryPercentage = level / (float) scale;
    return ((status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) 
        && plug == BatteryManager.BATTERY_PLUGGED_AC
        && batteryPercentage >= cv.ChargingBatteryThreshold())
        || batteryPercentage >= cv.DischargingBatteryThreshold();
  }

  private boolean isLocationOld(Location location) {
    if (location != null) {
      return (Util.getCurrentTimeWithGpsOffset() - location.getTime()) > cv
          .MaxGpsAge();
    } else {
      return false;
    }
  }

  private boolean enoughTimeSinceLastGpsReset() {
    return (Util.getCurrentTimeWithGpsOffset() - lastGpsResetTime) > cv
        .MaxGpsAge();
  }

  private void updateLastGpsResetTime() {
    lastGpsResetTime = Util.getCurrentTimeWithGpsOffset();
  }
}
