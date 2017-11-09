package com.via.avi.mqtt.config;

public class MissingMCVProperty extends Exception {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  public MissingMCVProperty(String missingKey) {
    super("Missing mqtt config property " + missingKey);
  }

}
