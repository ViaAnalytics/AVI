package com.via.avi.config;

public class MissingCVProperty extends Exception {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  public MissingCVProperty(String missingKey) {
    super("Missing config property " + missingKey);
  }
}
