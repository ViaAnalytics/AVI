package com.via.avi.gs.test;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;

import android.os.BatteryManager;

@RunWith(RobolectricTestRunner.class)
public class DeviceStateTest extends TestCase {

  UpdatableGlobalState instance;

  String appVersion = "3.1";
  String deviceId = "1234567890abcdef";

  Long lastBatteryMeasurementTime = 1400000000000L - 2600;

  int batteryChargingStatus = BatteryManager.BATTERY_STATUS_CHARGING;
  int batteryLevel = 80;
  int batteryPlugStatus = BatteryManager.BATTERY_PLUGGED_AC;
  int batteryScale = 100;
  int batteryScaleCopy = 99;

  long lastGpsTime = 1395000000000L;
  long lastCommTime = lastGpsTime + 3000;

  @Before
  public void setUp() {
    instance = UpdatableGlobalState.getInstance();

    instance.getDeviceState().setAppVersion(appVersion);
    instance.getDeviceState().setDeviceId(deviceId);

    instance.getDeviceState().setBatteryChargingStatus(batteryChargingStatus);
    instance.getDeviceState().setBatteryLevel(batteryLevel);
    instance.getDeviceState().setBatteryPlugStatus(batteryPlugStatus);
    instance.getDeviceState().setBatteryScale(batteryScale);

    instance.getDeviceState().setLastGpsTime(lastGpsTime);
    instance.getDeviceState().setLastCommTime(lastCommTime);
  }

  @After
  public void tearDown() {
    instance = null;
  }

  @Test
  public final void testSetAppVersion() {
    assertEquals(appVersion, instance.getDeviceState().getAppVersion());
  }

  @Test
  public final void testSetDeviceId() {
    assertEquals(deviceId, instance.getDeviceState().getDeviceId());
  }

  @Test
  public final void testSetBatteryChargingStatus() {
    assertEquals(batteryChargingStatus, instance.getDeviceState().getBatteryChargingStatus());
  }

  @Test
  public final void testSetBatteryLevel() {
    assertEquals(batteryLevel, instance.getDeviceState().getBatteryLevel());
  }

  @Test
  public final void testSetBatteryPlugStatus() {
    assertEquals(batteryPlugStatus, instance.getDeviceState().getBatteryPlugStatus());
  }

  @Test
  public final void testSetBatteryScale() {
    assertEquals(batteryScale, instance.getDeviceState().getBatteryScale());
  }

  @Test
  public final void testDeviceStateCopyIsSafe() {
    DeviceState deviceStateCopy = instance.getDeviceStateCopy();
    deviceStateCopy.setBatteryScale(batteryScaleCopy);

    assertEquals(batteryScaleCopy, 
        deviceStateCopy.getBatteryScale());
    assertEquals(batteryScale, 
        instance.getDeviceState().getBatteryScale());
  }

  @Test
  public final void testDeviceStateCopy() {
    DeviceState deviceState = instance.getDeviceState();
    DeviceState deviceStateCopy = instance.getDeviceStateCopy();
    assertEquals(deviceState.getAppVersion(), deviceStateCopy.getAppVersion());
    assertEquals(deviceState.getBatteryChargingStatus(), deviceStateCopy.getBatteryChargingStatus());
    assertEquals(deviceState.getBatteryHealthStatus(), deviceStateCopy.getBatteryHealthStatus());
    assertEquals(deviceState.getBatteryLevel(), deviceStateCopy.getBatteryLevel());
    assertEquals(deviceState.getBatteryPlugStatus(), deviceStateCopy.getBatteryPlugStatus());
    assertEquals(deviceState.getBatteryScale(), deviceStateCopy.getBatteryScale());
    assertEquals(deviceState.getBatteryTemperature(), deviceStateCopy.getBatteryTemperature());
    assertEquals(deviceState.getDeviceId(), deviceStateCopy.getDeviceId());
    assertEquals(deviceState.getLastCommTime(), deviceStateCopy.getLastCommTime());
    assertEquals(deviceState.getLastGpsTime(), deviceStateCopy.getLastGpsTime());
  }

  @Test
  public final void testSetLastGpsTime() {
    assertEquals(lastGpsTime,
        instance.getDeviceState().getLastGpsTime());
  }

  @Test
  public final void testSetLastCommTime() {
    assertEquals(lastCommTime,
        instance.getDeviceState().getLastCommTime());
  }
}
