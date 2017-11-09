package com.via.avi.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.TimeZone;

import android.content.Context;

public class ConfigValues {
  public enum CVKey {
    AGENCY("agency", true),
    TIME_ZONE("time_zone", true),
    PUSH_LINK_API_KEY("push_link_api_key", false),
    MAIN_LOOP_SLEEP("main_loop_sleep", false),
    WAKE_CADENCE("wake_cadence", false),
    FORCED_SLEEP_START("forced_sleep_start", false),
    FORCED_SLEEP_END("forced_sleep_end", false),
    CHARGING_BATTERY_THRESHOLD("charging_battery_threshold", false),
    DISCHARGING_BATTERY_THRESHOLD("discharging_battery_threshold", false),
    LOW_BATTERY_DISCHARGING_RATE_LIMIT(
        "low_battery_discharging_rate_limit", false),
    LOW_BATTERY_DISCHARGING_RATE_TIME_THRESHOLD(
        "low_battery_discharging_rate_time_threshold", false),
    DISCHARGING_RATE_MAX_MEASUREMENTS(
        "discharging_rate_max_measurements", false),
    DISCHARGING_BATTERY_SHUTDOWN_THRESHOLD(
        "discharging_battery_shutdown_threshold", false),
    MAX_CONNECTIVITY_AGE("max_connectivity_age", false),
    CONNECTIVITY_REBOOT_AGE("connectivity_reboot_age", false),
    GPS_REQUEST_MIN_TIME("gps_request_min_time", false),
    GPS_REQUEST_MAX_DIST("gps_request_max_dist", false),
    MAX_GPS_AGE("max_gps_age", false),
    INTERMEDIATE_GPS_AGE("intermediate_gps_age", false),
    MAX_STORE_LOCATIONS_AGE("max_store_locations_age", false),
    GPS_REBOOT_AGE("gps_reboot_age", false),
    FILES_DIRECTORY_NAME("files_directory_name", false),
    OLD_FILES_CHECK_CADENCE("old_files_check_cadence", false),
    MAX_FILE_AGE("max_file_age", false),
    SAFETY_NET_CHECK_CADENCE("safety_net_check_cadence", false),
    FOREGROUND_WAKE_CHECK_CADENCE("foreground_wake_check_cadence", false),
    FOREGROUND_SLEEP_CHECK_CADENCE("foreground_sleep_check_cadence", false),
    SCREEN_ORIENTATION("screen_orientation", false),
    SLEEP_SCREEN_TIMEOUT_MILLIS("sleep_screen_timeout_millis", false),
    RAW_LOCATION_MESSAGE_BUFFER_SIZE(
        "raw_location_message_buffer_size", false),
    SPEED_THRESHOLD("speed_threshold", false),
    EXIST_MESSAGE_BUFFER_SIZE("exist_message_buffer_size", false),
    MESSAGE_BUFFER_CLEARING_CADENCE("message_buffer_clearing_cadence", false),
    RAW_LOCATION_SENDING_CADENCE("raw_location_sending_cadence", false),
    EXIST_MESSAGE_CADENCE("exist_message_cadence", false),
    MESSAGES_PER_SEND("messages_per_send", false);
    
    private String key;
    private boolean required;
    CVKey(String key, boolean required) {
      this.key = key; this.required = required;
    }
    public boolean isRequired() { return required; }
    public String getKey() { return key; }
  }
  
  public enum Orientation {
    LANDSCAPE("landscape", 0),
    PORTRAIT("portrait", 1),
    REVERSE_LANDSCAPE("reverse_landscape", 8),
    REVERSE_PORTRAIT("reverse_portrait", 9);
    
    private String key;
    private int value;
    Orientation(String key, int value) {
      this.key = key; this.value = value;
    }
    public String getKey() { return key; }
    public int getValue() { return value; }
    public static Integer getValueForKey(String key) {
      for (Orientation o : Orientation.values()) {
        if (o.getKey().equals(key)) {
          return o.getValue();
        }
      }
      return null;
    }
  }
  
  private Properties prop;
  
  public ConfigValues(String assetName, Context context) throws
  MissingCVProperty, IOException {
    prop = new Properties();
    prepareDefaults();
    
    InputStream i = context.getAssets().open(assetName);
    Properties inProp = new Properties();
    inProp.load(i);
    
    prop.putAll(inProp);
    
    checkRequired();
  }
  
  public ConfigValues(Properties inProp) throws 
  MissingCVProperty {
    prop = new Properties();
    prepareDefaults();
    
    prop.putAll(inProp);
    
    checkRequired();
  }
  
  private void checkRequired() throws MissingCVProperty {
    for (CVKey key : CVKey.values()) {
      if (key.isRequired() && !prop.containsKey(key.getKey())) {
        throw new MissingCVProperty(key.getKey());
      }
    }
  }
  
  public String getConfigValue(CVKey key) {
    return prop.getProperty(key.getKey());
  }
  
  public void setConfigValue(CVKey key, Object value) {
    if (value == null) {
      prop.remove(key.getKey());
    } else {
      prop.setProperty(key.getKey(), String.valueOf(value));
    }
  }
  
  private void prepareDefaults() {
    // --------- device management -----------
    setConfigValue(CVKey.MAIN_LOOP_SLEEP, "1000");
    setConfigValue(CVKey.PUSH_LINK_API_KEY, null);
    
    // wake state
    setConfigValue(CVKey.WAKE_CADENCE, "30000"); //30 seconds
    setConfigValue(CVKey.FORCED_SLEEP_START, "10800000"); // 03:00
    setConfigValue(CVKey.FORCED_SLEEP_END, "11400000"); // 03:10
    
    // battery
    setConfigValue(CVKey.CHARGING_BATTERY_THRESHOLD, "0.25");
    setConfigValue(CVKey.DISCHARGING_BATTERY_THRESHOLD, "0.9");
    setConfigValue(CVKey.LOW_BATTERY_DISCHARGING_RATE_LIMIT,
        "-0.0000001736"); // 15 percent battery every 24 hours
    setConfigValue(CVKey.LOW_BATTERY_DISCHARGING_RATE_TIME_THRESHOLD, 
        "28800000"); // 8 hours
    setConfigValue(CVKey.DISCHARGING_RATE_MAX_MEASUREMENTS, "15");
    setConfigValue(CVKey.DISCHARGING_BATTERY_SHUTDOWN_THRESHOLD, "0.3");
    
    // connectivity
    setConfigValue(CVKey.MAX_CONNECTIVITY_AGE, "300000"); // 5 minutes
    setConfigValue(CVKey.CONNECTIVITY_REBOOT_AGE, "21600000"); // 6 hours
    
    // locations
    setConfigValue(CVKey.GPS_REQUEST_MIN_TIME, "1000"); // 1 second
    setConfigValue(CVKey.GPS_REQUEST_MAX_DIST, "0.0");
    setConfigValue(CVKey.MAX_GPS_AGE, "300000"); // 5 minutes
    setConfigValue(CVKey.INTERMEDIATE_GPS_AGE, "30000"); // 30 seconds
    setConfigValue(CVKey.MAX_STORE_LOCATIONS_AGE, "900000"); // 15 minutes
    setConfigValue(CVKey.GPS_REBOOT_AGE, "21600000"); // 6 hours
    
    // files
    setConfigValue(CVKey.FILES_DIRECTORY_NAME, "AVI");
    setConfigValue(CVKey.OLD_FILES_CHECK_CADENCE, "86400000"); // 24 hours
    setConfigValue(CVKey.MAX_FILE_AGE, "1209600000"); // 2 weeks
    
    // safety net
    setConfigValue(CVKey.SAFETY_NET_CHECK_CADENCE, "10000"); // 10 seconds
    setConfigValue(CVKey.FOREGROUND_WAKE_CHECK_CADENCE,
        "30000"); // 30 seconds
    setConfigValue(CVKey.FOREGROUND_SLEEP_CHECK_CADENCE,
        "600000"); // 10 minutes
    
    // screen
    setConfigValue(CVKey.SCREEN_ORIENTATION, "portrait");
    setConfigValue(CVKey.SLEEP_SCREEN_TIMEOUT_MILLIS, "30000"); // 30 seconds
    
    // messaging
    setConfigValue(CVKey.RAW_LOCATION_MESSAGE_BUFFER_SIZE, "25");
    setConfigValue(CVKey.EXIST_MESSAGE_BUFFER_SIZE, "20");
    setConfigValue(CVKey.MESSAGE_BUFFER_CLEARING_CADENCE,
        "5000"); // 5 seconds
    setConfigValue(CVKey.RAW_LOCATION_SENDING_CADENCE, "14950"); // 15 seconds
    setConfigValue(CVKey.SPEED_THRESHOLD, "1.4"); // 1.4 m/s
    setConfigValue(CVKey.EXIST_MESSAGE_CADENCE, "300000"); // 5 minutes
    setConfigValue(CVKey.MESSAGES_PER_SEND, "5");
  }

  // ********************** Agency specific ********************

  /** short name of agency, for various usages */
  public String Agency() { return getConfigValue(CVKey.AGENCY); }

  /** timezone of agency */
  public TimeZone AgencyTimeZone() {
    return TimeZone.getTimeZone(getConfigValue(CVKey.TIME_ZONE));
  }

  // ***************************************************

  // ********************** Device Management ********************

  /** API key for Push-Link updating */
  public String PushLinkApiKey() { return getConfigValue(CVKey.PUSH_LINK_API_KEY); }

  /** Sleep period between calls to main loop, in [milliseconds] */
  public long MainLoopSleep() {
    return Long.parseLong(getConfigValue(CVKey.MAIN_LOOP_SLEEP));
  }

  // WAKE STATE

  /** cadence to check target wake state, in [milliseconds] */
  public Long WakeCadence() {
    return Long.parseLong(getConfigValue(CVKey.WAKE_CADENCE));
  }

  /** start time (in ms after midnight) of forced sleep */
  public Long ForcedSleepStart() {
    return Long.parseLong(getConfigValue(CVKey.FORCED_SLEEP_START));
  }

  /** end time (in ms after midnight) of forced sleep */
  public Long ForcedSleepEnd() {
    return Long.parseLong(getConfigValue(CVKey.FORCED_SLEEP_END));
  }

  // BATTERY

  /** battery percentage below which we idle if charging */
  public double ChargingBatteryThreshold() {
    return Double.parseDouble(getConfigValue(CVKey.CHARGING_BATTERY_THRESHOLD));
  }

  /** battery percentage below which we idle if discharging */
  public double DischargingBatteryThreshold() {
    return Double.parseDouble(getConfigValue(
        CVKey.DISCHARGING_BATTERY_THRESHOLD));
  }

  /** battery discharging rate limit in [percent/millisecond] during ASLEEP state */
  public double LowBatteryDischargingRateLimit() {
    return Double.parseDouble(getConfigValue(
        CVKey.LOW_BATTERY_DISCHARGING_RATE_LIMIT));
  }

  /** amount of time in [millis] to wait before restarting tablet due to 
   * excessive battery discharge */
  public Long LowBatteryDischargingRateTimeThreshold() {
    return Long.parseLong(getConfigValue(CVKey.LOW_BATTERY_DISCHARGING_RATE_TIME_THRESHOLD));
  }

  /** number of discharge measurements used to determine discharge rate in LowBattery */
  public Integer DischargingRateMaxMeasurements() {
    return Integer.parseInt(getConfigValue(CVKey.DISCHARGING_RATE_MAX_MEASUREMENTS));
  }

  /** battery percentage below which we shutdown if discharging */
  public double DischargingBatteryShutdownThreshold() {
    return Double.parseDouble(getConfigValue(
        CVKey.DISCHARGING_BATTERY_SHUTDOWN_THRESHOLD));
  }

  // CONNECTIVITY

  /** Maximum age of internet connectivity before we show a message, 
   * in [milliseconds]*/
  public Long MaxConnectivityAge() {
    return Long.parseLong(getConfigValue(CVKey.MAX_CONNECTIVITY_AGE));
  }

  /** Connectivity age above which we apply the last-ditch solution --
   * rebooting the tablet. */
  public Long ConnectivityRebootAge() {
    return Long.parseLong(getConfigValue(CVKey.CONNECTIVITY_REBOOT_AGE));
  }

  // LOCATIONS

  /** Minimum frequency of GPS updates requested in [milliseconds]*/
  public Long GpsRequestMinTime() {
    return Long.parseLong(getConfigValue(CVKey.GPS_REQUEST_MIN_TIME));
  }

  /** Maximum distance of GPS updates requested in [meters]*/
  public Float GpsRequestMaxDist() {
    return Float.parseFloat(getConfigValue(CVKey.GPS_REQUEST_MAX_DIST));
  }

  /** Maximum age of GPS locations before we go to a "GPS inactive" screen, 
   * in [milliseconds]*/
  public Long MaxGpsAge() {
    return Long.parseLong(getConfigValue(CVKey.MAX_GPS_AGE));
  }

  /** Maximum age of GPS locations before we start displaying a warning icon, 
   * in [milliseconds]*/
  public Long IntermediateGpsAge() {
    return Long.parseLong(getConfigValue(CVKey.INTERMEDIATE_GPS_AGE));
  }

  /** Age above which we do not store recent locations, in [milliseconds] */
  public Long MaxStoreLocationsAge() {
    return Long.parseLong(getConfigValue(CVKey.MAX_STORE_LOCATIONS_AGE));
  }

  /** Gps age above which we apply the last-ditch solution -- rebooting the
   * tablet. */
  public Long GpsRebootAge() {
    return Long.parseLong(getConfigValue(CVKey.GPS_REBOOT_AGE));
  }

  // FILES

  /** Directory in SD card to store various logs */
  public String FilesDirectoryName() {
    return getConfigValue(CVKey.FILES_DIRECTORY_NAME);
  }

  /** Cadence for deleting old files, in [milliseconds] */
  public Long OldFilesCheckCadence() {
    return Long.parseLong(getConfigValue(CVKey.OLD_FILES_CHECK_CADENCE));
  }

  /** Age above which file is old, in [milliseconds] */
  public Long MaxFileAge() {
    return Long.parseLong(getConfigValue(CVKey.MAX_FILE_AGE));
  }

  // SAFETY NET

  /** Cadence for checking whether safety net is running, in [milliseconds] */
  public Long SafetyNetCheckCadence() {
    return Long.parseLong(getConfigValue(CVKey.SAFETY_NET_CHECK_CADENCE));
  }

  /** Cadence for checking whether AVI is on foreground during wake,
   * in [milliseconds] */
  public long ForegroundWakeCheckCadence() {
    return Long.parseLong(getConfigValue(CVKey.FOREGROUND_WAKE_CHECK_CADENCE));
  }

  /** Cadence for checking whether AVI is on foreground during sleep,
   * in [milliseconds] */
  public long ForegroundSleepCheckCadence() {
    return Long.parseLong(getConfigValue(CVKey.FOREGROUND_SLEEP_CHECK_CADENCE));
  }

  // SCREEN

  /** Returns one of the ActivityInfo.SCREEN_ORIENTATION_... values */
  public int ScreenOrientation() {
    return Orientation.getValueForKey(
        getConfigValue(CVKey.SCREEN_ORIENTATION));
  }

  /** Screen timeout when device is in sleep mode */
  public Integer SleepScreenTimeoutMillis() {
    return Integer.parseInt(
        getConfigValue(CVKey.SLEEP_SCREEN_TIMEOUT_MILLIS));
  }

  // ***************************************************

  // ********************** Messaging ********************

  // UNSENT MESSAGE BUFFER SIZES

  public int RawLocationMessageBufferSize() {
    return Integer.parseInt(
        getConfigValue(CVKey.RAW_LOCATION_MESSAGE_BUFFER_SIZE));
  }
  public int ExistMessageBufferSize() {
    return Integer.parseInt(getConfigValue(CVKey.EXIST_MESSAGE_BUFFER_SIZE));
  }

  // MESSAGE BUFFER CLEARING CADENCE CONTROL

  /** Cadence at which to clear message buffers, in [milliseconds] */
  public Long MessageBufferClearingCadence() {
    return Long.parseLong(getConfigValue(CVKey.MESSAGE_BUFFER_CLEARING_CADENCE));
  }
  /** Cadence to send raw location messages, in [milliseconds] */
  public Long RawLocationSendingCadence() {
    return Long.parseLong(getConfigValue(CVKey.RAW_LOCATION_SENDING_CADENCE));
  }
  /** Speed threshold below which speed is "low", in [m/s] */
  public Float SpeedThreshold() {
    return Float.parseFloat(getConfigValue(CVKey.SPEED_THRESHOLD));
  }
  /** Cadence to generate exist messages, in [milliseconds] */
  public Long ExistMessageCadence() {
    return Long.parseLong(getConfigValue(CVKey.EXIST_MESSAGE_CADENCE));
  }

  /** Number of messages to send per queue clearing cadence */
  public int MessagesPerSend() {
    return Integer.parseInt(getConfigValue(CVKey.MESSAGES_PER_SEND));
  }

  // ***************************************************
}
