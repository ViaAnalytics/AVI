package com.via.avi.messages.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import com.via.avi.messages.Exist;
import com.via.avi.messages.ExistDataSource;
import com.via.avi.messages.ExistDbHelper;

import android.database.sqlite.SQLiteDatabase;

@RunWith(RobolectricTestRunner.class)
public class ExistDataSourceTest extends TestCase {
  private ExistDbHelper existDbHelper;
  private ExistDataSource existDataSource, existDataSource2;
  private String customDbPath;
  private String tempDbPath;
  SQLiteDatabase database;

  private String deviceId = "fakeDevice";
  private Long ts = 1400000000000l;
  private Long timeSent = ts + 2500;
  private String appVersion = "0.1";

  private Double batteryLevel = 0.56;
  private Integer batteryPlugged = 2;
  private Integer batteryStatus = 3;
  private Integer batteryTemperature = 363;
  private Integer batteryHealth = 7;

  private Boolean comm = true;
  private Boolean mqtt = false;
  private Long lastGpsTime = ts - 5000;

  private Double latitude = -45.035;
  private Double longitude = 125.383;

  private int existMessageQueueSize = 5;

  @Before
  public void setUp() throws URISyntaxException, IOException {
    customDbPath = System.getProperty("user.dir") + "/assets/sample_exist.db";
    tempDbPath = System.getProperty("user.dir") + "/assets/sample_exist.db.tmp";

    File fi = new File(customDbPath);
    File fo = new File(tempDbPath);
    copyFileUsingFileChannels(fi, fo);
    database = SQLiteDatabase.openDatabase(tempDbPath, null, SQLiteDatabase.OPEN_READWRITE);

    existDbHelper = Mockito.mock(ExistDbHelper.class);
    Mockito.when(existDbHelper.getWritableDatabase()).thenReturn(database);
    existDataSource = new ExistDataSource(existDbHelper,existMessageQueueSize);

    existDataSource.open();
  }

  @After
  public void tearDown() {
    database.close();

    // now delete the temp file
    File fo = new File(tempDbPath);
    fo.delete();
    // and the journal file
    File fj = new File(tempDbPath + "-journal");
    if (fj.exists()) {
      fj.delete();
    }
  }

  private static void copyFileUsingFileChannels(File source, File dest)
      throws IOException {
    FileChannel inputChannel = null;
    FileChannel outputChannel = null;
    try {
      inputChannel = new FileInputStream(source).getChannel();
      outputChannel = new FileOutputStream(dest).getChannel();
      outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
    } finally {
      inputChannel.close();
      outputChannel.close();
    }
  }

  private Exist prepareExist() {
    Exist ex = new Exist();
    ex.setDeviceId(deviceId).setTime(ts).setSentTime(timeSent)
    .setAppVersion(appVersion)
    .setBatteryLevel(batteryLevel).setBatteryPlugged(batteryPlugged)
    .setBatteryStatus(batteryStatus).setBatteryTemperature(batteryTemperature)
    .setBatteryHealth(batteryHealth)
    .setCommunication(comm).setMqtt(mqtt)
    .setLastGpsTime(lastGpsTime)
    .setLatitude(latitude).setLongitude(longitude);
    return ex;
  }

  @Test
  public void insertSingleExist() {
    Exist ex = prepareExist();

    int id = existDataSource.addNewExist(ex);
    Exist newEx = existDataSource.getSingleExistMessage(id);

    assertEquals(ex.getDeviceId(), newEx.getDeviceId());
    assertEquals(ex.getTime(), newEx.getTime());
    assertEquals(ex.getSentTime(), newEx.getSentTime());
    assertEquals(ex.getAppVersion(), newEx.getAppVersion());
    assertEquals(ex.getBatteryLevel(), newEx.getBatteryLevel());
    assertEquals(ex.getBatteryPlugged(), newEx.getBatteryPlugged());
    assertEquals(ex.getBatteryStatus(), newEx.getBatteryStatus());
    assertEquals(ex.getBatteryTemperature(), newEx.getBatteryTemperature());
    assertEquals(ex.getBatteryHealth(), newEx.getBatteryHealth());
    assertEquals(ex.getCommunication(), newEx.getCommunication());
    assertEquals(ex.getMqtt(), newEx.getMqtt());
    assertEquals(ex.getLastGpsTime(), newEx.getLastGpsTime());
    assertEquals(ex.getLatitude(), newEx.getLatitude());
    assertEquals(ex.getLongitude(), newEx.getLongitude());
  }

  @Test
  public void insertSingleExistWithSomeNulls() {
    Exist ex = prepareExist();
    ex.setBatteryTemperature(null);
    ex.setMqtt(null);

    int id = existDataSource.addNewExist(ex);
    Exist newEx = existDataSource.getSingleExistMessage(id);

    assertNull(newEx.getBatteryTemperature());
    assertNull(newEx.getMqtt());
  }

  @Test
  public void insertSeveralExistMessages() {
    Exist ex1 = prepareExist();
    Exist ex2 = prepareExist();
    ex2.setTime(ts+5*60*1000l);
    Exist ex3 = prepareExist();
    ex3.setTime(ts+10*60*1000l);

    int id1 = existDataSource.addNewExist(ex1);
    int id2 = existDataSource.addNewExist(ex2);
    int id3 = existDataSource.addNewExist(ex3);

    List<Exist> msgs = existDataSource.getOldestUnsentMessages(5);

    assertEquals(3, msgs.size());

    for (Exist ex: msgs) {
      if (ex.getId() == id1) {
        assertEquals(ts.longValue(), ex.getTime().longValue());
      } else if (ex.getId() == id2) {
        assertEquals(ts+5*60*1000l, ex.getTime().longValue());
      } else if (ex.getId() == id3) {
        assertEquals(ts+10*60*1000l, ex.getTime().longValue());
      } else {
        fail("Should have matched one of the ids!");
      }
    }
  }

  @Test
  public void insertAndDeleteSeveralExistMessages() {
    Exist ex1 = prepareExist();
    Exist ex2 = prepareExist();
    ex2.setTime(ts+5*60*1000l);
    Exist ex3 = prepareExist();
    ex3.setTime(ts+10*60*1000l);

    int id1 = existDataSource.addNewExist(ex1);
    int id2 = existDataSource.addNewExist(ex2);
    int id3 = existDataSource.addNewExist(ex3);

    ArrayList<Integer> ids = new ArrayList<Integer>();
    ids.add(id1); ids.add(id2); ids.add(id3);
    existDataSource.removeSentMessages(ids);

    List<Exist> msgs = existDataSource.getOldestUnsentMessages(5);
    assertEquals(0, msgs.size());
  }

  @Test
  public void testInsertionFromTwoExistDataSources(){
    Exist ex1 = prepareExist();
    Exist ex2 = prepareExist();
    ex2.setTime(ts+5*60*1000l);
    Exist ex3 = prepareExist();
    ex2.setTime(ts+10*60*1000l);

    int id1 = existDataSource.addNewExist(ex1);
    int id2 = existDataSource.addNewExist(ex2);

    List<Exist> msgs1 = existDataSource.getOldestUnsentMessages(5);
    assertEquals(2,msgs1.size());

    // Emulate app restart
    existDataSource2 = new ExistDataSource(existDbHelper,existMessageQueueSize);
    existDataSource2.open();

    int id3 = existDataSource2.addNewExist(ex3);
    assertEquals(id1+2,id3);
    assertEquals(id2+1,id3);
    List<Exist> msgs2 = existDataSource2.getOldestUnsentMessages(5);
    assertEquals(3,msgs2.size());
  }
}