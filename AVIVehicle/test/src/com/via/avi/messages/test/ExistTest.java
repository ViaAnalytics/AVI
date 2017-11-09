package com.via.avi.messages.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import com.via.avi.messages.Exist;
import com.via.avi.messages.Exist.UninitializedExistException;

import android.location.Location;
import junit.framework.TestCase;

@RunWith(RobolectricTestRunner.class)
public class ExistTest extends TestCase {
  private Location buildLocation() {
    Location loc = new Location("testProvider");
    loc.setLatitude(50.0);
    loc.setLongitude(0.0);
    loc.setTime(1395000000000L);
    return loc;
  }

  @Test
  public void testUninitializedByteMessage() {
    Exist exist = new Exist();
    try {
      exist.getByteMessage();
      fail("Expected UninitializedExistException to be thrown");
    } catch (UninitializedExistException e) {
    }
  }

  @Test
  public void testInitialization() {
    Exist exist = new Exist();
    assertEquals(false, exist.initialized());

    exist.setDeviceId("testDevice");
    assertEquals(false, exist.initialized());

    exist.setTime(buildLocation().getTime());
    assertEquals(true, exist.initialized());

    @SuppressWarnings("unused")
    byte[] msg;
    try {
      msg = exist.getByteMessage();
    } catch (UninitializedExistException e) {
      fail("Expected byte message to be initialized");
    }

  }
}

