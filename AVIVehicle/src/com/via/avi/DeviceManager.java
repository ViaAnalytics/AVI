package com.via.avi;

public interface DeviceManager {

  // Enable GPS
  public void enableLocations();

  // Disable GPS
  public void disableLocations();

  // Enable various tasks that run while device is awake
  public void enableWakeTasks();

  // Disable various tasks that run while device is awake
  public void disableWakeTasks();

  // Ensure that wake check is running
  public void startWakeCheckIfNotRunning();

  // Enable airplane mode
  public void enableAirplaneMode();

  // Disable airplane mode
  public void disableAirplaneMode();

  // received order to reboot
  public void receivedRebootOrder();

  // received order to shut down
  public void receivedShutdownOrder();
}
