package com.via.avi.mqtt.test;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import com.via.avi.mqtt.MqttSubscriptionTopic;
import com.via.avi.mqtt.MqttSubscriptionTopic.MqttSubscriptionTopicException;


@RunWith(RobolectricTestRunner.class)
public class MqttSubscriptionTopicTest extends TestCase {
  String[] tsEls = new String[] {
      "el1",
      "el2",
      "el3",
      "el4"
  };
  String ts = "el1/el2/el3/el4";
  String[] tsSingleWildEls = new String[] {
      "el1",
      "el2",
      "+",
      "el4"
  };
  String tsSingleWild = "el1/el2/+/el4";
  String[] tsEndWildEls = new String[] {
      "el1",
      "el2",
      "el3",
      "#"
  };
  String tsEndWild = "el1/el2/el3/#";
  String[] tsBadSlashEls = new String[] {
      "el1",
      "el2",
      "/",
      "el4"
  };
  String[] tsBadNullEls = new String[] {
      "el1",
      "el2",
      null,
      "el4"
  };
  String[] tsBadEmptyEls = new String[] {
      "",
      "el2",
      "el3",
      "el4"
  };
  String tsBadEmpty = "/el2/el3/el4";
  String[] tsBadEndWildEls = new String[] {
      "el1",
      "el2",
      "#",
      "el4"
  };
  String tsBadEndWild = "el1/el2/#/el4";
  MqttSubscriptionTopic t;

  @Before
  public void setUp() {
  }

  @After
  public void tearDown() {
  }

  @Test
  public void testGood() {
    try {
      t = new MqttSubscriptionTopic(tsEls);
    } catch (MqttSubscriptionTopicException e) {
      fail("Good topic.");
    }
  }

  @Test
  public void testGoodSingleWild() {
    try {
      t = new MqttSubscriptionTopic(tsSingleWildEls);
    } catch (MqttSubscriptionTopicException e) {
      fail("Good topic.");
    }
  }

  @Test
  public void testGoodEndWild() {
    try {
      t = new MqttSubscriptionTopic(tsEndWildEls);
    } catch (MqttSubscriptionTopicException e) {
      fail("Good topic.");
    }
  }

  @Test
  public void testBadEndWild() {
    try {
      t = new MqttSubscriptionTopic(tsBadEndWildEls);
      fail("Should fail on '#' topic element in middle of topic.");
    } catch (MqttSubscriptionTopicException e) {
    }
  }

  @Test
  public void testBadSlash() {
    try {
      t = new MqttSubscriptionTopic(tsBadSlashEls);
      fail("Should fail on '/' topic element.");
    } catch (MqttSubscriptionTopicException e) {
    }
  }

  @Test
  public void testBadNull() {
    try {
      t = new MqttSubscriptionTopic(tsBadNullEls);
      fail("Should fail on null topic element.");
    } catch (MqttSubscriptionTopicException e) {
    }
  }

  @Test
  public void testBadEmpty() {
    try {
      t = new MqttSubscriptionTopic(tsBadEmptyEls);
      fail("Should fail on '' topic element.");
    } catch (MqttSubscriptionTopicException e) {
    }
  }

  @Test
  public void testGoodString() {
    try {
      t = new MqttSubscriptionTopic(ts);
    } catch (MqttSubscriptionTopicException e) {
      fail("Good topic.");
    }
  }

  @Test
  public void testGoodSingleWildString() {
    try {
      t = new MqttSubscriptionTopic(tsSingleWild);
    } catch (MqttSubscriptionTopicException e) {
      fail("Good topic.");
    }
  }

  @Test
  public void testGoodEndWildString() {
    try {
      t = new MqttSubscriptionTopic(tsEndWild);
    } catch (MqttSubscriptionTopicException e) {
      fail("Good topic.");
    }
  }

  @Test
  public void testBadEndWildString() {
    try {
      t = new MqttSubscriptionTopic(tsBadEndWild);
      fail("Should fail on '#' topic element in middle of topic.");
    } catch (MqttSubscriptionTopicException e) {
    }
  }

  @Test
  public void testBadEmptyString() {
    try {
      t = new MqttSubscriptionTopic(tsBadEmpty);
      fail("Should fail on '' topic element.");
    } catch (MqttSubscriptionTopicException e) {
    }
  }
}
