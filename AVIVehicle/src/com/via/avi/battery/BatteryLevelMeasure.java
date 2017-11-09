package com.via.avi.battery;

import android.annotation.SuppressLint;

public class BatteryLevelMeasure {

  @SuppressLint("DefaultLocale")
  @Override
  public String toString() {
    return String.format("(%d,%d)", this.timeMeasured, this.batteryLevel);
  }

  private Long timeMeasured;
  private Integer batteryLevel;

  public BatteryLevelMeasure(Long timeMeasured, Integer batteryLevel) {
    this.timeMeasured = timeMeasured;
    this.batteryLevel = batteryLevel;
  }

  public Long getTimeMeasured() {
    return timeMeasured;
  }

  public void setTimeMeasured(Long timeMeasured) {
    this.timeMeasured = timeMeasured;
  }

  public Integer getBatteryLevel() {
    return batteryLevel;
  }

  public void setBatteryLevel(Integer batteryLevel) {
    this.batteryLevel = batteryLevel;
  }


}
