package com.via.avi;

import com.via.avi.gs.UpdatableGlobalStateCopy;

public interface AviInterface {

  // Activities to undertake upon connectivity changes
  public void onDisconnect();
  public void onConnect();

  // Send an exist message
  public void sendExistMessage(UpdatableGlobalStateCopy globalState);
}
