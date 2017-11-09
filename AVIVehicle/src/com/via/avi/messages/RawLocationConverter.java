package com.via.avi.messages;

import com.via.avi.messages.AviMessages.RawLocationMessage;

import android.location.Location;

public class RawLocationConverter {
  private RawLocation rl;

  public RawLocationConverter(RawLocation rl) {
    this.rl = rl;
  }

  public class UninitializedRawLocationException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
  }

  public byte[] getByteMessage() throws UninitializedRawLocationException {
    if (rl.initialized()) {
      return generateByteMessage();
    } else {
      throw new UninitializedRawLocationException();
    }
  }

  private byte[] generateByteMessage() {
    RawLocationMessage.Builder message =
        RawLocationMessage.newBuilder();

    if (rl.getDeviceId() != null) {
      message.setDeviceId(rl.getDeviceId());
    }
    if (rl.getTime() != null) {
      message.setTs(rl.getTime());
    }
    if (rl.getLocation() != null) {
      Location location = rl.getLocation();
      message.setLatitude(location.getLatitude())
      .setLongitude(location.getLongitude())
      .setSpeed(location.getSpeed())
      .setBearing(location.getBearing())
      .setAccuracy(location.getAccuracy());
    }

    return message.build().toByteArray();
  }
}
