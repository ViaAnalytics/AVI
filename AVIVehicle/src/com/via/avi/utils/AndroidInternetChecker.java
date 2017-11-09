package com.via.avi.utils;

import com.via.avi.mqtt.MqttManagerInterface;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class AndroidInternetChecker {
  Context mContext;
  ConnectivityManager mConnManager;
  MqttManagerInterface mqttManager;

  public AndroidInternetChecker(Context context) {
    mContext = context;
    mConnManager = (ConnectivityManager) context
        .getSystemService(Context.CONNECTIVITY_SERVICE);
  }

  public boolean isInternetConnected() {
    NetworkInfo n = mConnManager.getActiveNetworkInfo();
    return (n != null && n.isConnected() && n.isAvailable());
  }

  public boolean isMqttConnected() {
    return isInternetConnected() && 
        (mqttManager != null && mqttManager.isConnected());
  }

  public void setMqttManager(MqttManagerInterface mqttManager) {
    this.mqttManager = mqttManager;
  }
}
