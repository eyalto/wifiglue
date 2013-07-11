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

	
	private long mobileTx_;
	private long initMobileTx_;
	private long mobileRx_;
	private long initMobileRx_;
	private long totalTx_;
	private long totalRx_;
	private long initTotalTx_;
	private long initTotalRx_;
	
	// 
	public WifiDataUsage(){	
		startSession();
	}
	// transmitted bytes over wifi interface
	public long getWifiTransmitted(){
		return (totalTx_ - initTotalTx_) - (mobileTx_- initMobileTx_);
	}
	// received bytes over wifi interface
	public long getWifiReceived(){
		return (totalRx_ - initTotalRx_) - (mobileRx_- initMobileRx_);
	}
	// transmitted bytes over mobile interface
	public long getMobileTransmitted(){
		return (mobileTx_- initMobileTx_);
	}
	// received bytes over wifi interface
	public long getMobileReceived(){
		return (mobileRx_- initMobileRx_);
	}

	//
	public void startSession(){
		initMobileTx_ = TrafficStats.getMobileTxBytes();
		initMobileRx_ = TrafficStats.getMobileRxBytes();
		initTotalTx_ = TrafficStats.getTotalTxBytes();
		initTotalRx_ = TrafficStats.getTotalRxBytes();
	}

	//	
	public void updateUsageCounters(){

		
		mobileTx_ = TrafficStats.getMobileTxBytes();
		mobileRx_ = TrafficStats.getMobileRxBytes();
		totalTx_ = TrafficStats.getTotalTxBytes();
		totalRx_ = TrafficStats.getTotalRxBytes();
		
		if (totalTx_ < initTotalTx_){
			totalTx_ = initTotalTx_;
		}
		if (totalRx_ < initTotalRx_){
			totalRx_ = initTotalRx_;
		}
		if (mobileTx_ < initMobileTx_){
			mobileTx_ = initMobileTx_;
		}
		if (mobileRx_ < initMobileRx_){
			mobileRx_ = initMobileRx_;
		}
	}


}
