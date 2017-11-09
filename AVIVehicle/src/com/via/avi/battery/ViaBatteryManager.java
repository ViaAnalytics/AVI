package com.via.avi.battery;

import android.content.Intent;
import android.os.BatteryManager;
import android.util.Log;

import com.via.avi.DeviceManager;
import com.via.avi.AviInterface;
import com.via.avi.config.ConfigValues;
import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.utils.Util;

public class ViaBatteryManager {
  private static String TAG = "ViaBatteryManager";

  private ConfigValues cv;
  private AviInterface app;
  private DeviceManager dm;

  private LowBatteryLevelList lblList;

  public ViaBatteryManager(ConfigValues cv, AviInterface app,
      DeviceManager dm) {
    this.cv = cv;
    this.app = app;
    this.dm = dm;
    lblList = new LowBatteryLevelList(cv.DischargingRateMaxMeasurements(),
        cv.LowBatteryDischargingRateLimit(), 
        cv.LowBatteryDischargingRateTimeThreshold());
  }

  public void processBatteryIntent(Intent intent) {
    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
        BatteryManager.BATTERY_STATUS_UNKNOWN);
    int plug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
    int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
    float batteryPercentage = level / (float) scale;
    BatteryLevelMeasure batteryLevelMeasure = new BatteryLevelMeasure(
        Util.getCurrentTimeWithGpsOffset(), level);

    UpdatableGlobalState globalState = UpdatableGlobalState.getInstance();
    DeviceState devState = globalState.getDeviceState();
    boolean changed = devState.getBatteryChargingStatus() != status ||
        devState.getBatteryPlugStatus() != plug ||
        devState.getBatteryLevel() != level ||
        devState.getBatteryScale() != scale ||
        (Math.abs(devState.getBatteryTemperature() - temp) >= 10) || // change of 10 tenths of a degree C
        devState.getBatteryHealthStatus() != health;

    if (changed) {
      Log.d(TAG, "Action battery change : {Plug:  " + plug + ", health: " + health + ", temperature: " + temp + ", status: " + status + ", level: " + batteryPercentage + "}");
      // set global variables:
      devState.setBatteryChargingStatus(status);
      devState.setBatteryPlugStatus(plug);
      devState.setBatteryHealthStatus(health);
      devState.setBatteryLevel(level);
      devState.setBatteryScale(scale);
      devState.setBatteryTemperature(temp);

      // every time battery state updates, send an exist message
      app.sendExistMessage(globalState.clone());

      if (status == BatteryManager.BATTERY_STATUS_DISCHARGING && 
          (double) batteryPercentage < cv.DischargingBatteryShutdownThreshold()) {
        dm.receivedShutdownOrder();
      }

      if ((double) batteryPercentage < cv.DischargingBatteryThreshold()) {
        lblList.addBatteryLevel(batteryLevelMeasure);
        if (lblList.excessiveDischargingRate()) {
          Log.d(TAG,"Forcing app reboot due to excessive battery discharge!");
          Log.d(TAG, lblList.toString());
          dm.receivedRebootOrder();
        }
      }

      // Safe to call this again -- will be ignored if we're
      // already running.
      dm.startWakeCheckIfNotRunning();
    }
  }
}
