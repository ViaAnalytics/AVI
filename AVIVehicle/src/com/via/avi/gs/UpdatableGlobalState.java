package com.via.avi.gs;

public class UpdatableGlobalState extends UpdatableGlobalStateCopy {

  // thread-safe class loader singleton pattern

  private UpdatableGlobalState() {
    super();
  }

  private static class Loader {
    public static final UpdatableGlobalState INSTANCE = new UpdatableGlobalState();
  }

  public static UpdatableGlobalState getInstance() {
    return Loader.INSTANCE;
  }

  public UpdatableGlobalStateCopy clone() {
    UpdatableGlobalStateCopy copy = new UpdatableGlobalStateCopy();
    copy.setDeviceState(this.getDeviceStateCopy());
    return copy;
  }
}
