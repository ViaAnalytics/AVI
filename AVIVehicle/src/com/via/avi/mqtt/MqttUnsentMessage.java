package com.via.avi.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttUnsentMessage {

  public class MqttUnsentMessageException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public MqttUnsentMessageException(String message) {
      super(message);
    }
  }

  private MqttMessageTopic topic;
  private MqttMessage mqttMessage;

  public MqttUnsentMessage(MqttMessageTopic topic, MqttMessage mqttMessage) 
      throws MqttUnsentMessageException {
    if (topic != null && mqttMessage != null) {
      this.setTopic(topic);
      this.setMqttMessage(mqttMessage);
    } else {
      throw new MqttUnsentMessageException("Bad topic or message");
    }
  }

  public MqttMessageTopic getTopic() {
    return topic;
  }

  public void setTopic(MqttMessageTopic topic) {
    this.topic = topic;
  }

  public MqttMessage getMqttMessage() {
    return mqttMessage;
  }

  public void setMqttMessage(MqttMessage unsentMessage) {
    this.mqttMessage = unsentMessage;
  }
}
