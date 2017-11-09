package com.via.avi.location.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import android.location.Location;

import com.via.avi.config.ConfigValues;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.location.LocationHandler;

import junit.framework.TestCase;

@RunWith(RobolectricTestRunner.class)
public class LocationHandlerTest extends TestCase {
  LocationHandler lh;

  UpdatableGlobalState gs;
  ConfigValues cv;

  @Before
  public void setUp() {
    gs = UpdatableGlobalState.getInstance();
    cv = Mockito.mock(ConfigValues.class);
    lh = new LocationHandler(gs);
  }

  @Test
  public void testUpdateGlobalLastGpsTime() {
    long currTime = System.currentTimeMillis();
    Location l1 = buildLocation(currTime);
    lh.onLocationChangedWrap(l1);
    assertEquals(currTime, gs.getDeviceState().getLastGpsTime());

    currTime += 1000l;
    Location l2 = buildLocation(currTime);
    lh.onLocationChangedWrap(l2);
    assertEquals(currTime, gs.getDeviceState().getLastGpsTime());
  }

  private Location buildLocation(Long time) {
    Location l = new Location("fakeProvider");
    l.setLatitude(39.5); l.setLongitude(-93.5);
    l.setAccuracy(15); l.setBearing(65); l.setSpeed(3.5f);
    l.setTime(time);
    return l;
  }

  @After
  public void tearDown() {
    gs.clear();
  }
}
