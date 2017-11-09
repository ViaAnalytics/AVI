package com.via.avi.mqtt.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import android.content.Context;

/**
 * This Class stores static configuration variables for MQTT.
 */
public class MqttConfigValues {
  public static final String BYTE_ENCODING = "ISO-8859-1";
  public enum MCVKey {
    MQTT_HOST("mqtt_host", true),
    MQTT_PORT("mqtt_port", false),
    MAX_IN_FLIGHT("max_in_flight", false);
    private String key;
    private boolean required;
    MCVKey(String key, boolean required) {
      this.key = key; this.required = required;
    }
    public boolean isRequired() { return required; }
    public String getKey() { return key; }
  }
  private Properties prop;

  public MqttConfigValues(String assetName, Context context) throws
  IOException, MissingMCVProperty {
    prop = new Properties();
    prepareDefaults();

    InputStream i = context.getAssets().open(assetName);
    Properties inProp = new Properties();
    inProp.load(i);

    prop.putAll(inProp);

    checkRequired();
  }

  public MqttConfigValues(Properties inProp) throws MissingMCVProperty {
    prop = new Properties();
    prepareDefaults();

    prop.putAll(inProp);

    checkRequired();
  }

  private void checkRequired() throws MissingMCVProperty {
    for (MCVKey key : MCVKey.values()) {
      if (key.isRequired() && !prop.containsKey(key.getKey())) {
        throw new MissingMCVProperty(key.getKey());
      }
    }
  }

  public String getConfigValue(MCVKey key) {
    return prop.getProperty(key.getKey());
  }

  public void setConfigValue(MCVKey key, Object value) {
    if (value == null) {
      prop.remove(key.getKey());
    } else {
      prop.setProperty(key.getKey(), String.valueOf(value));
    }
  }

  private void prepareDefaults() {
    setConfigValue(MCVKey.MQTT_PORT, 1883);
    setConfigValue(MCVKey.MAX_IN_FLIGHT, 10);
  }

  public String Hostname() { return getConfigValue(MCVKey.MQTT_HOST); }
  public int Port() { 
    return Integer.parseInt(getConfigValue(MCVKey.MQTT_PORT));
  }
  public int MaxInFlight() { 
    return Integer.parseInt(getConfigValue(MCVKey.MAX_IN_FLIGHT));
  }
}
