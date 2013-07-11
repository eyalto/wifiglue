package org.eeiiaa.tether;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.eeiiaa.wifi.Wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.util.Log;

public class WifiApManager extends BroadcastReceiver implements Runnable {
	/*
	 * constants matching android.net.wifi.WifiManager hidden constants
	 */
	public static final int WIFI_AP_STATE_DISABLING = 10;
	public static final int WIFI_AP_STATE_DISABLED = 11;
	public static final int WIFI_AP_STATE_ENABLING = 12;
	public static final int WIFI_AP_STATE_ENABLED = 13;
	public static final int WIFI_AP_STATE_FAILED = 14;
	public static final String WIFI_AP_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED";
	public static final String EXTRA_WIFI_AP_STATE = "wifi_state";
	public static final String EXTRA_PREVIOUS_WIFI_AP_STATE = "previous_wifi_state";

	//
	public static final int DEFAULT_UPDATE_INTERVAL = 3000; // 3 secs
	public static final int DEFAULT_CONNECTION_DELAY = 5000; // 10 seconds
	public static final int DEFAULT_ENBLING_DELAY = 1000; // 1 sec
	public static final int DEFAULT_DISABLING_DELAY = 1000; // 1 sec

	public interface MobileApListener {
		public void mobileApEnabled(String apSsid, String pwd);

		public void mobileApDisabled();

		public void devicesConnected(HashMap<String, String> devices);
	}

	public interface MobileApDataUsedListener {
		public void mobileApDataUsedUpdate(long transmitted, long received);

		public void mobileApDataUsed(long transmitted, long received);
	}

	//
	private Thread dataUpdateThread_;
	private WifiDataUsage used_;
	private WifiConfiguration savedApConfig_;
	private boolean restoreSavedAp_;

	public WifiApManager(Context context) {
		mContext = context;
		mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
		isRunning = false;
		mDevicesArp = new HashMap<String, String>();
		mListener = null;
		mDataListener = null;
		wifiConfig = new WifiConfiguration();
		updateInterval_ = DEFAULT_UPDATE_INTERVAL;
		mContext.registerReceiver(this, new IntentFilter(WIFI_AP_STATE_CHANGED_ACTION));
	}

	public boolean setWifiApEnabled(boolean enabled) {
		boolean ok = false;
		if (enabled) {
			savedApConfig_ = getWifiApConfiguration();
			ok = setWifiApEnabled(wifiConfig, enabled);
		}
		else {
			// return to previous settings
			if (savedApConfig_.SSID.isEmpty() || savedApConfig_.SSID.equals(wifiConfig.SSID)) {
				// in case of no previous settings or error where
				// previous settings are the same as current settings AndroidAP with no
				// password
				savedApConfig_.SSID = "AndroidAP";
				savedApConfig_.allowedKeyManagement.set(KeyMgmt.NONE);
			}
			restoreSavedAp_ = true;
			// disabling
			ok = setWifiApEnabled(wifiConfig, enabled);
		}

		return ok;
	}

	public boolean setWifiApEnabled(String ssid, String pwd, boolean enabled) {
		wifiConfig.SSID = ssid;
		Wifi.setupSecurity(wifiConfig, Wifi.WPA, pwd);

		boolean ok = false;
		if (enabled) {
			savedApConfig_ = getWifiApConfiguration();
			ok = setWifiApEnabled(wifiConfig, enabled);
		}
		else {
			// return to previous settings
			if (savedApConfig_.SSID.isEmpty() || savedApConfig_.SSID.equals(wifiConfig.SSID)) {
				// in case of no previous settings or error where
				// previous settings are the same as current settings AndroidAP with no
				// password
				savedApConfig_.SSID = "AndroidAP";
				savedApConfig_.allowedKeyManagement.set(KeyMgmt.NONE);
			}
			restoreSavedAp_ = true;
			// disabling
			ok = setWifiApEnabled(wifiConfig, enabled);
		}

		return ok;
	}

	public boolean setWifiApEnabled(WifiConfiguration config, boolean enabled) {
		try {
			// make sure this is not a duplicate request
			int state = getWifiApState();
			if (enabled && (state == WIFI_AP_STATE_ENABLED || state == WIFI_AP_STATE_ENABLING))
				return true;
			if (!enabled && (state == WIFI_AP_STATE_DISABLED || state == WIFI_AP_STATE_DISABLING))
				return true;

			// mobile ap desable requested - get usage - notify and exist thread when
			// finally disabled
			if (!enabled && isRunning && mDataListener != null && used_ != null) {
				updateApDataUsed();
			}

			//
			if (enabled) {
				mWifiManager.setWifiEnabled(false);
			}

			// getting the method
			Method method = mWifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);

			// activating the method
			boolean rc = (Boolean) method.invoke(mWifiManager, config, enabled);
			Log.i(TAG, "setWifiApEnabled(" + enabled + ") returned " + rc);

			// start devices thread when enabling ap
			if (enabled) {
				// initialize transmit receive counters
				used_ = new WifiDataUsage();
				transmitted = used_.getWifiTransmitted();
				received = used_.getWifiReceived();
				mState = getWifiApState(); // initialize state machine
				isRunning = true;
				dataUpdateThread_ = new Thread(this);
				dataUpdateThread_.start();
			}

			// disabling the AP - than enable the wifi again
			if (!enabled) {
				mWifiManager.setWifiEnabled(true);
			}
			return rc;
		}
		catch (Exception e) {
			Log.e(TAG, "mobile ap setup exception", e);
			return false;
		}
	}

	//
	public int getWifiApState() {
		try {
			Method method = mWifiManager.getClass().getMethod("getWifiApState");
			return (Integer) method.invoke(mWifiManager);
		}
		catch (Exception e) {
			Log.e(TAG, "", e);
			return WIFI_AP_STATE_FAILED;
		}
	}

	//
	public WifiConfiguration getWifiApConfiguration() {
		try {
			Method method = mWifiManager.getClass().getMethod("getWifiApConfiguration");
			return (WifiConfiguration) method.invoke(mWifiManager);
		}
		catch (Exception e) {
			Log.e(TAG, "reflection method call getWifiApConfiguration() failed", e);
			return null;
		}
	}

	//
	public String getWifiApSsid() {
		return getWifiApConfiguration().SSID;
	}

	//
	public String getWifiApBssid() {
		WifiConfiguration cfg = getWifiApConfiguration();
		return cfg.BSSID;
	}

	//
	public String getWifiApPwd() {
		WifiConfiguration cfg = getWifiApConfiguration();
		return cfg.preSharedKey;
	}

	//
	public String getWifiMac() {
		return mWifiManager.getConnectionInfo().getMacAddress();
	}

	//
	public void setUpdateInterval(long millies) {

	}

	@Override
	public void onReceive(Context context, Intent intent) {

		boolean notifyEnabled = false;
		boolean notifyDisabled = false;
		int currstate = -1;
		int prevstate = -1;

		Log.i(TAG, "received intent action " + intent.getAction());
		if (intent.getAction().equals(WIFI_AP_STATE_CHANGED_ACTION)) {
			if (intent.hasExtra(EXTRA_WIFI_AP_STATE)) {
				currstate = intent.getIntExtra(EXTRA_WIFI_AP_STATE, -1);
			}
			if (intent.hasExtra(EXTRA_PREVIOUS_WIFI_AP_STATE)) {
				prevstate = intent.getIntExtra(EXTRA_PREVIOUS_WIFI_AP_STATE, -1);
			}

			Log.i(TAG, "current ap state = " + getStateString(currstate) + " previous state was " + getStateString(prevstate));
			if (currstate == WIFI_AP_STATE_ENABLED && prevstate == WIFI_AP_STATE_ENABLING) {
				// check if enabled saved just for restoring so disable back
				if (getWifiApSsid().equals(wifiConfig.SSID)){
					notifyEnabled = true;
				}
				else if (restoreSavedAp_ && getWifiApSsid().equals(savedApConfig_.SSID) ){
					restoreSavedAp_ = setWifiApEnabled(savedApConfig_, false);
					Log.i(TAG,"Previous AP enabled - disabling SSID : = "+savedApConfig_.SSID);
				}
			}
			if (currstate == WIFI_AP_STATE_DISABLED
			    && (prevstate == WIFI_AP_STATE_DISABLING || prevstate == WIFI_AP_STATE_ENABLED)) {
				// check if original was disabled and restore is requested enable for restore.
				if (getWifiApSsid().equals(wifiConfig.SSID)){
					notifyDisabled = true;

					if (restoreSavedAp_){
						// enable for restore purpose
						restoreSavedAp_ = setWifiApEnabled(savedApConfig_, true);
						Log.i(TAG,"Disabled AP: "+wifiConfig.SSID+" enabling previous AP: = "+savedApConfig_.SSID);
					}
				}
				else {
					// the disabled ap is saved one or another 
					restoreSavedAp_ = false;
				}
			}
		}
		// notify disabled
		if (notifyDisabled) {
			Log.i(TAG, "notifyDisabled is true");
			if (mDataListener != null) {
				// total data report updated when disable requested
				mDataListener.mobileApDataUsed(transmitted, received);
			}
			if (mListener != null) {
				// notify ap disabled
				mListener.mobileApDisabled();
			}
		}
		// notify disabled
		if (notifyEnabled) {
			notifyEnabled = false;
			if (mListener != null)
				notifyApEnabledListener();
			// cleanup();
		}
	}

	public void cleanup() {
		isRunning = false;
		mContext.unregisterReceiver(this);
	}

	public void notifyApEnabledListener() {
		mListener.mobileApEnabled(getWifiApSsid(), getWifiApPwd());
	}

	//
	public void setMobileApListener(MobileApListener l) {
		mListener = l;
	}

	public void setMobileApDataUsedListener(MobileApDataUsedListener l) {
		mDataListener = l;
	}

	public HashMap<String, String> getDevicesConnected() {
		return mDevicesArp;
	}

	/**
	 * runnable monitoring devices connected and overall data used for updating
	 * during execution.
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public void run() {

		boolean notifyDevicesUpdated = false;
		boolean notifyDataUsedUpdate = false;

		while (isRunning) {
			notifyDevicesUpdated = false;
			notifyDataUsedUpdate = false;
			// sleep
			try {
				Thread.sleep(updateInterval_);
			}
			catch (InterruptedException e) {
				Log.e(TAG, "data usage and device connection update thread interrupted");
				e.printStackTrace();
			}

			mState = getWifiApState();
			Log.i(TAG, "wifiapstate " + getStateString(mState));

			switch (mState) {
			case WIFI_AP_STATE_ENABLED:
				notifyDevicesUpdated = updateDeviceConnectionMap();
				notifyDataUsedUpdate = updateApDataUsed();
				break;
			case WIFI_AP_STATE_DISABLED:
				isRunning = false;
				break;
			}
			// notify listener
			if (notifyDevicesUpdated) {
				mListener.devicesConnected(mDevicesArp);
			}

			// notify activity
			if (notifyDataUsedUpdate) {
				if (mDataListener != null) {
					mDataListener.mobileApDataUsedUpdate(transmitted, received);
				}
			}

		}

	}

	public static HashMap<String, String> getConnectedDeviceInfo() {
		HashMap<String, String> devices = new HashMap<String, String>();
		// open
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader("/proc/net/arp"));

			// read
			String line;
			line = br.readLine(); // dummy head line
			while ((line = br.readLine()) != null) {
				String[] entry = line.split(" +");
				String ip = entry[0];
				String mac = entry[3];
				if (!mac.equals("00:00:00:00:00:00")) {
					devices.put(mac, ip);
				}
			}
		}
		catch (FileNotFoundException e) {
			Log.e(TAG, "device connection arp file was not found");
			e.printStackTrace();
		}
		catch (IOException e) {
			Log.e(TAG, "device connection parsing arp file error");
			e.printStackTrace();
		}

		return devices;
	}

	private boolean updateDeviceConnectionMap() {
		boolean updated = false;
		HashMap<String, String> devices = getConnectedDeviceInfo();

		for (String mac : devices.keySet()) {
			String ip = devices.get(mac);
			if (!mDevicesArp.containsKey(mac)) {
				updated = true;
			}
			else if (!mDevicesArp.get(mac).equals(ip)) {
				updated = true;
			}
		}

		if (updated) {
			mDevicesArp = devices;
		}
		return updated;
	}

	private boolean updateApDataUsed() {
		boolean updated = false;
		long curr_transmitted = 0;
		long curr_received = 0;
		if (mDataListener != null) {
			used_.updateUsageCounters();
			curr_transmitted = used_.getWifiTransmitted();
			curr_received = used_.getWifiReceived();

			if (curr_transmitted != transmitted || curr_received != received) {
				transmitted = curr_transmitted;
				received = curr_received;
				updated = true;
			}
		}

		return updated;
	}

	private String getStateString(int state) {
		String str;
		switch (state) {
		case WIFI_AP_STATE_DISABLING:
			str = "DISABLING";
			break;
		case WIFI_AP_STATE_DISABLED:
			str = "DISABLED";
			break;
		case WIFI_AP_STATE_ENABLING:
			str = "ENABLING";
			break;
		case WIFI_AP_STATE_ENABLED:
			str = "ENABLED";
			break;
		case WIFI_AP_STATE_FAILED:
			str = "FAILED";
			break;
		default:
			str = "UNKNOWN #" + state;
			break;
		}

		return str;
	}

	private boolean isRunning;
	private HashMap<String, String> mDevicesArp;
	private int mState;
	private long transmitted, received;
	private MobileApListener mListener;
	private MobileApDataUsedListener mDataListener;
	private final WifiManager mWifiManager;
	private long updateInterval_;
	private Context mContext;
	private WifiConfiguration wifiConfig;
	private static final String TAG = "Wifiglue:WifiAPManager";

}
