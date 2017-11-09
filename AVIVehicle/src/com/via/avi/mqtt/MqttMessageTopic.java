package com.via.avi.mqtt;

import java.util.Arrays;

public class MqttMessageTopic extends MqttSubscriptionTopic {
  public class MqttMessageTopicException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public MqttMessageTopicException(String message) {
      super(message);
    }
  }

  public MqttMessageTopic(String topic) throws MqttSubscriptionTopicException, MqttMessageTopicException {
    this(parseElementsFromTopicString(topic));
  }

  public MqttMessageTopic(MqttSubscriptionTopic subTopic) throws MqttSubscriptionTopicException, MqttMessageTopicException {
    this(subTopic.getElements());
  }

  public MqttMessageTopic(String[] elements) throws MqttSubscriptionTopicException, MqttMessageTopicException {
    super(elements);
    for (int i=0; i<elements.length; i++) {
      if (elements[i].equals("#") || elements[i].equals("+")) {
        throw new MqttMessageTopicException("Wildcard not allowed in message topic.");
      }
    }
  }

  public boolean matchesSubscriptionTopic(MqttSubscriptionTopic subTopic) {
    // First, handle trailing # wildcard
    String[] msgEls = this.getElements().clone();
    String[] subEls = subTopic.getElements().clone();
    if (subEls.length > msgEls.length) {
      // only subTopic can have wildcards, so message topic can't be 
      // longer than subscribe topic and still match
      return false;
    } else if (msgEls.length > subEls.length) {
      // can only match if there is a trailing # wildcard on subEls
      if (subEls[subEls.length-1].equals("#")) {
        if (subEls.length == 1) {
          // single # wildcard -- matches everything
          return true;
        } else {
          // strip trailing # wildcard and anything at that level or deeper
          subEls = Arrays.copyOfRange(subEls, 0, subEls.length-1);
          msgEls = Arrays.copyOfRange(msgEls, 0, subEls.length-1);
        }
      } else {
        // unequal lengths with no trailing wildcard
        return false;
      }
    }

    // At this point, the elements are guaranteed to be the same length
    for (int i = 0; i < msgEls.length; i++) {
      String msgEl = msgEls[i];
      String subEl = subEls[i];
      if (!msgEl.equals(subEl) && !subEl.equals("+")) {
        // found an element that doesn't match, with no wildcards involved
        return false;
      }
    }

    return true;
  }
}
