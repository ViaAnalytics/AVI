package com.via.avi.utils.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import com.google.protobuf.InvalidProtocolBufferException;
import com.via.avi.messages.Exist;
import com.via.avi.messages.AviMessages.ExistMessage;
import com.via.avi.messages.Exist.UninitializedExistException;
import com.via.avi.utils.Util;

import junit.framework.TestCase;

@RunWith(RobolectricTestRunner.class)
public class UtilTest extends TestCase {
  Long t0;

  @Before
  public void setUp() {
    t0 = 1395000000000L;
  }

  @After
  public void tearDown() {
  }

  @Test
  public void checkExistSentTimeChange()
      throws UninitializedExistException, InvalidProtocolBufferException {
    Exist first = new Exist();
    first.setDeviceId("fakeDevice");
    first.setTime(t0);
    byte[] firstBM = first.getByteMessage();

    byte[] secondBM = Util.changeExistSentTime(firstBM, t0 + 5000L);
    ExistMessage second = ExistMessage.parseFrom(secondBM);
    assertEquals(t0 + 5000L, second.getSentTime());
  }
}
