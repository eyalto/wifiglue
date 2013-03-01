package org.eeiiaa.tether;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;


/**
 * monitor data usage 
 * @author eyalto
 *
 */
public class WifiDataUsageOld {
	
	private static final String LOG_TAG = "Wifiglue:WifiDataUsage";
	private static final String INTERFACE_FILE = "/proc/self/net/dev";
	//TODO: change hard coded interface values
	private static final String WIFI_INTERFACE_NAME = " wlan0:"; //test verizon nexus s (has 2 wlan0 and m.wlan0)
	private static final String[] MOBILE_INTERFACE_NAMES = {"rmnet0", "svnet0"};
	
    private static final Pattern LINE_PATTERN = Pattern.compile("^" + // start
            "(.*?):" + // the device name (group = 1)
            "\\s*" + // blanks
            "([0-9]+)" + // 1st number (group = 2) -> bytes received
            "\\s+" + // blanks
            "([0-9]+)" + // 2nd number (group = 3) -> packets received
            "\\s+" + // blanks
            "([0-9]+)" + // 3rd number (group = 4)
            "\\s+" + // blanks
            "([0-9]+)" + // 4th number (group = 5)
            "\\s+" + // blanks
            "([0-9]+)" + // 5th number (group = 6)
            "\\s+" + // blanks
            "([0-9]+)" + // 6th number (group = 7)
            "\\s+" + // blanks
            "([0-9]+)" + // 7th number (group = 8)
            "\\s+" + // blanks
            "([0-9]+)" + // 8th number (group = 9)
            "\\s+" + // blanks
            "([0-9]+)" + // 9th number (group = 10) -> bytes sent
            "\\s+" + // blanks
            "([0-9]+)" + // 10th number (group = 11) -> packets sent
            "\\s+" + // blanks
            "([0-9]+)" + // 11th number (group = 12)
            "\\s+" + // blanks
            "([0-9]+)" + // 12th number (group = 13)
            "\\s+" + // blanks
            "([0-9]+)" + // 13th number (group = 14)
            "\\s+" + // blanks
            "([0-9]+)" + // 14th number (group = 15)
            "\\s+" + // blanks
            "([0-9]+)" + // 15th number (group = 16)
            "\\s+" + // blanks
            "([0-9]+)" + // 16th number (group = 17)
            "$"); // end of the line
    
	private long wifiTransmitted_;
	private long initialWifiTransmitted_;
	private long mobileTransmitted_;
	private long initialMobileTransmitted_;
  private long wifiReceived_;
  private long initialWifiReceived_;
	private long mobileReceived_;
	private long initialMobileReceived_;

	// 
	public WifiDataUsageOld(){	
	  startSession();
	}
	// transmitted bytes over wifi interface
	public long getWifiTransmitted(){
		return wifiTransmitted_ - initialWifiTransmitted_;
	}
	// received bytes over wifi interface
	public long getWifiReceived(){
		return wifiReceived_ - initialWifiReceived_;
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
	  updateUsageCounters();
	  initialWifiTransmitted_ = wifiTransmitted_;
	  initialMobileTransmitted_ = mobileTransmitted_;
	  initialWifiReceived_ = wifiReceived_;
	  initialMobileReceived_ = mobileReceived_;
	}
	
	//	
	public void updateUsageCounters(){
		FileReader fstream = null;
        BufferedReader in = null;
        String wlanLine=null;
        String mobileLine=null;

        try {
            fstream = new FileReader(INTERFACE_FILE);
            if (fstream != null) {
                try {
                    in = new BufferedReader(fstream, 500);
                    String line;
                    while ((line = in.readLine()) != null) {
                    	if (line.substring(0, WIFI_INTERFACE_NAME.length()).equals(WIFI_INTERFACE_NAME)){
                    		wlanLine=line;
                    	} 
                    	else {
                    		for (String s : MOBILE_INTERFACE_NAMES) {
                    			if (line.contains(s)) {
                    				mobileLine = line;
                    			}
                    		}	
                    	}
                    }
                    in.close();
                    fstream.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Could not read from file '" + INTERFACE_FILE + "'.", e);
                }
            }
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "Could not open file '" + INTERFACE_FILE + "' for reading.", e);
        }
        //
        if (wlanLine!=null){
            Matcher matcher = LINE_PATTERN.matcher(wlanLine);
            if (matcher.matches()){
                String deviceName = matcher.group(1).trim();
                wifiReceived_ = Long.parseLong(matcher.group(2));
                wifiTransmitted_ = Long.parseLong(matcher.group(10)); 
                Log.i(LOG_TAG,"device: "+deviceName+" received "+wifiReceived_+" transmitted "+wifiTransmitted_);
            }
            matcher = LINE_PATTERN.matcher(mobileLine);
            if (matcher.matches()){
                String deviceName = matcher.group(1).trim();
                mobileReceived_ = Long.parseLong(matcher.group(2));
                mobileTransmitted_ = Long.parseLong(matcher.group(10)); 
                Log.i(LOG_TAG,"device: "+deviceName+" received "+mobileReceived_+" transmitted "+mobileTransmitted_);
            }
        }
        
	}
	

}
