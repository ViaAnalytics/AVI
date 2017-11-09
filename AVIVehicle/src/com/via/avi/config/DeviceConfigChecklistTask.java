package com.via.avi.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.containers.Permissions;
import com.via.avi.DeviceManager;
import com.via.avi.utils.Util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.util.Log;

@SuppressWarnings("deprecation")
public class DeviceConfigChecklistTask extends AsyncTask<Void, Void, Boolean>{
  private static String TAG = "DeviceConfigChecklistTask";

  // These refer to the battery script, which should exist in assets, and which
  // file it should be copied to. This is device-specific! 'ipod' is for 
  // the Ellipsis 7 tablets and probably other devices, 'playlpm' for Samsung
  // devices.
  private static final String[] SYSTEM_BATT_ANIM_FILES = new String [] {
    "/system/bin/playlpm",
    "/system/bin/ipod"
  };
  private static final String NEW_BATT_ANIM_SCRIPT_NAME = "batt_anim_script.sh";

  // This refers to the location of the SharedPreferences for SuperSU.
  @SuppressLint("SdCardPath")
  private static final String SUPERSU_CONFIG_FILE =
  "/data/data/eu.chainfire.supersu/shared_prefs/eu.chainfire.supersu_preferences.xml";

  // These refer to FOTAKill.apk, which should be in the device assets for
  // automatic installation.
  private static final String FOTAKILL_PATH = "/system/app/FOTAKill.apk";
  private static final String FOTAKILL_NAME = "FOTAKill.apk";

  // The following files refer to apks that should be removed for various
  // reasons. If they already don't exist, that's ok, the script checks
  // first.

  // These apks are not in /system/app and so they can be removed normally.
  private static final String[] NON_SYSTEM_APPS_TO_REMOVE = new String[] {
    "/data/app/com.quanta.pobu.apteam.help-1.apk",
    "/data/app/com.alephzain.framaroot-1.apk"
  };

  // These apks are in /system/app and so root must be used, the filesystem 
  // must be remounted, and the device must be rebooted after modification.
  private static final String[] SYSTEM_APPS_TO_REMOVE = new String[]{
    // ellipsis updaters
    "/system/app/FOTA.apk", 
    "/system/app/SystemUpdate.apk",
    "/system/app/SystemUpdate.odex",
    "/system/app/SystemUpdateAssistant.apk",
    "/system/app/SystemUpdateAssistant.odex",

    // samsung tab 2 OTA updater
    "/system/app/FotaClient.apk",
    "/system/app/FotaClient.odex",

    // ellipsis 7 bloatware
    "/system/app/BasicDreams.apk",
    "/system/app/BasicDreams.odex",
    "/system/app/Books.apk",
    "/system/app/Calculator.apk",
    "/system/app/Calculator.odex",
    "/system/app/CalendarGoogle.apk",
    "/system/app/CalendarImporter.apk",
    "/system/app/CalendarImporter.odex",
    "/system/app/CalendarProvider.apk",
    "/system/app/CalendarProvider.odex",
    "/system/app/DeskClockGoogle.apk",
    "/system/app/DownloadProviderUi.apk",
    "/system/app/DownloadProviderUi.odex",
    "/system/app/Drive.apk",
    "/system/app/Email.apk",
    "/system/app/Email.odex",
    "/system/app/Exchange2.apk",
    "/system/app/Exchange2.odex",
    "/system/app/FaceLock.apk",
    "/system/app/Flipboard-2.2.9-2194-verizon-release.apk",
    "/system/app/Galaxy4.apk",
    "/system/app/Galaxy4.odex",
    "/system/app/Gallery2.apk",
    "/system/app/Gallery2.odex",
    "/system/app/GoogleContactsSyncAdapter.apk",
    "/system/app/GoogleTTS.apk",
    "/system/app/Hangouts.apk",
    "/system/app/HoloSpiralWallpaper.apk",
    "/system/app/HoloSpiralWallpaper.odex",
    "/system/app/HTMLViewer.apk",
    "/system/app/HTMLViewer.odex",
    "/system/app/KindleForAndroid-4.4.0.71.apk",
    "/system/app/LiveWallpapers.apk",
    "/system/app/LiveWallpapers.odex",
    "/system/app/LiveWallpapersPicker.apk",
    "/system/app/LiveWallpapersPicker.odex",
    "/system/app/Magazines.apk",
    "/system/app/MagicSmokeWallpapers.apk",
    "/system/app/MagicSmokeWallpapers.odex",
    "/system/app/MediaUploader.apk",
    "/system/app/MtkVideoLiveWallpaper.apk",
    "/system/app/MtkVideoLiveWallpaper.odex",
    "/system/app/Music2.apk",
    "/system/app/MusicFX.apk",
    "/system/app/MusicFX.odex",
    "/system/app/MyVerizon_tablet_prod_650.apk",
    "/system/app/NoiseField.apk",
    "/system/app/NoiseField.odex",
    "/system/app/PartnerBookmarksProvider.apk",
    "/system/app/PartnerBookmarksProvider.odex",
    "/system/app/PhaseBeam.apk",
    "/system/app/PhaseBeam.odex",
    "/system/app/PhotoTable.apk",
    "/system/app/PhotoTable.odex",
    "/system/app/Plants_Vs_Zombies_signed_submission.apk",
    "/system/app/PlayGames.apk",
    "/system/app/PlusOne.apk",
    "/system/app/Protips.apk",
    "/system/app/Protips.odex",
    "/system/app/QciHelp.apk",
    "/system/app/QciOOBE.apk",
    "/system/app/Street.apk",
    "/system/app/TagGoogle.apk",
    "/system/app/UserDictionaryProvider.apk",
    "/system/app/UserDictionaryProvider.odex",
    "/system/app/Velvet.apk",
    "/system/app/VideoEditorGoogle.apk",
    "/system/app/Videos.apk",
    "/system/app/VideoFavorites.apk",
    "/system/app/VideoFavorites.odex",
    "/system/app/VideoPlayer.apk",
    "/system/app/VideoPlayer.odex",
    "/system/app/VisualizationWallpapers.apk",
    "/system/app/VisualizationWallpapers.odex",
    "/system/app/VoiceSearchStub.apk",
    "/system/app/VpnDialogs.apk",
    "/system/app/VpnDialogs.odex",
    "/system/app/YouTube.apk",
    "/system/app/iHeartRadio-vzw_preload-prod-T3prod-4.11.0-b17-r5cbc7be.apk",
    "/system/app/stub_zoetrope_stub.apk",
    "/system/app/talkback.apk",
    "/system/app/vzw_vnav_7.2.1.96_Tablet_rel_PROD_signed.apk",
    "/system/app/VZMessages-PROD-4.3.18-040814.apk",
    "/system/app/VZWAPNLib.apk",
    "/system/app/VZWAPNService.apk"
  };

  private Context context;
  private String tempDir;
  private DeviceManager dm;
  private ConfigValues cv;

  public DeviceConfigChecklistTask(Context context, DeviceManager dm,
      ConfigValues cv) {
    this.context = context;
    this.tempDir = context.getFilesDir().getAbsolutePath();
    this.dm = dm;
    this.cv = cv;

    RootTools.handlerEnabled = false;
  }

  @Override
  protected Boolean doInBackground(Void... params) {
    Log.i(TAG, "Starting Device Config Checklist.");

    // NOTE: enabling this task will perform a variety of device clean-up 
    // that don't require root, and should probably only be run on an actual
    // deployed device.
//    doNonRootConfigTasks();

    if (!RootTools.isAccessGiven()) {
      // without busybox and root, we can't do anything
      Log.w(TAG, "Running without root! Skipping root tasks.");
      return false;
    } else {
      // NOTE: enabling this task will perform a variety of device clean-up 
      // that DO require root (even more potentially destructive than the
      // earlier non-root tasks), and so should be run with extreme caution,
      // only after understanding everything that it does.
//      boolean restart = doRootConfigTasks();
//      return restart;
      return false;
    }
  }

  @Override
  protected void onPostExecute(Boolean restart) {
    if (restart) {
      Log.w(TAG, "Need to reboot!");
      dm.receivedRebootOrder();
    } else {
      Log.i(TAG, "Reboot unnecessary.");
    }
  }

  private void doNonRootConfigTasks() {
    Log.i(TAG, "Performing tasks that don't require root.");
    removeLockScreen();

    setScreenTimeout();

    disableSounds();
  }

  private boolean doRootConfigTasks() {
    Log.i(TAG, "Performing tasks that require root.");

    if (!Util.Debug) {
      ensureSafetyNetInstalled();
    }

    // enable only GPS (and disable network locations)
    enableGpsOnly();

    // rewrite battery script to automatically turn on device when charging
    rewriteBatteryAnimScript();

    removeNonSystemApks();

    // need to restart after modifications to system apps or SuperSU settings
    boolean superSUSettingsChanged = setSuperSUSettings();

    boolean installedFOTAKill = false;
    if (!isFOTAKillInstalled()) {
      installedFOTAKill = installFOTAKill();
    }

    boolean removedSystemApks = removeSystemApks();

    boolean needsRestart = removedSystemApks || installedFOTAKill || superSUSettingsChanged;
    return needsRestart;
  }

  // Enable gps and implicitly disable network locations (which are never
  // useful to us). Must be done as root.
  private void enableGpsOnly() {
    Log.i(TAG, "Enabling GPS, disabling network locations");
    String cmd = "settings put secure location_providers_allowed gps";
    executeSudoCmd(cmd);
  }

  private void setScreenTimeout() {
    Log.i(TAG, "Setting screen timeout to " + cv.SleepScreenTimeoutMillis()/1000 + "s.");
    android.provider.Settings.System.putInt(
        context.getContentResolver(),
        android.provider.Settings.System.SCREEN_OFF_TIMEOUT, 
        cv.SleepScreenTimeoutMillis());
  }

  private void removeLockScreen() {
    Log.i(TAG, "Attempting to remove lock screen.");
    // Does not actually disable lock screen, but it does make it so that our
    // can preempt insecure (e.g. swipe) lock screens.
    // Also won't work on devices above 4.3?
    KeyguardManager km = (KeyguardManager) context.getSystemService(Activity.KEYGUARD_SERVICE);
    KeyguardLock lock = km.newKeyguardLock(Activity.KEYGUARD_SERVICE);
    lock.reenableKeyguard();
    lock.disableKeyguard();
  }

  private void disableSounds() {
    Log.i(TAG, "Setting volume to zero.");
    AudioManager mgr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    mgr.setStreamVolume(AudioManager.STREAM_ALARM, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
    mgr.setStreamVolume(AudioManager.STREAM_DTMF, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
    mgr.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
    mgr.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
    mgr.setStreamVolume(AudioManager.STREAM_RING, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
    mgr.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
    mgr.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
  }

  private void ensureSafetyNetInstalled() {
    // expand safety net apk from assets to actual file
    String tempApkPath = tempDir + "/" + Util.SafetyNetApk;
    try {
      expandSafetyNetApk(tempApkPath);
    } catch (SafetyNetException e) {
      Log.e(TAG, "Failed to expand SafetyNet apk from assets!", e);
      return;
    }

    int targetVersion = getTargetSafetyNetVersion(tempApkPath);
    int currentVersion;
    try {
      currentVersion = installedSafetyNetVersion(Util.SafetyNetPackage);
    } catch (SafetyNetException e) {
      Log.w(TAG, "SafetyNet not previous installed. Installing.");
      String cmd = "pm install -f " + tempApkPath;
      Log.i(TAG, "installing: " + cmd);
      if (!executeSudoCmd(cmd)) {
        Log.i(TAG, "Failed to install SafetyNet!");
      } else {
        Log.i(TAG, "Successfully installed SafetyNet!");
      }

      return;
    }

    Log.i(TAG, "SafetyNet current: " + currentVersion + 
        ", target: " + targetVersion);
    if (targetVersion > currentVersion) {
      // now do the actual install
      String cmd = "pm install -f -r " + tempApkPath;
      Log.i(TAG, "installing: " + cmd);
      if (!executeSudoCmd(cmd)) {
        Log.i(TAG, "Failed to install SafetyNet!");
      } else {
        Log.i(TAG, "Successfully installed SafetyNet!");
      }
    } if (targetVersion < currentVersion) {
      String cmd = "pm uninstall " + Util.SafetyNetPackage;
      Log.i(TAG, "uninstalling old SafetyNet: " + cmd);
      if (!executeSudoCmd(cmd)) {
        Log.i(TAG, "Failed to uninstall previous SafetyNet!");
      } else {
        Log.i(TAG, "Successfully uninstalled previous SafetyNet!");
        Log.i(TAG, "Installing new SafetyNet: " + cmd);
        String cmd2 = "pm install -f " + tempApkPath;
        if (!executeSudoCmd(cmd2)) {
          Log.i(TAG, "Failed to install new SafetyNet!");
        } else {
          Log.i(TAG, "Successfully installed new SafetyNet!");
        }
      }
    }
  }

  private int installedSafetyNetVersion(String targetPackage)
      throws SafetyNetException {
    PackageManager pm = context.getPackageManager();
    try {
      PackageInfo info = pm.getPackageInfo(targetPackage,
          PackageManager.GET_META_DATA);
      return info.versionCode;
    } catch (PackageManager.NameNotFoundException e) {
      throw new SafetyNetException("SafetyNet isn't installed!", e);
    }
  }

  private void expandSafetyNetApk(String targetApkPath) 
      throws SafetyNetException {
    try {
      writeAssetToFile(Util.SafetyNetApk, targetApkPath);
    } catch (IOException e) {
      throw new SafetyNetException("Failed to expand safety net apk", e);
    }

    // change permissions to rw-r--r--
    String permCmd = "chmod 644 " + targetApkPath;
    if (!executeSudoCmd(permCmd)) {
      throw new SafetyNetException("Failed to chown SafetyNet apk");
    }
  }

  // assumes that apk has already been inflated
  private int getTargetSafetyNetVersion(String apkPath) {
    final PackageManager pm = context.getPackageManager();
    PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
    return info.versionCode;
  }

  private class SafetyNetException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public SafetyNetException(String message) {
      super(message);
    }
    public SafetyNetException(String message, Throwable throwable) {
      super(message, throwable);
    }
  }

  private void removeNonSystemApks() {
    // remove non-system apks (by backing them up to a non-apk file)
    for (String apkPath : NON_SYSTEM_APPS_TO_REMOVE) {
      if (RootTools.exists(apkPath)) {
        if (renameToBackup(apkPath, false)) {
          Log.i(TAG, apkPath + " renamed to backup.");
        }
      } else {
        Log.i(TAG, apkPath + " already doesn't exist -- not renaming.");
      }
    }
  }

  private boolean removeSystemApks() {
    boolean needsRestart = false;
    for (String apkPath : SYSTEM_APPS_TO_REMOVE) {
      if (RootTools.exists(apkPath)){
        boolean renamed = renameToBackup(apkPath, true);
        if (renamed) {
          Log.i(TAG, apkPath + " renamed to backup.");
        }
        // update needsRestart
        needsRestart = needsRestart || renamed;
      } else {
        Log.i(TAG, apkPath + " already doesn't exist -- not renaming.");
      }
    }
    return needsRestart;
  }

  private boolean rewriteBatteryAnimScript() {
    // find the appropriate battery script location for our device
    String realScriptPath = "";
    boolean found = false;
    for (String possibleScriptPath : SYSTEM_BATT_ANIM_FILES) {
      if (RootTools.exists(possibleScriptPath)) {
        Log.i(TAG, "Found battery anim file at " + possibleScriptPath);
        found = true;
        realScriptPath = possibleScriptPath;
        break;
      }
    }
    if (!found) {
      Log.w(TAG, "Didn't find battery anim file at any path! Skipping.");
      return false;
    }

    // get existing permissions
    Permissions perm = RootTools.getFilePermissionsSymlinks(realScriptPath);
    if (perm == null) {
      Log.w(TAG, "Failed to get previous battery anim script permissions!"
          + " Giving up.");
      return false;
    }

    // write battery anim script to actual file
    String tempScriptPath = tempDir + "/" + NEW_BATT_ANIM_SCRIPT_NAME;
    try {
      writeAssetToFile(NEW_BATT_ANIM_SCRIPT_NAME, tempScriptPath);
    } catch (IOException e) {
      Log.e(TAG, "Failed to expand battery file asset", e);
      return false;
    }

    // mount /system as writable
    if (!RootTools.remount("/system", "rw")) {
      Log.w(TAG, "Failed to mount /system as writable! Giving up.");
      return false;
    }

    String backupScriptPath = realScriptPath + "_orig";
    if (!RootTools.exists(backupScriptPath)) {
      // Back up original battery script before overwriting, but only if it
      // doesn't exist. If we didn't do this check, then we'd be copying over  
      // the original system version on the second and future calls.
      Log.i(TAG, "Backing up pre-existing battery anim file at path " + backupScriptPath);
      if (!RootTools.copyFile(realScriptPath, backupScriptPath, false, true)) {
        Log.w(TAG, "failed to back up pre-existing battery anim file!");
      }
    }

    if (!RootTools.copyFile(tempScriptPath, realScriptPath, false, false)) {
      Log.w(TAG, "Failed to copy new battery anim script! 1st try.");
      try { Thread.sleep(5000l); } catch (InterruptedException e) {}
      if (!RootTools.copyFile(tempScriptPath, realScriptPath, false, false)) {
        try { Thread.sleep(5000l); } catch (InterruptedException e) {}
        Log.w(TAG, "Failed to copy new battery anim script! Second try.");
        if (!RootTools.copyFile(tempScriptPath, realScriptPath, false, false)) {
          Log.w(TAG, "Failed to copy new battery anim script! Giving up.");
          return false;
        }
      }
    }

    // change user/group to be the same as the original script
    String usrCmd = "chown " + perm.getUser() + ":" + perm.getGroup() + " " + realScriptPath;
    if (!executeSudoCmd(usrCmd)) {
      Log.w(TAG, "Failed to chmod new battery anim script! Giving up.");
      return false;
    }

    // change permissions to rwxr-xr-x
    String permCmd = "chmod 755 " + realScriptPath;
    if (!executeSudoCmd(permCmd)) {
      Log.w(TAG, "Failed to chown new battery anim script! Giving up.");
      return false;
    }

    // finish by remounting /system as read-only
    if (!RootTools.remount("/system", "ro")) {
      Log.w(TAG, "Failed to mount /system as read-only!");
    }

    Log.i(TAG, "Finished copying new battery anim script.");
    return true;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private boolean setSuperSUSettings() {
    Log.i(TAG, "Checking SuperSU prefs");
    // get existing permissions
    Permissions perm = RootTools.getFilePermissionsSymlinks(SUPERSU_CONFIG_FILE);

    if (perm == null) {
      Log.w(TAG, "Couldn't get old SuperSU settings permissions! Giving up.");
      return false;
    }

    // change permissions to original rwxrwxrwx-
    String permCmd = "chmod 777 " + SUPERSU_CONFIG_FILE;
    if (!executeSudoCmd(permCmd)) {
      Log.w(TAG, "Failed to chown SuperSU settings! Giving up.");
      return false;
    }

    File prefFile = new File(SUPERSU_CONFIG_FILE);

    File parentFile = prefFile.getParentFile();

    permCmd = "chmod 777 " + parentFile.getAbsolutePath();
    if (!executeSudoCmd(permCmd)) {
      Log.w(TAG, "Failed to chown SuperSU parent dir! Giving up.");
      return false;
    }

    permCmd = "chmod 777 " + parentFile.getParentFile().getAbsolutePath();
    if (!executeSudoCmd(permCmd)) {
      Log.w(TAG, "Failed to chown SuperSU grandparent dir! Giving up.");
      return false;
    }

    Class prefImplClass;
    try {
      prefImplClass = Class.forName("android.app.SharedPreferencesImpl");
    } catch (ClassNotFoundException e) {
      Log.e(TAG, "Couldn't access SharedPreferences", e);
      return false;
    }

    Constructor prefImplConstructor;
    try {
      prefImplConstructor = prefImplClass.getDeclaredConstructor(File.class,int.class);
    } catch (NoSuchMethodException e) {
      Log.e(TAG, "Couldn't access SharedPreferences Constructor", e);
      return false;
    }
    prefImplConstructor.setAccessible(true);

    Object prefImpl;
    try {
      prefImpl = prefImplConstructor.newInstance(prefFile,
          Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE);
    } catch (Exception e) {
      Log.e(TAG, "Couldn't set Constructor readable/writable", e);
      return false;
    }

    SharedPreferences sp = (SharedPreferences) prefImpl;
    if (sp.getString("config_default_access", "").equals("grant")) {
      // no need to change the value!
      Log.i(TAG, "No need to change SuperSU settings.");
      return false;
    }

    Editor editor;
    try {
      editor = (Editor) prefImplClass.getMethod("edit").invoke(prefImpl);
    } catch (Exception e) {
      Log.e(TAG, "Couldn't get SharedPreferences editor", e);
      return false;
    }

    // make the actual change to the SharedPreferences
    editor.putString("config_default_access", "grant");
    editor.commit();

    Log.w(TAG, "Set SuperSU default to 'grant' -- need to reboot");

    return true;
  }

  private boolean isFOTAKillInstalled() {
    final List<String> lineList = new ArrayList<String>();

    String cmd = "ls -al " + FOTAKILL_PATH;
    Command permCommand = new Command(0, cmd) {
      @Override
      public void commandOutput (int id, String line) {
        // this command should only return one line
        lineList.add(line);

        // this super call is very important -- otherwise RootTools can't
        // it's internal processing, and everything slows down dramatically
        super.commandOutput(id, line);
      }
    };

    // get current permissions (don't run as root because it's annoying to get
    // the output, and we don't need to)
    executeSynchronously(permCommand, false);

    // now lineList should have a single entry -- if permissions are good, it
    // looks something like:
    // -rw-r--r-- root     root        23076 2014-12-05 12:59 FOTAKill.apk
    String matchStr = "-rw-r--r-- root     root";
    if (lineList.size() != 1) {
      Log.i(TAG, "Wrong number of lines returned: " + lineList.size());
      return false;
    } else if (lineList.get(0).length() < matchStr.length()) {
      Log.i(TAG, "Result too short! Output of command: " + lineList.get(0));
      return false;
    } else if (!lineList.get(0).startsWith(matchStr)) {
      Log.i(TAG, "Permissions don't match! Output of command: " + lineList.get(0));
      return false;
    }

    Log.i(TAG, "FOTAKill.apk is installed with correct permissions and ownership.");
    return true;
  }

  private boolean installFOTAKill() {
    // write FOTAKill apk to actual file
    String filePath = tempDir + "/" + FOTAKILL_NAME;
    try {
      Log.d(TAG, "Writing FOTAKill.apk to " + filePath);
      writeAssetToFile(FOTAKILL_NAME, filePath);
    } catch (IOException e) {
      Log.e(TAG, "Failed to expand FOTAKill.apk to temporary file", e);
      return false;
    }

    // mount /system as writable
    if (!RootTools.remount("/system", "rw")) {
      Log.w(TAG, "Failed to mount /system as writable! Giving up.");
      return false;
    }

    if (!RootTools.copyFile(filePath, FOTAKILL_PATH, false, false)) {
      Log.w(TAG, "Failed to copy FOTAKill.apk! Giving up.");
      return false;
    }

    // change user/group to root/root
    String usrCmd = "chown root:root " + FOTAKILL_PATH;
    if (!executeSudoCmd(usrCmd)) {
      Log.w(TAG, "Failed to chmod FOTAKill.apk! Giving up.");
      return false;
    }

    // change permissions to rw-r--r--
    String permCmd = "chmod 644 " + FOTAKILL_PATH;
    if (!executeSudoCmd(permCmd)) {
      Log.w(TAG, "Failed to chown FOTAKill.apk! Giving up.");
      return false;
    }

    // finish by remounting /system as read-only
    if (!RootTools.remount("/system", "ro")) {
      Log.w(TAG, "Failed to mount /system as read-only!");
    }

    Log.i(TAG, "Finished installing FOTAKill.apk.");
    return true;
  }

  private boolean renameToBackup(String filePath, boolean remountRequired) {
    String backupPath = filePath + ".bak";
    boolean copied = RootTools.copyFile(filePath, backupPath, remountRequired, true);
    boolean deleted = RootTools.deleteFileOrDirectory(filePath, remountRequired);
    return copied && deleted;
  }

  /**
   * Write an asset file out to a real system file. This can only be done for
   * folders that the app has access to -- e.g., can't write to a directory 
   * that requires root privileges.
   * */
  private void writeAssetToFile(String assetName, String targetPath) 
      throws IOException {
    // Open the asset as the input stream
    InputStream mInput = context.getAssets().open(assetName);

    // Open the empty file as the output stream
    OutputStream mOutput = new FileOutputStream(targetPath);

    // transfer bytes from the inputfile to the outputfile
    byte[] buffer = new byte[1024];
    int length;
    while ((length = mInput.read(buffer)) > 0) {
      mOutput.write(buffer, 0, length);
    }

    // Close the streams
    mOutput.flush();
    mOutput.close();
    mInput.close();
  }

  private boolean executeSudoCmd(String cmd) {
    try {
      Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
      proc.waitFor();
    } catch (Exception e) {
      Log.e(TAG, "Failed to execute sudo command!", e);
      return false;
    }
    return true;
  }

  private boolean executeSynchronously(Command cmd, boolean asRoot) {
    try {
      RootTools.getShell(asRoot).add(cmd);
    } catch (IOException e) {
      Log.e(TAG, "Executing command failed due to IOException!", e);
      Log.e(TAG, cmd.toString());
      return false;
    } catch (TimeoutException e) {
      Log.e(TAG, "Executing command timed out!", e);
      Log.e(TAG, cmd.toString());
      return false;
    } catch (RootDeniedException e) {
      Log.e(TAG, "Executing command failed because root denied!", e);
      Log.e(TAG, cmd.toString());
      return false;
    } catch (IllegalStateException e) {
      Log.e(TAG, "Can't execute right now. Giving up.", e);
      return false;
    }

    // wait for command to finish
    while (!cmd.isFinished()) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Log.e(TAG, "Failed to wait for command to finish!", e);
        Log.e(TAG, cmd.toString());
        return false;
      }
    }

    return true;
  }
}
