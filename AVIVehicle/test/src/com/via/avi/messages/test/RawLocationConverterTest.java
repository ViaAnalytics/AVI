package com.via.avi.messages.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.location.Location;

import com.via.avi.messages.RawLocation;
import com.via.avi.messages.RawLocationConverter;
import com.via.avi.messages.AviMessages.RawLocationMessage;
import com.via.avi.messages.RawLocationConverter.UninitializedRawLocationException;

import junit.framework.TestCase;

@RunWith(RobolectricTestRunner.class)
public class RawLocationConverterTest extends TestCase {
  private static double lat = 50.0;
  private static double lon = 0.0;
  private static long time = 1395000000000L;
  private static float acc = 30.2f;
  private static float bear = 185.f;
  private static float speed = 12.3f;
  private static String deviceId = "fakeDevice";

  private static Location buildLocation() {
    Location l = new Location("fake_provider");
    l.setLatitude(lat);
    l.setLongitude(lon);
    l.setTime(time);
    l.setAccuracy(acc);
    l.setBearing(bear);
    l.setSpeed(speed);
    return l;
  }

  public static RawLocation buildRawLocation() {
    RawLocation rl = new RawLocation();
    rl.setDeviceId(deviceId);
    rl.setTime(time);
    rl.setLocation(buildLocation());
    return rl;
  }

  public static byte[] buildRawLocationByteArray() {
    RawLocationMessage.Builder bldr =
        RawLocationMessage.newBuilder();
    bldr.setAccuracy(acc).setBearing(bear).setDeviceId(deviceId)
    .setLatitude(lat).setLongitude(lon).setSpeed(speed).setTs(time);
    return bldr.build().toByteArray();
  }

  @Test
  public void testUninitializedByteMessage() {
    RawLocation rawLoc = new RawLocation();
    RawLocationConverter rlc = new RawLocationConverter(rawLoc);
    try {
      rlc.getByteMessage();
      fail("Expected UninitializedRawLocationException to be thrown");
    } catch (UninitializedRawLocationException e) {
    }
  }

  @Test
  public void testInitializedByteMessage() {
    RawLocation rawLoc = buildRawLocation();
    RawLocationConverter rlc = new RawLocationConverter(rawLoc);
    byte[] msg = null;
    try {
      msg = rlc.getByteMessage();
    } catch (UninitializedRawLocationException e) {
      fail("Expected byte message to be initialized");
    }
    byte[] actMsg = buildRawLocationByteArray();
    assertEquals(actMsg.length, msg.length);
    for (int i=0; i<msg.length; i++) {
      assertEquals(actMsg[i], msg[i]);
    }
  }
}