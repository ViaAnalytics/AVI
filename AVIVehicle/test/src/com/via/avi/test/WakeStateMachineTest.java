package com.via.avi.test;

import java.util.Properties;

import junit.framework.TestCase;

import org.robolectric.RobolectricTestRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.via.avi.WakeStateMachine;
import com.via.avi.WakeStateMachine.WakeAction;
import com.via.avi.WakeStateMachine.WakeState;
import com.via.avi.config.ConfigValues;
import com.via.avi.config.MissingCVProperty;
import com.via.avi.config.ConfigValues.CVKey;
import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.gs.UpdatableGlobalStateCopy;
import com.via.avi.utils.Util;

import android.os.BatteryManager;

@RunWith(RobolectricTestRunner.class)
public class WakeStateMachineTest extends TestCase {
  WakeStateMachine wakeStateMachine;
  ConfigValues cv;
  String timeZone = "Europe/Amsterdam";
  // 2016/09/01, 00:00 Amsterdam time
  long targetTime = 1472680800000l;
  
  private ConfigValues buildDefaultCv() {
    Properties prop = new Properties();
    prop.setProperty(CVKey.AGENCY.getKey(), "fakeagency");
    prop.setProperty(CVKey.TIME_ZONE.getKey(), "America/Los_Angeles");
    prop.setProperty(CVKey.PUSH_LINK_API_KEY.getKey(), "fake_key");
    try {
      return new ConfigValues(prop);
    } catch (MissingCVProperty e) {
      System.out.println("Shouldn't happen!");
    }
    return null;
  }

  @Before
  public void setUp() {
    //    ShadowLog.stream = System.out;
    Util.setGPSTimeOffset(System.currentTimeMillis() - targetTime);
    cv = buildDefaultCv();
  }

  @After
  public void tearDown() {
    wakeStateMachine = null;
    Util.setGPSTimeOffset(0l);
  }

  @Test
  public void initialize() {
    // start asleep
    wakeStateMachine = new WakeStateMachine(cv, timeZone);
    assertEquals(WakeState.UNKNOWN,
        wakeStateMachine.getWakeState());
  }

  @Test
  public void goToSleep() {
    // initialize with low battery -- ensure we stay asleep
    int plug = 0;
    int status = BatteryManager.BATTERY_STATUS_DISCHARGING;
    int scale = 100;
    int level0 = (int) (cv.DischargingBatteryThreshold()*scale - 5);

    UpdatableGlobalStateCopy gS = UpdatableGlobalState.getInstance().clone();
    DeviceState devState = gS.getDeviceState();
    devState.setBatteryLevel(level0);
    devState.setBatteryPlugStatus(plug);
    devState.setBatteryChargingStatus(status);
    devState.setBatteryScale(scale);

    wakeStateMachine = new WakeStateMachine(cv, timeZone);
    WakeAction action = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.UNKNOWN_TO_SLEEP, action);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC,
        wakeStateMachine.getWakeState());

    WakeAction action2 = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.DISABLE_WAKE_TASKS, action2);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS,
        wakeStateMachine.getWakeState());

    WakeAction action3 = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.ENABLE_AIRPLANE_MODE, action3);
    assertEquals(WakeState.ASLEEP,
        wakeStateMachine.getWakeState());
  }

  @Test
  public void wakeUp() {
    int plug = BatteryManager.BATTERY_PLUGGED_AC;
    int status = BatteryManager.BATTERY_STATUS_CHARGING;
    int scale = 100;
    int level0 = (int) (1.*scale);

    UpdatableGlobalStateCopy gS = UpdatableGlobalState.getInstance().clone();
    DeviceState devState = gS.getDeviceState();
    devState.setBatteryLevel(level0);
    devState.setBatteryPlugStatus(plug);
    devState.setBatteryChargingStatus(status);
    devState.setBatteryScale(scale);

    wakeStateMachine = new WakeStateMachine(cv, timeZone);
    WakeAction action = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.UNKNOWN_TO_AWAKE, action);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS,
        wakeStateMachine.getWakeState());

    WakeAction action2 = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.ENABLE_WAKE_TASKS, action2);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC,
        wakeStateMachine.getWakeState());

    WakeAction action3 = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.ENABLE_LOCATIONS, action3);
    assertEquals(WakeState.AWAKE,
        wakeStateMachine.getWakeState());
  }

  @Test
  public void wakeUpThenSleep() {
    int level0 = (int) (cv.DischargingBatteryThreshold()*100) + 1;
    int plug = 0;
    int status = BatteryManager.BATTERY_STATUS_DISCHARGING;
    int scale = 100;

    UpdatableGlobalStateCopy gS = UpdatableGlobalState.getInstance().clone();
    DeviceState devState = gS.getDeviceState();
    devState.setBatteryLevel(level0);
    devState.setBatteryPlugStatus(plug);
    devState.setBatteryChargingStatus(status);
    devState.setBatteryScale(scale);

    wakeStateMachine = new WakeStateMachine(cv, timeZone);
    WakeAction action = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.UNKNOWN_TO_AWAKE, action);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS,
        wakeStateMachine.getWakeState());

    WakeAction action2 = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.ENABLE_WAKE_TASKS, action2);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC,
        wakeStateMachine.getWakeState());

    WakeAction action3 = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.ENABLE_LOCATIONS, action3);
    assertEquals(WakeState.AWAKE,
        wakeStateMachine.getWakeState());

    // pass discharging threshold
    devState.setBatteryLevel(level0 - 2);
    devState.setBatteryPlugStatus(plug);
    devState.setBatteryChargingStatus(status);
    devState.setBatteryScale(scale);

    action = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.DISABLE_LOCATIONS, action);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC,
        wakeStateMachine.getWakeState());

    action = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.DISABLE_WAKE_TASKS, action);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS,
        wakeStateMachine.getWakeState());

    action = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.ENABLE_AIRPLANE_MODE, action);
    assertEquals(WakeState.ASLEEP,
        wakeStateMachine.getWakeState());
  }

  @Test
  public void wakeUpThenForcedSleep() {
    int plug = BatteryManager.BATTERY_PLUGGED_AC;
    int status = BatteryManager.BATTERY_STATUS_CHARGING;
    int scale = 100;
    int level0 = (int) (1.*scale);

    UpdatableGlobalStateCopy gS = UpdatableGlobalState.getInstance().clone();
    DeviceState devState = gS.getDeviceState();
    devState.setBatteryLevel(level0);
    devState.setBatteryPlugStatus(plug);
    devState.setBatteryChargingStatus(status);
    devState.setBatteryScale(scale);

    wakeStateMachine = new WakeStateMachine(cv, timeZone);
    WakeAction action = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.UNKNOWN_TO_AWAKE, action);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC_WAKE_TASKS,
        wakeStateMachine.getWakeState());

    WakeAction action2 = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.ENABLE_WAKE_TASKS, action2);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC,
        wakeStateMachine.getWakeState());

    WakeAction action3 = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.ENABLE_LOCATIONS, action3);
    assertEquals(WakeState.AWAKE,
        wakeStateMachine.getWakeState());

    // now, set time to a period during forced sleep window
    long newTargetTime = targetTime + cv.ForcedSleepStart();
    Util.setGPSTimeOffset(System.currentTimeMillis() - newTargetTime);

    action = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.DISABLE_LOCATIONS, action);
    assertEquals(WakeState.INTERMEDIATE_WITHOUT_LOC,
        wakeStateMachine.getWakeState());

    // now, set time to a period just after forced sleep window
    newTargetTime = targetTime + cv.ForcedSleepEnd() + 1;
    Util.setGPSTimeOffset(System.currentTimeMillis() - newTargetTime);

    action = wakeStateMachine.stateMachineTransition(gS);

    assertEquals(WakeAction.ENABLE_LOCATIONS, action);
    assertEquals(WakeState.AWAKE,
        wakeStateMachine.getWakeState());
  }
}
