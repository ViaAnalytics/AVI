package com.via.avi.battery;

import java.util.ArrayList;
import java.util.List;

import com.via.avi.gs.UpdatableGlobalState;

import android.util.Log;

public class LowBatteryLevelList {
  private static String TAG = "LowBatteryLevelList";
  private List<BatteryLevelMeasure> blList;
  private int maxSize;
  private double dischargeThreshold;
  Long dischargeTimeThreshold;

  public LowBatteryLevelList(int maxSize, double dischargeThreshold,
      Long dischargeTimeThreshold) {
    this.maxSize = maxSize;
    this.dischargeThreshold = dischargeThreshold;
    this.dischargeTimeThreshold = dischargeTimeThreshold;
    blList = new ArrayList<BatteryLevelMeasure>();
  }

  @Override
  public String toString() {
    if (blList.size() > 1) {
      int last = blList.size() - 1;
      return blList.size() + " battery level measures. Initial measure: " +
      blList.get(0) + ". Final measure: " + blList.get(last) + ".";
    } else if (blList.size() == 1) {
      return "Single battery level measure: " + blList.get(0) + ".";
    } else {
      return "LowBatteryLevelList empty.";
    }
  }

  public void addBatteryLevel(BatteryLevelMeasure batteryLevelMeasure) {
    synchronized (UpdatableGlobalState.class) {
      if (this.blList.size() == 0 ||
          (this.blList.get(this.blList.size() - 1).getBatteryLevel() 
              != batteryLevelMeasure.getBatteryLevel())) {
        this.blList.add(batteryLevelMeasure);
      }
    }

    // manage list size
    while (this.blList.size() > maxSize) {
      this.blList.remove(0);
    }
  }

  public boolean excessiveDischargingRate(){
    synchronized(UpdatableGlobalState.class){
      Log.d(TAG,"batteryLevels size: " + Integer.toString(blList.size()));
      boolean result = false;
      Long timeGap = 0L;
      Integer batteryLevelIncrement = 0;
      if (blList.size() > 1){
        for (int i = 0; i < blList.size() - 1; i++){
          if (blList.get(i).getBatteryLevel() > blList.get(i+1).getBatteryLevel()){
            batteryLevelIncrement += blList.get(i+1).getBatteryLevel() - blList.get(i).getBatteryLevel();
            timeGap += blList.get(i+1).getTimeMeasured() - blList.get(i).getTimeMeasured();
          }
        }
        if (timeGap > dischargeTimeThreshold) {
          double dischargeRate = (double) batteryLevelIncrement / timeGap.doubleValue();
          Log.d(TAG,"Battery discharge rate: " + Double.toString(dischargeRate));
          Log.d(TAG,"Low battery discharging rate limit: " + Double.toString(dischargeThreshold));
          if (dischargeRate < dischargeThreshold){
            result = true;
          }
        }
      }
      return result;
    }
  }

  public int size() {
    synchronized (UpdatableGlobalState.class) {
      return blList.size();
    }
  }

  public BatteryLevelMeasure get(int which) {
    synchronized (UpdatableGlobalState.class) {
      if (which < blList.size()) {
        return blList.get(which);
      } else {
        return null;
      }
    }
  }
}
