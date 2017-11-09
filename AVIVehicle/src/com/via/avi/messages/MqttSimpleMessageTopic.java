package com.via.avi.messages;

import com.via.avi.mqtt.MqttMessageTopic;
import com.via.avi.mqtt.MqttMessageTopic.MqttMessageTopicException;
import com.via.avi.mqtt.MqttSubscriptionTopic.MqttSubscriptionTopicException;

public class MqttSimpleMessageTopic {
  public static enum MqttSimpleMessageType {
    EXIST("exist", 2, true),
    RAW_LOCATION("raw_location", 0, false);
    private String topicElement;
    private int qos;
    private boolean retained;
    private MqttSimpleMessageType(String topicElement, int qos, 
        boolean retained) { 
      this.topicElement = topicElement; 
      this.qos = qos;
      this.retained = retained;
    }
    public String getTopicElement() { return topicElement; }
    public int getQos() { return qos; }
    public boolean getRetained() { return retained; }
  }
  private MqttMessageTopic topic = null;

  private MqttSimpleMessageType mType;

  public MqttSimpleMessageTopic(String agency, String identifier,
      MqttSimpleMessageType mType) 
          throws MqttSubscriptionTopicException, MqttMessageTopicException {
    String[] elements = new String[4];
    this.mType = mType;
    elements[0] = agency;
    elements[1] = "pb";
    elements[2] = identifier;
    elements[3] = mType.getTopicElement();
    topic = new MqttMessageTopic(elements);
  }

  public MqttMessageTopic getTopic() {
    return topic;
  }

  public MqttSimpleMessageType getType() {
    return mType;
  }
}