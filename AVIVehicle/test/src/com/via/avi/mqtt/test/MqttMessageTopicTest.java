package com.via.avi.mqtt.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import com.via.avi.mqtt.MqttMessageTopic;
import com.via.avi.mqtt.MqttMessageTopic.MqttMessageTopicException;
import com.via.avi.mqtt.MqttSubscriptionTopic;
import com.via.avi.mqtt.MqttSubscriptionTopic.MqttSubscriptionTopicException;

import junit.framework.TestCase;

@RunWith(RobolectricTestRunner.class)
public class MqttMessageTopicTest extends TestCase {
  private MqttMessageTopic t1;
  String[] ts1 = new String[] {
      "el1",
      "el2",
      "el3",
      "el4"
  };
  private MqttSubscriptionTopic t1Exact;
  private String[] ts1Exact = new String[] {
      "el1",
      "el2",
      "el3",
      "el4"
  };
  private MqttSubscriptionTopic t1NonMatch;
  private String[] ts1NonMatch = new String[] {
      "el5",
      "el2",
      "el3",
      "el4"
  };
  private MqttSubscriptionTopic t1SingleWild;
  private String[] ts1SingleWild = new String[] {
      "el1",
      "el2",
      "+",
      "el4"
  };
  private MqttSubscriptionTopic t1SingleWildNonMatch;
  private String[] ts1SingleWildNonMatch = new String[] {
      "el1",
      "el2",
      "+",
      "el5"
  };
  private MqttSubscriptionTopic t1EndWild;
  private String[] ts1EndWild = new String[] {
      "el1",
      "el2",
      "#"
  };
  private MqttSubscriptionTopic t1AllWild;
  private String[] ts1AllWild = new String[] {
      "#"
  };
  private MqttSubscriptionTopic t1EndWildNonMatch;
  private String[] ts1EndWildNonMatch = new String[] {
      "el1",
      "el2",
      "el3",
      "el4",
      "#"
  };


  @Before
  public void setUp() {
    try {
      t1 = new MqttMessageTopic(ts1);
      t1Exact = new MqttSubscriptionTopic(ts1Exact);
      t1NonMatch = new MqttSubscriptionTopic(ts1NonMatch);
      t1SingleWild = new MqttSubscriptionTopic(ts1SingleWild);
      t1SingleWildNonMatch = new MqttSubscriptionTopic(ts1SingleWildNonMatch);
      t1EndWild = new MqttSubscriptionTopic(ts1EndWild);
      t1AllWild = new MqttSubscriptionTopic(ts1AllWild);
      t1EndWildNonMatch = new MqttSubscriptionTopic(ts1EndWildNonMatch);
    } catch (MqttSubscriptionTopicException e) {
      fail("Failed to initialize MqttSubscriptionTopic: " + e.toString());
    } catch (MqttMessageTopicException e) {
      fail("Failed to initialize MqttMessageTopic: " + e.toString());
    }
  }

  @After
  public void tearDown() {
    t1 = null;
    t1Exact = null;
    t1SingleWild = null;
    t1SingleWildNonMatch = null;
    t1EndWild = null;
    t1AllWild = null;
    t1EndWildNonMatch = null;
  }

  @Test
  public void testBadMessageTopicSingleWild() {
    try {
      t1 = new MqttMessageTopic(ts1SingleWild);
      fail("Should have thrown MqttMessageTopicException");
    } catch (MqttSubscriptionTopicException e) {
      fail("Fine subscription topic.");
    } catch (MqttMessageTopicException e) {
    }
  }

  @Test
  public void testBadMessageTopicEndWild() {
    try {
      t1 = new MqttMessageTopic(ts1EndWild);
      fail("Should have thrown MqttMessageTopicException");
    } catch (MqttSubscriptionTopicException e) {
      fail("Fine subscription topic.");
    } catch (MqttMessageTopicException e) {
    }
  }

  @Test
  public void testBadMessageTopicAllWild() {
    try {
      t1 = new MqttMessageTopic(ts1AllWild);
      fail("Should have thrown MqttMessageTopicException");
    } catch (MqttSubscriptionTopicException e) {
      fail("Fine subscription topic.");
    } catch (MqttMessageTopicException e) {
    }
  }

  @Test
  public void testExactMatch() {
    assertTrue(t1.matchesSubscriptionTopic(t1Exact));
  }

  @Test
  public void testNonMatch() {
    assertFalse(t1.matchesSubscriptionTopic(t1NonMatch));
  }

  @Test
  public void testSingleWild() {
    assertTrue(t1.matchesSubscriptionTopic(t1SingleWild));
  }

  @Test
  public void testSingleWildNonMatch() {
    assertFalse(t1.matchesSubscriptionTopic(t1SingleWildNonMatch));
  }

  @Test
  public void testEndWild() {
    assertTrue(t1.matchesSubscriptionTopic(t1EndWild));
  }

  @Test
  public void testAllWild() {
    assertTrue(t1.matchesSubscriptionTopic(t1AllWild));
  }

  @Test
  public void testEndWildNonMatch() {
    assertFalse(t1.matchesSubscriptionTopic(t1EndWildNonMatch));
  }

  @Test
  public void testInitializeFromMqttSubscriptionTopic() {
    try {
      t1 = new MqttMessageTopic(t1Exact);
    } catch (MqttSubscriptionTopicException e) {
      fail("Fine subscription topic.");
    } catch (MqttMessageTopicException e) {
      fail("Fine message topic");
    }
  }

  @Test
  public void testInitializeFromWildMqttSubscriptionTopic() {
    try {
      t1 = new MqttMessageTopic(t1SingleWild);
      fail("Should have choked on wildcard.");
    } catch (MqttSubscriptionTopicException e) {
      fail("Fine subscription topic.");
    } catch (MqttMessageTopicException e) {
    }
  }

  @Test
  public void testToString() {
    assertEquals("el1/el2/el3/el4", t1.toString());
  }
}
