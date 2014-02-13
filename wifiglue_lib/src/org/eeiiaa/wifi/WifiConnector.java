package org.eeiiaa.wifi;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eeiiaa.tether.WifiDataUsage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiConnector extends BroadcastReceiver implements Runnable {

  // default ssid pwd
  public static final String NETSSID = "";
  public static final String NETPWD = ""; // for now no security later adding
                                          // password
  // make sure that when a new open network is added we don't have a
  // configuration list that is too big - normally will not be needed
  public static final int MAX_OPEN_NETS = 30;
  public static final int GET_ALL = 0;
  public static final int GET_BSSID_ONLY = 1;
  public static final int DEFAULT_UPDATE_INTERVAL = 5000; // 5 secs

  private boolean forgetting_ = false;
  private Thread dataUpdateThread_;
  private long updateInterval_ = DEFAULT_UPDATE_INTERVAL;
  private boolean isRunning_ = false;
  private WifiDataUsage used_;
  private IntentFilter filter_;
  
  public class ConnectionInfo {
    public WifiInfo winf;
    public DhcpInfo dhinf;
  }

  public interface OnConnectionListener {
    public void connectionEstablished(ConnectionInfo info);

    public void disconnected();

    public void forgot(boolean success);

    public void networkLost(WifiConnector con, NetworkInfo networkInfo);

    public void connectionFailed();
  }

  public interface WifiDataUsedListener {
    public void wifiDataUsed(long transmitted, long received);

    public void wifiDataUsedUpdate(long transmitted, long received);
  }

  public interface OnScanResultsListener {
    public void scanResultsJSON(String json);
  }

  /**
   * will create a connector to default setwork name without security
   * 
   * @param ctx
   */
  public WifiConnector(Context ctx, int sro) {
    this(ctx, sro, NETSSID, NETPWD);
  }

  /**
   * will create a connector to the specified network without security
   * 
   * @param ctx
   * @param ssid
   */
  public WifiConnector(Context ctx, int sro, String ssid) {
    this(ctx, sro, ssid, NETPWD);
  }

  /**
   * will create a connector with specified network and specified security if
   * the password is null or empty seting its the same as constructing without
   * the password parameter
   * 
   * @param ctx
   * @param ssid
   * @param pwd
   */
  public WifiConnector(Context ctx, int sro, String ssid, String pwd) {
    mContext = ctx;
    mNetssid = ssid;
    mPwd = pwd;
    mWifiMgr = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    mMacAddress = mWifiMgr.getConnectionInfo().getMacAddress();
    mConnecting = false;
    mListener = null;
    mScanListener = null;
    mDataListener = null;
    mWaitingConnection = false;
    mWaitingScan = false;
    scanResultOption = sro;
    filter_ = new IntentFilter();
    filter_.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    filter_.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    
  }

  public void setSsid(String ssid) {
    mNetssid = ssid;
  }

  public void setPwd(String pwd) {
    mPwd = pwd;
  }

  public String getSsid() {
    return mNetssid;
  }

  public String getPwd() {
    return mPwd;
  }

  public String getMacAddress() {
    return mMacAddress;
  }

  public void setOnConnectionListener(OnConnectionListener l) {
    //
    mListener = l;
  }

  public void setWifiDataUsedListener(WifiDataUsedListener l) {
    //
    mDataListener = l;
  }

  public void setOnScanResultsListener(OnScanResultsListener l) {
    //
    mScanListener = l;
  }

  public boolean scan() {

    if (mConnecting && !mWaitingConnection) {
      return false;
    }

    mWaitingScan = true;
    mContext.registerReceiver(this, filter_);
    boolean enabled = mWifiMgr.isWifiEnabled();

    if (!enabled) {
      mWifiMgr.setWifiEnabled(true);
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Log.e(TAG, "interrupt exception when sleeping untill wifi is enabled");
        e.printStackTrace();
      }
    }

    return mWifiMgr.startScan();
  }

  public void forget() {

    forgetting_ = true;
    
    if (dataUpdateThread_ != null) {
      isRunning_ = false;
      dataUpdateThread_.interrupt();
      try {
        dataUpdateThread_.join();
      } catch (InterruptedException e) {
        Log.w(TAG, "joining data usage update thread interrupted");
        e.printStackTrace();
      }
    }
    boolean forgotOK = Wifi.disconnectFromCurrentNetwork(mNetssid, mWifiMgr, true);
    if (forgotOK) {
      Log.i(TAG, "forgot success previous settings were");
      notifyForgot(forgotOK);
    }
    // forgetting connection state and unregister
    if (mConnected){
      mConnected = false;
      mContext.unregisterReceiver(this);
    }
    
    forgetting_ = false;
  }

  public void connect() {

    mConnected = false;
    mWaitingConnection = false;
    
    transmitted = received = 0;

    // TODO: check if not already connected
    WifiInfo info = mWifiMgr.getConnectionInfo();

    if (info != null) {
      String currssid = unQuote(info.getSSID());
      if (currssid != null) {
        // already connected
        if (currssid.equals(mNetssid)) {
          mConnected = true;
          Log.i(TAG, "already successfully connected to: " + currssid);
          notifyConnectionListener();
          mContext.registerReceiver(this, filter_);
          return;
        }
      }
    }

    if (!mConnected) {

      Log.i(TAG, "starting standard scan...");

      // when connection requested than register
      boolean enabled = mWifiMgr.isWifiEnabled();
      if (!enabled) {
        Log.i(TAG, "enabling wifi...");

        mWifiMgr.setWifiEnabled(true);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          Log.e(TAG, "interrupt exception when sleeping untill wifi is enabled");
          e.printStackTrace();
        }
      }
      mConnecting = true;
      mContext.registerReceiver(this, filter_);
      mWifiMgr.startScan();
      Log.i(TAG, "starting scan prior to connection...");

    }
  }

  public boolean disconnect() {

    Log.i(TAG, "calling disconnect procedure...");
    boolean disconnectOK;

    Log.i(TAG, "mDataListener:" + mDataListener);
    if (mDataListener != null) {
      used_.updateUsageCounters();
      transmitted = used_.getWifiTransmitted();
      received = used_.getWifiReceived();
    }
    if (disconnectOK = mWifiMgr.disconnect()) {
      Log.i(TAG, "successfully disconnected...");
      notifyDisconnected();
    }
    if (disconnectOK) {
      forget();
      Log.i(TAG, "forget() called from disconnect");
    }

    Log.i(TAG, "wWifiMgr.disconnect() finished");

    return disconnectOK;

  }

  @Override
  public void onReceive(Context context, Intent intent) {
    // handle stuff related to scanning requested when:
    // normal scan is issued
    // when forgetting a network and re-connecting to a previously configured
    // existing one
    // when connecting and need a scan to verify connection can be established
    Log.i(TAG,"action: "+intent.getAction());
    if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) && (mWaitingScan || mConnecting )) {
      // forgetting current and reconnecting to highest priority existing
      // network

      Log.i(TAG, "ACTION SCAN: mWaitingScan:" + mWaitingScan + " connecting "+mConnecting);

      // normal scan request
      if (mWaitingScan) {
        String json = new String();
        switch (scanResultOption) {
        case GET_ALL:
          json = getScannedNetworksAllJSON();
          break;
        case GET_BSSID_ONLY:
          json = getScannedNetworksJSON();
          break;
        }
        mScanListener.scanResultsJSON(json);
        mWaitingScan = false;
        
        // scan is done - unregister
        mContext.unregisterReceiver(this);
        Log.i(TAG, "processing scan results as JSON");
        
      }

      if (mConnecting && !mWaitingConnection) {
        // look for the network we want to connect to in the scanned results
        ScanResult scannedNet = searchNetwork(mNetssid);
        if (scannedNet == null) {
          Log.i(TAG, "ssid not found...: " + mNetssid);
          mConnecting = false;
          mContext.unregisterReceiver(this);
          notifyConnectionFailed();
          return; // didn't find requested network noting to connect
        }

        WifiConfiguration configuredNet = searchConfiguration(scannedNet);
        boolean result_ok = false;
                
        if (configuredNet != null) {
          // configuration exits connect to a configured network
          mWaitingConnection = true;
          result_ok = Wifi.connectToConfiguredNetwork(mContext, mWifiMgr, configuredNet, false);
          Log.i(TAG, "configuration exits connect to a configured network");
        }
        else {
          // configure and connect to network
          mWaitingConnection = true;
          result_ok = Wifi.connectToNewNetwork(mContext, mWifiMgr, scannedNet, mPwd, MAX_OPEN_NETS);
          Log.i(TAG, "configure and connect to network");
        }

        // failed to connect - unregister and notify
        if (!result_ok) {
          mContext.unregisterReceiver(this);
          mWaitingConnection = false;
          mConnecting = false;
          Log.e(TAG, "error connecting Wifi.connect returned error");
          notifyConnectionFailed();
        }
      }
    }
    
    else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION) && (mConnected || (mConnecting && mWaitingConnection))) {
      Log.i(TAG, "ACTION CONNECTIVITY: mWaitingScan:" + mWaitingScan + " forgetting: " + forgetting_ + " connecting "+mConnecting);

      NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

      if (!mConnected && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected()) {

        if (mWifiMgr.getConnectionInfo().getSupplicantState() == SupplicantState.COMPLETED) {

          Log.i(TAG, " getConnectionInfo: " + mWifiMgr.getConnectionInfo() + " getSSID: " + mWifiMgr.getConnectionInfo().getSSID());
          String connectionSsid = unQuote(mWifiMgr.getConnectionInfo().getSSID());
          // when phone turns into AP mode, wifimgr returns null...
          // fail protection for such cases...
          if (connectionSsid != null) {
            Log.i(TAG, "mNetssid: |"+mNetssid +"| connectionSsid |"+connectionSsid+"|");
            if (connectionSsid.equals(mNetssid)) {
              mConnected = true;
              mConnecting = false;
              mWaitingConnection = false;
              forgetting_ = false;

              Log.i(TAG, "VERIFIED SUCCESSFUL CONNECTION to: " + mNetssid);
              // connection done notify - do not unregister to get disconnection detected
              notifyConnectionListener();
            }
          }
        }
      }
      else if (mConnected && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && !networkInfo.isConnected()) {

        Log.i(TAG, "network is disconnected...");

        // Wifi is disconnected
        mConnected = false;
        mWaitingConnection = false;
        if (mDataListener != null && used_ != null) {
          used_.updateUsageCounters();
          transmitted = used_.getWifiTransmitted();
          received = used_.getWifiReceived();
        }
        // network lost was not expected (when forgetting we expect network to
        // be lost)
        if (!forgetting_) {
          Log.i(TAG, "network lost " + networkInfo);
          notifyNetworkLost(networkInfo);
          mContext.unregisterReceiver(this);
        }
        else {
          Log.i(TAG, "network lost when forgetting " + networkInfo);
        }
      }
      

      Log.i(TAG, "end of CONNECTIVITY_ACTION");
    }

  }

  public static final String unQuote(String str) {
    if (str == null)
        return null;
    if (str.charAt(0) == '\"' && str.charAt(str.length()-1) == '\"')
      return str.substring(1, str.length()-1);
    else 
      return str;
  }

  private void notifyConnectionListener() {
    used_ = new WifiDataUsage();
    transmitted = used_.getWifiTransmitted();
    received = used_.getWifiReceived();
    ConnectionInfo info = getConnectionInfo();
    if (mListener != null)
      mListener.connectionEstablished(info);

    dataUpdateThread_ = new Thread(this);
    isRunning_ = true;
    dataUpdateThread_.start();
  }

  private void notifyConnectionFailed() {
    if (mListener != null) {
      Log.i(TAG, "calling ui connectionFailed()");
      mListener.connectionFailed();
    }
  }

  private void notifyDisconnected() {

    if (dataUpdateThread_ != null) {
      isRunning_ = false;
      dataUpdateThread_.interrupt();
      try {
        dataUpdateThread_.join();
      } catch (InterruptedException e) {
        Log.w(TAG, "joining data usage update thread interrupted");
        e.printStackTrace();
      }
    }

    if (mListener != null) {
      Log.i(TAG, "calling ui disconnected()");
      mListener.disconnected();
    }
    if (mDataListener != null) {
      Log.i(TAG, "calling ui wifiDataUsed()");
      mDataListener.wifiDataUsed(transmitted, received);
    }
  }

  private void notifyForgot(boolean sucess) {
    if (mListener != null)
      mListener.forgot(sucess);
    if (mDataListener != null)
      mDataListener.wifiDataUsed(transmitted, received);
  }

  private void notifyNetworkLost(NetworkInfo networkInfo) {
    if (mListener != null)
      mListener.networkLost(this, networkInfo);
    if (mDataListener != null)
      mDataListener.wifiDataUsed(transmitted, received);
  }

  public ConnectionInfo getConnectionInfo() {
    ConnectionInfo info = new ConnectionInfo();
    WifiInfo winf = mWifiMgr.getConnectionInfo();
    info.winf = winf;
    
    if (winf != null) {
      if (winf.getSSID() != null) {
        String winfSsid = unQuote(winf.getSSID());
        if (winfSsid.equals(mNetssid)) {
          DhcpInfo dhinf = mWifiMgr.getDhcpInfo();
          info.dhinf = dhinf;
        }
      }
    }
    return info;
  }

  @Override
  public void run() {

    while (isRunning_) {
      if (mDataListener != null) {
        used_.updateUsageCounters();
        transmitted = used_.getWifiTransmitted();
        received = used_.getWifiReceived();
        mDataListener.wifiDataUsedUpdate(transmitted, received);
      }
      // sleep
      try {
        Thread.sleep(updateInterval_);
      } catch (InterruptedException e) {
        Log.e(TAG, "data usage update thread interrupted");
        e.printStackTrace();
      }
    }

  }

  private String getScannedNetworksJSON() {

    String jsonString = null;

    try {
      List<ScanResult> scanResults = mWifiMgr.getScanResults();
      JSONObject json = new JSONObject();

      if (scanResults == null) {
        return null;
      }

      for (ScanResult sr : scanResults) {
        json.put(sr.BSSID, sr.level);
        jsonString = json.toString(4);
      }
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return jsonString;

  }

  private String getScannedNetworksAllJSON() {

    Comparator<ScanResult> _comparator = new Comparator<ScanResult>() {
      @Override
      public int compare(ScanResult lhs, ScanResult rhs) {
        return rhs.level - lhs.level;
      }
    };

    String json = null;

    try {

      List<ScanResult> scanResults = mWifiMgr.getScanResults();
      Collections.sort(scanResults, _comparator);
      JSONArray jarr = new JSONArray(scanResults);

      json = jarr.toString(4);
    } catch (JSONException e) {
      Log.e(TAG, "error creating scan result json");
      e.printStackTrace();
    }

    return json;
  }

  private ScanResult searchNetwork(String ssid) {
    // compare with preferred list
    List<ScanResult> scanResults = mWifiMgr.getScanResults();
    ScanResult foundNet = null;
    Iterator<ScanResult> scan = scanResults.iterator();
    while (scan.hasNext() && foundNet == null) {
      ScanResult test = scan.next();
      if (test.SSID.equals(ssid)) {
        foundNet = test;
      }
    }
    return foundNet;
  }

  private WifiConfiguration searchConfiguration(ScanResult scanres) {
    // compare with preferred list
    return searchConfigurationBySSID(scanres.SSID);
  }

  private WifiConfiguration searchConfigurationBySSID(String ssid) {

    Log.i(TAG, "searchConfigurationBySSID ssid: " + ssid);

    List<WifiConfiguration> configs = mWifiMgr.getConfiguredNetworks();

    if (configs == null) {
      return null;
    }

    Iterator<WifiConfiguration> prefs = configs.iterator();
    WifiConfiguration foundPref = null;
    while (prefs.hasNext() && foundPref == null) {
      WifiConfiguration pref_test = prefs.next();
      if (ssid.equals(pref_test.SSID)) {
        foundPref = pref_test;
      }
    }
    return foundPref;
  }

  private Context mContext;
  private boolean mConnecting;
  private boolean mConnected;
  private boolean mWaitingConnection;
  private boolean mWaitingScan;
  private long transmitted, received;
  private String mNetssid;
  private String mPwd;
  private String mMacAddress;
  private WifiManager mWifiMgr;
  private OnConnectionListener mListener;
  private WifiDataUsedListener mDataListener;
  private OnScanResultsListener mScanListener;
  private static final String TAG = "Wifiglue:WifiConnector";
  private int scanResultOption;

}
