package com.via.avi;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import com.github.anrwatchdog.ANRWatchDog;
import com.pushlink.android.PushLink;
import com.pushlink.android.StrategyEnum;
import com.via.avi.battery.ViaBatteryManager;
import com.via.avi.config.ConfigValues;
import com.via.avi.config.DeviceConfigChecklistTask;
import com.via.avi.config.MissingCVProperty;
import com.via.avi.files.AviFileManager;
import com.via.avi.files.OldFilesCleaner;
import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.gs.UpdatableGlobalStateCopy;
import com.via.avi.location.LocationHandler;
import com.via.avi.location.LocationHandlerInterface;
import com.via.avi.location.LocationService;
import com.via.avi.location.LocationUtils;
import com.via.avi.location.NewStyleLocationService;
import com.via.avi.location.OldStyleLocationService;
import com.via.avi.messages.Exist;
import com.via.avi.messages.ExistDataSource;
import com.via.avi.messages.ExistDbHelper;
import com.via.avi.messages.MqttMessageManager;
import com.via.avi.mqtt.MqttManager;
import com.via.avi.mqtt.MqttManager.Builder;
import com.via.avi.mqtt.config.MissingMCVProperty;
import com.via.avi.mqtt.config.MqttConfigValues;
import com.via.avi.screen.ScreenController;
import com.via.avi.sync.SntpClient;
import com.via.avi.utils.AndroidInternetChecker;
import com.via.avi.utils.GpsRestarter;
import com.via.avi.utils.InternetManager;
import com.via.avi.utils.RepeatingAlarm;
import com.via.avi.utils.AviUEHandler;
import com.via.avi.utils.Util;
import com.via.avi.R;

public class AviActivity extends FragmentActivity
implements AviInterface, DeviceManager
{

  // TAG for debugging
  public static String TAG = "AviActivity";

  // Store configuration settings
  private ConfigValues cv;
  private MqttConfigValues mcv;

  // connection setting
  private AndroidInternetChecker mConnChecker;
  private InternetManager mConnManager;

  // for loss of communication
  private BroadcastReceiver mConnReceiver;

  // for global state
  private UpdatableGlobalState globalState;

  // files
  private AviFileManager fileManager;

  // locationing
  private LocationService mLocationService;
  private LocationHandlerInterface mLocationHandler;
  private GpsRestarter mGpsRestarter;

  // I exist messaging
  private RepeatingAlarm mExistMessageAlarm;
  private BroadcastReceiver mExistMessageAlarmReceiver;
  private ExistDataSource existData;

  // Main loop
  private MainLoopThread melThread;

  // Old files check
  private RepeatingAlarm mOldFilesCheckAlarm;
  private BroadcastReceiver mOldFilesCheckAlarmReceiver;

  // Foreground check
  private RepeatingAlarm mForegroundCheckAlarm;

  // SafetyNet check
  private RepeatingAlarm mSafetyNetCheckAlarm;
  private BroadcastReceiver mSafetyNetCheckAlarmReceiver;

  // thread pool for background tasks
  private static ExecutorService pool = Executors.newCachedThreadPool();

  // battery
  private ViaBatteryManager batteryManager;
  private BroadcastReceiver myBatteryReceiver;

  // UI views and controllers
  private ScreenController mScreenController;

  // WIFI manager and lock
  private WifiManager mWifiManager;
  private WifiLock mWifiLock;

  // For Database actions
  private Context mContext;

  // MQTT communications
  private MqttManager mqttManager = null;
  private MqttMessageManager messageManager = null;

  // Wake State Manager
  private WakeStateManager mWakeStateManager = null;

  // thread to watch for ANRs
  private ANRWatchDog mANRWatchDog;

  // Time sync
  private static final SimpleDateFormat DATE_FORMAT = 
      new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
  
  // for Push-Link updates
  private boolean doPushLink = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "Activity: in onCreate");
    // Never deploy in strict mode!
    //		StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
    //				.detectAll() // or .detectAll() for all detectable problems
    //				.penaltyLog().build());
    //		StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
    //				.detectLeakedSqlLiteObjects().detectLeakedClosableObjects()
    //				.penaltyLog().build());
    super.onCreate(savedInstanceState);
    mContext = this.getApplicationContext();

    // This function initializes config settings
    try {
      prepareConfig();
    } catch (MissingCVProperty e) {
      Log.e(TAG, "Can't load ConfigValues. Closing!", e);
      this.finish();
    } catch (IOException e) {
      Log.e(TAG, "Can't load configuration file. Closing!", e);
      this.finish();
    } catch (MissingMCVProperty e) {
      Log.e(TAG, "Can't load MqttConfigValues. Closing!", e);
      this.finish();
    }
    prepareDeviceStateInfo();

    // Initialize UI elements
    initializeUI();

    // Critical data sources
    prepareFiles();

    // Connectivity and locations
    prepareConnectivityReceiver();
    prepareMqttManager();

    // Message sending (needs to be after both MqttManager and CrashData)
    prepareMessageManager();

    // Initialize the mLocationService and mLocationHandler
    prepareLocationListener();

    // Maintenance tasks
    prepareBatteryReceiver();
    prepareOldFilesCheck();
    prepareExistAlarmReceiver();
    prepareForegroundCheck();

    // Crash handling
    prepareUEH();

    // Finally, start running the main code loop (needs to be after
    // MessageSender):
    prepareWakeCheck();
    prepareMainLoop();

    // Initialize updater
    prepareUpdater();

    // Initializes mANRWatchDog. At the end of onCreate() because
    // initializing stuff is slow and we don't want to kill it.
    prepareANRWatchDog();

    prepareDeviceConfig();

    prepareSafetyNetChecker();
  }

  private void prepareSafetyNetChecker() {
    // BroadcastReceiver to catch SafetyNet check intents
    Log.d(TAG, "Preparing safety net alarm every " + cv.SafetyNetCheckCadence());
    mSafetyNetCheckAlarmReceiver = new SafetyNetCheckReceiver();
    mSafetyNetCheckAlarm = new RepeatingAlarm(
        Util.SAFETY_NET_CHECK_INTENT, cv.SafetyNetCheckCadence(), 
        cv.SafetyNetCheckCadence());

    if (!Util.MockMode && !Util.Debug) {
      registerReceiver(mSafetyNetCheckAlarmReceiver, new IntentFilter(
          Util.SAFETY_NET_CHECK_INTENT));
      mSafetyNetCheckAlarm.setAlarm(mContext);
    }
  }

  @Override
  public void receivedRebootOrder() {
    runOnUiThread(new Runnable() {

      @Override
      public void run() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            sudoRebootTablet();
          }
        }, 15*1000l);
      }
    });
  }

  @Override
  public void receivedShutdownOrder() {
    runOnUiThread(new Runnable() {

      @Override
      public void run() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            sudoShutdownTablet();
          }
        }, 15*1000l);
      }
    });
  }

  private void prepareDeviceConfig() {
    if (Util.MockMode || Util.Debug) {
      // don't do the device preparation
      return;
    }
    DeviceConfigChecklistTask task = new DeviceConfigChecklistTask(mContext, 
        this, cv);
    Log.d(TAG, "Executing device config task");
    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void []) null);
  }

  private void prepareMessageManager() {
    ExistDbHelper dbHelper = new ExistDbHelper(mContext);
    existData = new ExistDataSource(dbHelper,cv.ExistMessageBufferSize());
    messageManager = new MqttMessageManager(mConnChecker, mqttManager,
        existData, cv.Agency(),
        globalState.getDeviceState().getDeviceId(), cv);
    messageManager.startClearing();
  }

  private void prepareUpdater() {
    doPushLink = (cv.PushLinkApiKey() != null);

    if (!Util.Debug && doPushLink) {
      PushLink.setCurrentStrategy(StrategyEnum.NINJA);
      PushLink.addMetadata("agency", cv.Agency());
      // Setting idle=false prevents PushLink from actually starting. Setting
      // idle=true will allow PushLink to actually start listening for updates.
      // This is done in enableUpdates().
      PushLink.idle(false);
      PushLink.start(mContext,
          R.drawable.ic_launcher,
          cv.PushLinkApiKey(),
          android.os.Build.SERIAL);
    }
  }

  private void prepareConfig() throws MissingCVProperty, IOException,
  MissingMCVProperty {
    this.cv = new ConfigValues("avi.config", mContext);
    this.mcv = new MqttConfigValues("avi.mqtt_config", mContext);
  }

  private void prepareANRWatchDog() {
    if (!Util.Debug) {
      mANRWatchDog = new ANRWatchDog();
      mANRWatchDog.start();
    }
  }

  private void prepareForegroundCheck() {
    mForegroundCheckAlarm = new RepeatingAlarm(ForegroundCheckReceiver.class,
        cv.ForegroundWakeCheckCadence(), cv.ForegroundWakeCheckCadence());
  }

  private void prepareOldFilesCheck() {
    mOldFilesCheckAlarmReceiver = new OldFilesCleaner(pool,
        cv.FilesDirectoryName(), fileManager.getFilePath(), cv.MaxFileAge());
    
    registerReceiver(mOldFilesCheckAlarmReceiver, new IntentFilter(
        Util.OLD_FILES_CHECK_INTENT));

    mOldFilesCheckAlarm = new RepeatingAlarm(
        Util.OLD_FILES_CHECK_INTENT, cv.OldFilesCheckCadence(),
        cv.OldFilesCheckCadence());
    mOldFilesCheckAlarm.setAlarm(mContext);
  }

  private void prepareBatteryReceiver() {
    batteryManager = new ViaBatteryManager(cv, this, this);

    myBatteryReceiver = new BroadcastReceiver() {
      public void onReceive(Context context, Intent intent) {
        batteryManager.processBatteryIntent(intent);
      }
    };
    registerReceiver(myBatteryReceiver, new IntentFilter(
        Intent.ACTION_BATTERY_CHANGED));
  }

  @Override
  public void startWakeCheckIfNotRunning() {
    mWakeStateManager.startHandling(0l);
  }

  @Override
  public void sendExistMessage(UpdatableGlobalStateCopy globalState) {
    Exist exist = createExistMessage(globalState);
    // write exist message to file
    fileManager.writeExist(exist);
    // phone home
    messageManager.sendExistMessage(exist);
  }

  private void prepareExistAlarmReceiver() {
    // BroadcastReceiver to catch ExistMessage creation intents and schedule
    // that task
    mExistMessageAlarmReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "ExistMessageAlarm intent received");
        PowerManager pm = (PowerManager) context
            .getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();
        try {
          sendExistMessage(globalState);
        } catch (Exception e) {
          Log.e(TAG, "Error in the creation of the ExistMessage.", e);
        }
        wl.release();
      }
    };
    // generate initial exist message with no delay
    mExistMessageAlarm = new RepeatingAlarm(
        Util.EXIST_MESSAGE_ALARM_INTENT, cv.ExistMessageCadence(), 0l);

    if (!Util.MockMode) {
      registerReceiver(mExistMessageAlarmReceiver, new IntentFilter(
          Util.EXIST_MESSAGE_ALARM_INTENT));
      mExistMessageAlarm.setAlarm(mContext);
    }
  }

  private void prepareUEH() {
    // setup handler for uncaught exception
    if (!Util.Debug) {
      AviUEHandler mUEHandler = new AviUEHandler(mContext,
          fileManager);
      Thread.setDefaultUncaughtExceptionHandler(mUEHandler);
    }
  }

  private void initializeUI() {
    mScreenController = new ScreenController(this, cv);

    // Initially turn on and set the screen to stay on
    enableScreen();
  }

  private void prepareDeviceStateInfo() {

    // Get the app version to communicate it with the I exist messages
    PackageInfo pInfo = null;
    try {
      pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
    } catch (NameNotFoundException e1) {
      Log.e(TAG, "Error getting the package manager info", e1);
    }
    globalState = UpdatableGlobalState.getInstance();
    globalState.getDeviceState().setAppVersion(pInfo.versionName);
    Log.d(TAG, "Version of the app: " + pInfo.versionName);

    globalState.getDeviceState().setDeviceId(android.os.Build.SERIAL);
  }

  private void prepareMqttManager() {
    int connectTimeout = 10;
    int keepAlive = 20;

    // use default connection options
    MqttConnectOptions conOpt = new MqttConnectOptions();
    conOpt.setConnectionTimeout(connectTimeout);
    //		conOpt.setUserName(globalState.getDeviceState().getDeviceId());
    conOpt.setKeepAliveInterval(keepAlive);
    if (mqttManager == null) {
      Builder mmB = new MqttManager
          .Builder(globalState.getDeviceState().getDeviceId(), 
              conOpt, mContext, cv, mcv);
      mqttManager = mmB.build();
    } else {
      mqttManager.setConnectOptions(conOpt);
    }
    mConnChecker.setMqttManager(mqttManager);
    if (mConnChecker.isInternetConnected()) {
      mqttManager.createConnection(0);
    }
  }

  private void prepareConnectivityReceiver() {
    // Chunk of code corresponding to communication with the server

    // Get the wifi manager and wifi lock
    mWifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
    mWifiLock = mWifiManager.createWifiLock(
        WifiManager.WIFI_MODE_FULL_HIGH_PERF, "VIA Wifi Lock");
    mWifiLock.setReferenceCounted(false);

    // mConnChecker will provide connection status
    mConnChecker = new AndroidInternetChecker(mContext);
    mConnReceiver = new ConnectivityReceiver(this, mConnChecker);
    registerReceiver(mConnReceiver, new IntentFilter(
        ConnectivityManager.CONNECTIVITY_ACTION));
    mConnManager = new InternetManager(mConnChecker, this, 
        cv.ConnectivityRebootAge());
  }

  private void prepareFiles() {
    fileManager = new AviFileManager(Environment.getExternalStorageDirectory(),
        cv.FilesDirectoryName());
  }

  private void prepareLocationListener() {
    Log.d(TAG, "In initializeLocationListener(): Initializing the LocationListener");
    mLocationHandler = new LocationHandler(globalState);
    if (LocationUtils.googlePlayServicesConnected(mContext)) {
      mLocationService = new NewStyleLocationService(mContext,
          cv.GpsRequestMinTime(), mLocationHandler);
    } else {
      mLocationService = new OldStyleLocationService(mContext, mLocationHandler,
          cv.GpsRequestMinTime(), cv.GpsRequestMaxDist());
    }

    mGpsRestarter = new GpsRestarter(this, cv.GpsRebootAge());
  }

  @Override
  protected void onStart() {
    Log.d(TAG, "Activity: in onStart");
    super.onStart();
  }

  @Override
  protected void onRestart() {
    Log.d(TAG, "Activity: in onRestart");
    super.onRestart();
  }

  @Override
  protected void onResume() {
    Log.d(TAG, "Activity: in onResume");
    super.onResume();
  }

  @Override
  protected void onPause() {
    Log.d(TAG, "Activity: in onPause");
    super.onPause();
  }

  @Override
  protected void onStop() {
    Log.d(TAG, "Activity: in onStop");
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    Log.d(TAG, "Activity: in onDestroy");

    // kill MainEventLoop
    melThread.close();

    // remove battery listener
    unregisterReceiver(myBatteryReceiver);
    Log.d(TAG, "battery receiver removed");

    // stop listening for connectivity updates
    unregisterReceiver(mConnReceiver);
    Log.d(TAG, "connectivity receiver removed");

    // stop listening for new locations
    disableLocations();

    // turn off connectivity lock
    disableConnectivity();

    // stop managing the wake state
    mWakeStateManager.stopHandling();

    // stop writing to files
    fileManager.close();

    messageManager.stopClearing();

    // stop monitoring for ANRs during destroy
    if (!Util.Debug) {
      mANRWatchDog.interrupt();

      if (!Util.MockMode) {
        mExistMessageAlarm.cancelAlarm(mContext);
        unregisterReceiver(mExistMessageAlarmReceiver);
      }
    }

    // Remove Old files check
    mOldFilesCheckAlarm.cancelAlarm(mContext);
    unregisterReceiver(mOldFilesCheckAlarmReceiver);

    // Remove foreground check
    mForegroundCheckAlarm.cancelAlarm(mContext);

    super.onDestroy();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Log.d(TAG, "Orientation change!");
  }

  @Override
  protected void onSaveInstanceState(Bundle savedInstanceState) {
    Log.d(TAG, "Activity: in onSaveInstanceState");
    super.onSaveInstanceState(savedInstanceState);
  }

  // Limit the ability to interact with the device
  @Override
  public void onBackPressed() {
    Log.d(TAG, "in onBackPressed");
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      return true;
    } else {
      return super.onKeyDown(keyCode, event);
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      return true;
    } else if (event.getKeyCode() == KeyEvent.KEYCODE_POWER) {
      return super.onKeyDown(keyCode, event);
    } else {
      return super.onKeyDown(keyCode, event);
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    Log.d(TAG, "Focus change");
    super.onWindowFocusChanged(hasFocus);
    if (!hasFocus) {
      if ((WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON & this.getWindow()
          .getAttributes().flags) == WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) {
      }

      // Close every kind of system dialog
      Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
      sendBroadcast(closeDialog);
    }
  }

  private Exist createExistMessage(UpdatableGlobalStateCopy globalState){
    Long time = Util.getCurrentTimeWithGpsOffset();
    DeviceState deviceState = globalState.getDeviceStateCopy();

    Long lastGpsTime = 0l;
    Location l = deviceState.getCurrentLocation();
    if (l != null) {
      lastGpsTime = l.getTime();
    }

    Log.d(TAG,"(createExistMessage) last gps time = " + lastGpsTime);
    if (lastGpsTime != 0) {
      Long timeDiff = time - lastGpsTime;
      if (timeDiff > 60000L) {
        Log.d(TAG,"On IExist, GPS has not been active for more than a minute");
      } else {
        Log.d(TAG, "On IExist, GPS active.");
      }
    }
    Exist exist = new Exist()
    .setDeviceId(deviceState.getDeviceId())
    .setTime(time)
    .setSentTime(time)
    .setBatteryLevel((double) deviceState.getBatteryLevel())
    .setBatteryTemperature(deviceState.getBatteryTemperature())
    .setBatteryStatus(deviceState.getBatteryChargingStatus())
    .setBatteryPlugged(deviceState.getBatteryPlugStatus())
    .setBatteryHealth(deviceState.getBatteryHealthStatus())
    .setAppVersion(deviceState.getAppVersion())
    .setLastGpsTime(lastGpsTime);

    if (l != null) {
      exist.setLatitude(l.getLatitude()).setLongitude(l.getLongitude());
    }

    exist.setCommunication(mConnChecker.isInternetConnected());
    exist.setMqtt(mConnChecker.isMqttConnected());

    return exist;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void enableAirplaneMode(){
    Log.d(TAG,"Build version " + Integer.toString(Build.VERSION.SDK_INT));
    if (Build.VERSION.SDK_INT >= 17){
      SudoSetAirplaneModeTask task = new SudoSetAirplaneModeTask(true);
      task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void []) null);
    } else {
      Settings.System.putInt(getContentResolver(),
          Settings.System.AIRPLANE_MODE_ON, 1);
      Intent airplaneModeIntent = new Intent(
          Intent.ACTION_AIRPLANE_MODE_CHANGED);
      airplaneModeIntent.putExtra("state", true);
      sendBroadcast(airplaneModeIntent);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void disableAirplaneMode(){
    Log.d(TAG,"Build version " + Integer.toString(Build.VERSION.SDK_INT));
    if (Build.VERSION.SDK_INT >= 17){
      SudoSetAirplaneModeTask task = new SudoSetAirplaneModeTask(false);
      task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void []) null);
    } else {
      Settings.System.putInt(getContentResolver(),
          Settings.System.AIRPLANE_MODE_ON, 0);
      Intent airplaneModeIntent = new Intent(
          Intent.ACTION_AIRPLANE_MODE_CHANGED);
      airplaneModeIntent.putExtra("state", false);
      sendBroadcast(airplaneModeIntent);
    }
  }

  private void enableConnectivity() {
    mWifiLock.acquire();
    mConnManager.startLooping();
  }

  private void disableConnectivity() {
    // release the Wifi lock
    mConnManager.stopLooping();
    if (mWifiLock!=null && mWifiLock.isHeld()){
      mWifiLock.release();
    } 
  }

  @Override
  public void onDisconnect() {
    Log.d(TAG, "connection is down");
    Log.d(TAG, "disconnecting mqttManager");
    mqttManager.disconnect();
    mqttManager.cancelConnectAttempts();
  }

  @Override
  public void onConnect() {
    Log.d(TAG, "connection is back on, attempting to reconnect mqttManager");

    Log.d(TAG,"Attempting to sync the device time since connection is available again.");
    // Get the NTP time and sync the device if necessary
    pool.execute(new Runnable() {
      @Override
      public void run() {
        SntpClient client = new SntpClient();
        if (client.requestTime("pool.ntp.org", 20000)) {
          long NTPTime = client.getNtpTime() + SystemClock.elapsedRealtime()
              - client.getNtpTimeReference();
          if (Math.abs(NTPTime - System.currentTimeMillis()) > 5000L){
            changeSystemTime(NTPTime);
            Log.d(TAG,"Modifying the system time. NTP now: " + NTPTime + ", System now: " + System.currentTimeMillis());
          }
        } else {
          Log.d(TAG, "NTP request failed.");
        }
      }
    });

    // no delay on reconnect
    mqttManager.createConnection(0);
  }

  @Override
  public void enableLocations() {
    if (mLocationHandler == null) {
      prepareLocationListener();
    }

    if (!Util.MockMode) {
      mLocationService.startGPS();
      mGpsRestarter.startLooping();
    }
  }

  @Override
  public void disableLocations() {
    if (!Util.MockMode && mLocationService != null) {
      mLocationService.stopGPS();
      mGpsRestarter.stopLooping();
    }
  }

  private void disableUpdates() {
    if (!Util.Debug && doPushLink) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          // tell PushLink that AVI is busy and therefore don't update
          PushLink.idle(false);
        }
      });
    }
  }

  private void enableUpdates() {
    if (!Util.Debug && doPushLink) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          // tell PushLink that AVI is idle and therefore accept updates
          PushLink.idle(true);
        }
      });
    }
  }

  private void enableScreen() {
    Log.d(TAG,"Enabling Screen On from the AVI App");
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);

    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE); 
    @SuppressWarnings("deprecation")
    WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
        | PowerManager.ACQUIRE_CAUSES_WAKEUP
        | PowerManager.ON_AFTER_RELEASE, "MyWakeLock");
    wakeLock.acquire();
    wakeLock.release();
  }

  private void disableScreen() {
    Log.d(TAG,"Disabling Screen On from the AVI App");
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
  }

  @Override
  public void enableWakeTasks() {
    mScreenController.setAwake(true);

    enableConnectivity();
    enableScreen();
    enableUpdates();
    enableWakeForegroundCheck();
    enableMessageClearing();

    // enable main event loop
    if (melThread != null) {
      melThread.unpause();
    }
  }

  @Override
  public void disableWakeTasks() {
    mScreenController.setAwake(false);

    disableConnectivity();
    disableScreen();
    disableUpdates();
    enableSleepForegroundCheck();
    disableMessageClearing();

    // disable main event loop
    if (melThread != null) {
      melThread.pause();
    }
  }

  private void enableMessageClearing() {
    messageManager.startClearing();
  }

  private void disableMessageClearing() {
    messageManager.stopClearing();
  }

  private void enableWakeForegroundCheck() {
    mForegroundCheckAlarm.cancelAlarm(mContext);
    mForegroundCheckAlarm.setRepeatPeriod(cv.ForegroundWakeCheckCadence());
    mForegroundCheckAlarm.setAlarm(mContext);
  }

  private void enableSleepForegroundCheck() {
    mForegroundCheckAlarm.cancelAlarm(mContext);
    mForegroundCheckAlarm.setRepeatPeriod(cv.ForegroundSleepCheckCadence());
    mForegroundCheckAlarm.setAlarm(mContext);
  }

  private void prepareForShutdown() {
    // Ensure that queued exist messages are stored before actually
    // shutting down.
    try {
      if (existData != null) {
        existData.flushExistMessageQueueToDb();
      }
    } catch (Exception ex) {  
      Log.i(TAG, "Could not perform pre-shutdown cleanup", ex);
    }
  }

  /**
   * USE ONLY AT THE UTMOST END OF NEED. USES ROOT POWER TO MANUALLY RESTART
   * TABLET. 
   */
  private void sudoRebootTablet() {
    if (!Util.Debug) {
      Log.w(TAG, "Attempting to reboot!");
      prepareForShutdown();
      try {
        Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", "reboot" });
        proc.waitFor();
      } catch (Exception ex) {
        Log.i(TAG, "Could not reboot", ex);
      }
    } else {
      Log.i(TAG, "In debug mode: not rebooting");
    }
  }

  /**
   * USE ONLY AT THE UTMOST END OF NEED. USES ROOT POWER TO MANUALLY SHUT DOWN
   * TABLET. 
   */
  private void sudoShutdownTablet() {
    if (!Util.Debug) {
      Log.w(TAG, "Attempting to shut down!");
      prepareForShutdown();
      try {
        Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", "reboot", "-p"});
        proc.waitFor();
      } catch (Exception ex) {
        Log.i(TAG, "Could not shut down", ex);
      }
    } else {
      Log.i(TAG, "In debug mode: not shutting down");
    }
  }

  private void prepareWakeCheck(){
    Log.d(TAG,"Starting the Wake Check manager.");
    mWakeStateManager = new WakeStateManager(this, cv,
        cv.AgencyTimeZone().getID());
    mWakeStateManager.startHandling(cv.WakeCadence());
  }

  private void prepareMainLoop() {
    melThread = new MainLoopThread(messageManager, cv.MainLoopSleep());
    melThread.start();
  }

  private void changeSystemTime(long time){
    String localTime = DATE_FORMAT.format(new Date(time));
    String year = localTime.substring(6, 10);
    String month = localTime.substring(3, 5);
    String day = localTime.substring(0, 2);
    String hour = localTime.substring(11, 13);
    String minute = localTime.substring(14, 16);
    String second = localTime.substring(17, 19);
    String command = "date -s "+year+month+day+"."+hour+minute+second+"\n";
    executeSudoCmd(command,true);
    Log.d(TAG,"SUDO command: " + command);
  }

  private boolean executeSudoCmd(String cmd, boolean waitFor) {
    try {
      Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
      if (waitFor) proc.waitFor();
    } catch (Exception e) {
      Log.e(TAG, "Failed to execute sudo command!", e);
      return false;
    }
    return true;
  }
}
