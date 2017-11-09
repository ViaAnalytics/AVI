package com.via.avi.battery.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import com.via.avi.battery.BatteryLevelMeasure;
import com.via.avi.battery.LowBatteryLevelList;

import junit.framework.TestCase;

@RunWith(RobolectricTestRunner.class)
public class LowBatteryLevelListTest extends TestCase {
  Long lastTime = 1400000000000L;
  int maxSize = 2;
  double maxDischargeRate = -15.0/(24*60*60*1000);
  Long minDischargeTime = 8*60*60*1000l;
  LowBatteryLevelList lblList = new LowBatteryLevelList(maxSize,
      maxDischargeRate, minDischargeTime);
  BatteryLevelMeasure lowBatteryLevelMeasure = new BatteryLevelMeasure(lastTime,79);
  BatteryLevelMeasure lowBatteryLevelMeasure2 = new BatteryLevelMeasure(lastTime,69);
  BatteryLevelMeasure lowBatteryLevelMeasure3 = new BatteryLevelMeasure(lastTime,59);

  @Test
  public final void testAddBatteryLevelWhileLowBattery(){
    lblList.addBatteryLevel(lowBatteryLevelMeasure);
    assertEquals(1,lblList.size());
    lblList.addBatteryLevel(lowBatteryLevelMeasure2);
    assertEquals(2,lblList.size());
    // list size is still 2 because maximum list size is 2
    lblList.addBatteryLevel(lowBatteryLevelMeasure3);
    assertEquals(2,lblList.size());
    assertEquals(lowBatteryLevelMeasure2,lblList.get(0));
  }

  @Test
  public final void testAddTwoIdenticalBatteryLevels() {
    lblList.addBatteryLevel(lowBatteryLevelMeasure);
    assertEquals(1,lblList.size());
    lblList.addBatteryLevel(lowBatteryLevelMeasure);
    assertEquals(1,lblList.size());
  }
}
