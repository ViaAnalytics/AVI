package com.via.avi.mqtt;

public abstract class AbstractMqttSubscription implements MqttSubscription {
  private int failureCount = 0;

  @Override
  public int getFailures() {
    return failureCount;
  }

  @Override
  public void incrementFailures() {
    failureCount += 1;
  }

  @Override
  public void clearFailures() {
    failureCount = 0;
  }
}
