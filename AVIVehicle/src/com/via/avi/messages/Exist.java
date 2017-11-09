package com.via.avi.messages;

import com.via.avi.messages.AviMessages.ExistMessage;
import com.via.avi.messages.AviMessages.ExistMessage.BatteryHealth;
import com.via.avi.messages.AviMessages.ExistMessage.BatteryPlugged;
import com.via.avi.messages.AviMessages.ExistMessage.BatteryStatus;

public class Exist {
  private byte[] byteMessage = null;

  private String deviceId = null;
  private Long time = null;
  private Long sentTime = null;
  private Double batteryLevel = null;
  private Integer batteryTemperature = null;
  private Integer batteryStatus = null;
  private Integer batteryPlugged = null;
  private Integer batteryHealth = null;
  private String appVersion = null;
  private Boolean communication = null;
  private Long lastGpsTime = null;
  private Double latitude = null;
  private Double longitude = null;
  private Boolean mqtt = null;

  private Integer id = null;

  public Exist() {
  }

  public Exist copy() {
    Exist ex = new Exist();
    // copy all fields except bytemessage
    ex.setAppVersion(appVersion)
    .setBatteryHealth(batteryHealth).setBatteryLevel(batteryLevel)
    .setBatteryPlugged(batteryPlugged).setBatteryStatus(batteryStatus)
    .setBatteryTemperature(batteryTemperature)
    .setCommunication(communication)
    .setDeviceId(deviceId).setId(id).setLastGpsTime(lastGpsTime)
    .setLatitude(latitude).setLongitude(longitude).setMqtt(mqtt)
    .setSentTime(sentTime).setTime(time);
    return ex;
  }

  // only to be used for database message identification
  public Integer getId() {
    return id;
  }
  public Exist setId(Integer id) {
    this.id = id;
    return this;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public Exist setDeviceId(String deviceId) {
    this.deviceId = deviceId;
    return this;
  }

  public Long getTime() {
    return time;
  }

  public Exist setTime(Long time) {
    this.time = time;
    return this;
  }

  public Long getSentTime() {
    return sentTime;
  }

  public Exist setSentTime(Long sentTime) {
    this.sentTime = sentTime;
    return this;
  }

  public Double getBatteryLevel() {
    return batteryLevel;
  }

  public Exist setBatteryLevel(Double batteryLevel) {
    this.batteryLevel = batteryLevel;
    return this;
  }

  public Integer getBatteryTemperature() {
    return batteryTemperature;
  }

  public Exist setBatteryTemperature(Integer batteryTemperature) {
    this.batteryTemperature = batteryTemperature;
    return this;
  }

  public Integer getBatteryStatus() {
    return batteryStatus;
  }

  public Exist setBatteryStatus(Integer batteryStatus) {
    this.batteryStatus = batteryStatus;
    return this;
  }

  public Integer getBatteryPlugged() {
    return batteryPlugged;
  }

  public Exist setBatteryPlugged(Integer batteryPlugged) {
    this.batteryPlugged = batteryPlugged;
    return this;
  }

  public Integer getBatteryHealth() {
    return batteryHealth;
  }

  public Exist setBatteryHealth(Integer batteryHealth) {
    this.batteryHealth = batteryHealth;
    return this;
  }

  public String getAppVersion() {
    return appVersion;
  }

  public Exist setAppVersion(String appVersion) {
    this.appVersion = appVersion;
    return this;
  }

  public Boolean getCommunication() {
    return communication;
  }

  public Exist setCommunication(Boolean communication) {
    this.communication = communication;
    return this;
  }

  public Long getLastGpsTime() {
    return lastGpsTime;
  }

  public Exist setLastGpsTime(Long lastGpsTime) {
    this.lastGpsTime = lastGpsTime;
    return this;
  }

  public Double getLatitude() {
    return latitude;
  }

  public Exist setLatitude(Double latitude) {
    this.latitude = latitude;
    return this;
  }

  public Double getLongitude() {
    return longitude;
  }

  public Exist setLongitude(Double longitude) {
    this.longitude = longitude;
    return this;
  }

  public Boolean getMqtt() {
    return mqtt;
  }

  public Exist setMqtt(Boolean mqtt) {
    this.mqtt = mqtt;
    return this;
  }

  public String toString() {
    String returnString = "{\n";
    if (deviceId != null) {
      returnString += "\t" + "deviceId: " + deviceId + ",\n";
    }
    if (time != null) {
      returnString += "\t" + "time: " + time + ",\n";
    }
    if (sentTime != null) {
      returnString += "\t" + "sentTime: " + sentTime + ",\n";
    }
    if (batteryLevel != null) {
      returnString += "\t" + "batteryLevel: " + batteryLevel + ",\n";
    }
    if (batteryTemperature != null) {
      returnString += "\t" + "batteryTemperature: " + batteryTemperature + ",\n";
    }
    if (batteryStatus != null) {
      returnString += "\t" + "batteryStatus: " + batteryStatus + ",\n";
    }
    if (batteryPlugged != null) {
      returnString += "\t" + "batteryPlugged: " + batteryPlugged + ",\n";
    }
    if (batteryHealth != null) {
      returnString += "\t" + "batteryHealth: " + batteryHealth + ",\n";
    }
    if (appVersion != null) {
      returnString += "\t" + "appVersion: " + appVersion + ",\n";
    }
    if (communication != null) {
      returnString += "\t" + "communication: " + communication + ",\n";
    }
    if (lastGpsTime != null) {
      returnString += "\t" + "lastGpsTime: " + lastGpsTime + ",\n";
    }
    if (latitude != null) {
      returnString += "\t" + "latitude: " + latitude + ",\n";
    }
    if (longitude != null) {
      returnString += "\t" + "longitude: " + longitude + ",\n";
    }
    if (mqtt != null) {
      returnString += "\t" + "mqtt: " + mqtt + ",\n";
    }
    returnString += "}\n";

    return returnString;
  }

  public Exist setByteMessage(byte[] byteMessage) {
    this.byteMessage = byteMessage;
    return this;
  }

  public byte[] getByteMessage() throws UninitializedExistException {
    if (this.initialized() && byteMessage == null) {
      return generateExistByteMessage();
    } else if (byteMessage != null ){
      return byteMessage;
    }
    else {
      throw new UninitializedExistException();
    }
  }

  public boolean initialized() {
    return (deviceId != null && time != null);
  }

  public class UninitializedExistException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

  }

  private byte[] generateExistByteMessage() {
    ExistMessage.Builder message = ExistMessage.newBuilder()
        .setDeviceId(this.getDeviceId())
        .setTs(this.getTime());

    if (this.getSentTime() != null) {
      message.setSentTime(this.getSentTime());
    }
    if (this.getCommunication() != null) {
      message.setCommunication(this.getCommunication());
    }
    if (this.getBatteryLevel() != null) {
      message.setBatteryLevel(this.getBatteryLevel());
    }
    if (this.getBatteryTemperature() != null) {
      message.setBatteryTemperature(this.getBatteryTemperature());
    }
    if (this.getBatteryStatus() != null) {
      message.setBatteryStatus(BatteryStatus.valueOf(this.getBatteryStatus()));
    }
    if (this.getBatteryPlugged() != null) {
      message.setBatteryPlugged(BatteryPlugged.valueOf(this.getBatteryPlugged()));
    }
    if (this.getBatteryHealth() != null) {
      message.setBatteryHealth(BatteryHealth.valueOf(this.getBatteryHealth()));
    }
    if (this.getAppVersion() != null) {
      message.setAppVersion(this.getAppVersion());
    }
    if (this.getSentTime() != null) {
      message.setSentTime(this.getSentTime());
    }
    if (this.getLastGpsTime() != null) {
      message.setLastGpsTime(this.getLastGpsTime());
    }
    if (this.getLatitude() != null) {
      message.setLatitude(this.getLatitude());
    }
    if (this.getLongitude() != null) {
      message.setLongitude(this.getLongitude());
    }
    if (this.getMqtt() != null) {
      message.setMqtt(this.getMqtt());
    }
    return message.build().toByteArray();
  }

}
