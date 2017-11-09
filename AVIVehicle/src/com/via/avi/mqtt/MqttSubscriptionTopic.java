package com.via.avi.mqtt;

public class MqttSubscriptionTopic {

  public class MqttSubscriptionTopicException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public MqttSubscriptionTopicException(String message) {
      super(message);
    }
  }

  private String[] elements = null;

  public MqttSubscriptionTopic(String topic) throws MqttSubscriptionTopicException {
    this(parseElementsFromTopicString(topic));
  }

  public MqttSubscriptionTopic(String[] elements) throws MqttSubscriptionTopicException {

    if (elements == null) {
      MqttSubscriptionTopicException e = 
          new MqttSubscriptionTopicException("List of topic elements null.");
      throw e;
    }

    if (elements.length == 0) {
      MqttSubscriptionTopicException e = 
          new MqttSubscriptionTopicException("No topic elements.");
      throw e;
    }

    boolean good = true;
    for ( String el : elements ) {
      if (!goodElement(el)) {
        good = false;
        break;
      }
    }

    if (good) {
      for (int i=0; i<elements.length-1; i++) {
        if (elements[i].equals("#")) {
          good = false;
          break;
        }
      }
    }

    if (!good){
      MqttSubscriptionTopicException e = 
          new MqttSubscriptionTopicException("Bad element in topic list: "
              + elements.toString());
      throw e;
    } else {
      this.elements = elements;
    } 
  }

  public String toString() {
    String topic = elements[0];
    for (int i=1; i<this.getLength(); i++) {
      topic += "/" + elements[i];
    }
    return topic;
  }

  private boolean goodElement(String el) {
    if (el == null || el.contains("/") || el.equals("")) {
      return false;
    }
    return true;
  }

  public String[] getElements() {
    return elements;
  }

  public int getLength() {
    return elements.length;
  }

  public static String[] parseElementsFromTopicString(String topic) {
    return topic.split("/");
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final MqttSubscriptionTopic other = (MqttSubscriptionTopic) obj;
    if (!this.toString().equals(other.toString())) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = 85;
    hash = 23*hash + (this.toString() != null ? this.toString().hashCode() : 0);
    return hash;
  }
}
