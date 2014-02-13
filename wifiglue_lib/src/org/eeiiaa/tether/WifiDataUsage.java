The MIT License (MIT)

Copyright (c) 2014 Eyal Toledano 

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.


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
