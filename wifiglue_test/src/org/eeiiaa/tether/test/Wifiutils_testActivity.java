package org.eeiiaa.tether.test;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import edu.mit.media.viral.test.R;
import org.eeiiaa.tether.WifiApManager;
import org.eeiiaa.tether.WifiApManager.MobileApListener;
import org.eeiiaa.wifi.Wifi;
import org.eeiiaa.wifi.WifiConnector;
import org.eeiiaa.wifi.WifiConnector.ConnectionInfo;
import org.eeiiaa.wifi.WifiConnector.OnConnectionListener;
import org.eeiiaa.wifi.WifiConnector.OnScanResultsListener;

/**
 * test wifiutils - example for usage wifi test service - example for usage of
 * external service
 * 
 * @author eyalto
 * 
 */
public class Wifiutils_testActivity extends Activity implements OnConnectionListener, OnScanResultsListener, MobileApListener {

  private WifiConnector con;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    setTitle(R.string.tether_title);
    //
    con = new WifiConnector(this, WifiConnector.GET_ALL);

    //
    // handling main ui
    //

    // connect button
    ((Button) findViewById(R.id.btn_connect)).setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        String ssid = ((TextView) findViewById(R.id.netssid)).getText().toString();
        String pwd = ((TextView) findViewById(R.id.netpwd)).getText().toString();
        con.setSsid(ssid);
        con.setPwd(pwd);
        con.setOnConnectionListener(Wifiutils_testActivity.this);
        con.connect();
      }
    });
    // create button
    ((Button) findViewById(R.id.btn_create)).setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        String ssid = ((TextView) findViewById(R.id.netssid)).getText().toString();
        String pwd = ((TextView) findViewById(R.id.netpwd)).getText().toString();
        if (mAP == null) {
          mAP = new WifiApManager(Wifiutils_testActivity.this);
        }
        mAP.setMobileApListener(Wifiutils_testActivity.this);
        mAP.setWifiApEnabled(ssid, pwd, true);
      }
    });
    // stop button
    ((Button) findViewById(R.id.btn_stop)).setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        String ssid = ((TextView) findViewById(R.id.netssid)).getText().toString();
        String pwd = ((TextView) findViewById(R.id.netpwd)).getText().toString();
        if (mAP == null) {
          mAP = new WifiApManager(Wifiutils_testActivity.this);
        }
        mAP.setWifiApEnabled(ssid, pwd, false);
        TextView tx = (TextView) findViewById(R.id.txv);
        tx.setText("");
      }
    });
    // scan button
    ((Button) findViewById(R.id.scan)).setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        con.setOnScanResultsListener(Wifiutils_testActivity.this);
        con.scan();
      }

    });
    // forget button
    ((Button) findViewById(R.id.forget)).setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        String ssid = ((TextView) findViewById(R.id.netssid)).getText().toString();
        con.setSsid(ssid);
        con.setOnConnectionListener(Wifiutils_testActivity.this);
        con.forget();
        TextView tx = (TextView) findViewById(R.id.txv);
        tx.setText("forgotting network: " + ssid);
      }
    });

    // disconnect button
    ((Button) findViewById(R.id.disconnect)).setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        String ssid = ((TextView) findViewById(R.id.netssid)).getText().toString();
        con.setSsid(ssid);
        con.setOnConnectionListener(Wifiutils_testActivity.this);
        boolean disconnectOK = con.disconnect();
        TextView tx = (TextView) findViewById(R.id.txv);
        tx.setText("disconnect network: " + ssid + " return " + String.valueOf(disconnectOK));
      }
    });

    // test button
    ((Button) findViewById(R.id.test)).setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        String iface = Wifi.getWifiIface();
        TextView tx = (TextView) findViewById(R.id.txv);
        tx.setText("interface ip/mask = "+iface);
      }
    });

  }

  //
  // activity lifecycle management
  //
  @Override
  public void onStart() {
    super.onStart();

  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public void onStop() {
    if (mAP != null) {
      mAP.cleanup();
      mAP = null;
    }
    
    super.onStop();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

  }

  //
  // WifiConnector callbacks
  //
  @Override
  public void connectionEstablished(final ConnectionInfo info) {
    tx = (TextView) findViewById(R.id.txv);
    final String myIp = ((info.dhinf.ipAddress >> 0) & 0xFF) + "." + ((info.dhinf.ipAddress >> 8) & 0xFF) + "."
        + ((info.dhinf.ipAddress >> 16) & 0xFF) + "." + ((info.dhinf.ipAddress >> 24) & 0xFF);
    final String gwIp = ((info.dhinf.gateway >> 0) & 0xFF) + "." + ((info.dhinf.gateway >> 8) & 0xFF) + "."
        + ((info.dhinf.gateway >> 16) & 0xFF) + "." + ((info.dhinf.gateway >> 24) & 0xFF);
    runOnUiThread(new Runnable() {
      public void run() {
        tx.setText("Connected:" + "\n" + "SSID = " + info.winf.getSSID() + "\n" + "My IP = " + myIp + "\n" + "Gateway IP = " + gwIp + "\n"
            + "Link speed =" + info.winf.getLinkSpeed() + "\n" + "Rssi =" + info.winf.getRssi() + "\n");
      }
    });

  }

  @Override
  public void connectionFailed() {
    tx = (TextView) findViewById(R.id.txv);
    runOnUiThread(new Runnable() {
      public void run() {
        tx.setText("connection failed");
      }
    });
  }

  @Override
  public void disconnected() {

    runOnUiThread(new Runnable() {
      public void run() {
        tx = (TextView) findViewById(R.id.txv);
        tx.setText(tx.getText() + "\n transmitted\n received");
      }
    });
  }

  @Override
  public void networkLost(WifiConnector con, NetworkInfo networkInfo) {
    con.forget();
    runOnUiThread(new Runnable() {
      public void run() {
        tx = (TextView) findViewById(R.id.txv);
        tx.setText(tx.getText() + "\n network Lost - \n forgetting lost configuration");
      }
    });
  }

  @Override
  public void forgot(final boolean sucess) {
    runOnUiThread(new Runnable() {
      public void run() {
        tx = (TextView) findViewById(R.id.txv);
        tx.setText(tx.getText() + "\n forgot success = " + sucess + "\n transmitted\n received");
      }
    });
  }

  @Override
  public void scanResultsJSON(String json) {
    updateText = new String(json);
    tx = (TextView) findViewById(R.id.txv);
    runOnUiThread(new Runnable() {
      public void run() {
        tx.setText(updateText);
      }
    });
  }

  //
  // WifiApManager callbacks
  //
  @Override
  public void mobileApEnabled() {
    runOnUiThread(new Runnable() {
      public void run() {
        // give it some time to complete dhcp
        (new Handler()).postDelayed(new Runnable () {
          public void run() {      
            String ifAddress = Wifi.getWifiIface();
            String ipAddress = ifAddress.substring(0, ifAddress.indexOf('/'));
            String ipMask = ifAddress.substring(ifAddress.indexOf('/')+1);
            tx = (TextView) findViewById(R.id.txv);
            tx.setText("my ip: " + ipAddress+"\n"+" mask: "+ipMask);
          }
        },5000);
      }
    });
  }

  @Override
  public void mobileApDisabled() {
    runOnUiThread(new Runnable() {
      public void run() {
        tx = (TextView) findViewById(R.id.txv);
        tx.setText(tx.getText() + "\n transmitted = \n received");
      }
    });
  }

  @Override
  public void devicesConnected(HashMap<String, String> devices) {
    int i = 1;
    String text = new String();
    tx = (TextView) findViewById(R.id.txv);

    for (Map.Entry<String, String> entry : devices.entrySet()) {
      text += "Device " + i + ": IP: " + entry.getValue() + " MAC: " + entry.getKey() + "\n";
      i++;
    }
    final String t_txt = new String(text);
    runOnUiThread(new Runnable() {
      public void run() {
        tx.setText(t_txt);
      }
    });
  }

  //
  // private members
  //
  private String updateText;
  private TextView tx;
  private WifiApManager mAP;
  private final static String LOG_TAG = "Wifiglue::Test";

}