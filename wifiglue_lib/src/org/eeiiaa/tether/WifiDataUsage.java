package org.eeiiaa.tether;

import android.net.TrafficStats;
import android.util.Log;


/**
 * monitor data usage 
 * @author eyalto
 *
 */
public class WifiDataUsage {

	private static final String LOG_TAG = "Wifiglue:WifiDataUsage";

	private long wifiTransmitted_;
	private long initialWifiTransmitted_;
	private long mobileTransmitted_;
	private long initialMobileTransmitted_;
	private long wifiReceived_;
	private long initialWifiReceived_;
	private long mobileReceived_;
	private long initialMobileReceived_;

	// 
	public WifiDataUsage(){	
		startSession();
	}
	// transmitted bytes over wifi interface
	public long getWifiTransmitted(){
		return (wifiTransmitted_ - initialWifiTransmitted_) - getMobileTransmitted();
	}
	// received bytes over wifi interface
	public long getWifiReceived(){
		return (wifiReceived_ - initialWifiReceived_) - getMobileReceived();
	}
	// transmitted bytes over mobile interface
	public long getMobileTransmitted(){
		return mobileTransmitted_ - initialMobileTransmitted_;
	}
	// received bytes over wifi interface
	public long getMobileReceived(){
		return mobileReceived_ - initialMobileReceived_;
	}

	//
	public void startSession(){
		initialMobileTransmitted_ = TrafficStats.getMobileTxBytes();
		initialMobileReceived_ = TrafficStats.getMobileRxBytes();
		initialWifiTransmitted_ = TrafficStats.getTotalTxBytes();
		initialWifiReceived_ = TrafficStats.getTotalRxBytes();
	}

	//	
	public void updateUsageCounters(){

		wifiTransmitted_ = TrafficStats.getTotalTxBytes();
		wifiReceived_ = TrafficStats.getTotalRxBytes();
		mobileTransmitted_ = TrafficStats.getMobileTxBytes();
		mobileReceived_ = TrafficStats.getMobileRxBytes();
		
		if (wifiTransmitted_ < initialWifiTransmitted_) {
			initialWifiTransmitted_ = wifiTransmitted_;
		}
		if (wifiReceived_ < initialWifiReceived_) {
			initialWifiReceived_ = wifiReceived_;
		}
		if (mobileTransmitted_ < initialMobileTransmitted_) {
			initialMobileTransmitted_ = mobileTransmitted_;
		}
		if (mobileReceived_ < initialMobileReceived_) {
			initialMobileReceived_ = mobileReceived_;
		}

	}


}
