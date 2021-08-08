package spqr.n95decon;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class BLE_scanner{
    
    private boolean NOTIFY_ALL_PACKETS = false;
    private boolean TOTAL_COUNT_PP_ENABLE = false;

    private Context context;
    private SharedPreferences syncPreferences;
    private long last_time = 0; // store the last timestamp of receiving a node's packet in this scanning round
    private int totalCount = 0;  // don't change this. used for testing
    private MyLEScanCallback mScanCallback;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private DeconVerifier mVerifier;
    public boolean scanning;

    // scanner instance constructor
    public BLE_scanner(Context context, DeconVerifier mVerifier)
    {
        this.context = context;
        this.scanning = false; // scanning should be terminated before start scanning again in UI
        this.syncPreferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.mVerifier = new DeconVerifier(context);
    }


    public void startScanning() {
        if (!scanning)
        {
            // get the resources for BLE
            mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();
            if (mBluetoothAdapter == null || mBluetoothManager == null) {
                Log.d("WTF", "Having errors getting BLE resources");
            }
            // enable BLE
            if (!mBluetoothAdapter.isEnabled()) {
                try {
                    mBluetoothAdapter.enable();
                } catch (Exception e) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(enableBtIntent);
                }
            }
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            mScanCallback = new MyLEScanCallback();
            // Filters and scan settings
            ScanFilter scanFilter = new ScanFilter.Builder()
                    .setDeviceAddress("E5:E7:27:01:29:27")
                    .build();
            List<ScanFilter> filters = new ArrayList<>();
//            filters.add(scanFilter);  // !!!!!!!!!! Using the filter would make the app stuck !!!!!!!
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            // start BLE scanning
            try { mBluetoothLeScanner.startScan(filters, settings, mScanCallback); } catch (Exception e) { e.printStackTrace(); }
            scanning = true;
            Log.d("WTF", "BLE scanning started");
        }
        else
            Log.d("WTF", "Already scanning but clicked! Something went wrong!");
    }

    public void stopScanning() {
        if (scanning) {
            // stop scanning
            mBluetoothLeScanner.stopScan(mScanCallback);
            // release resources
            mBluetoothManager = null;
            mBluetoothAdapter = null;
            scanning = false;
            Log.d("WTF", "BLE scanning stopped");
        }
    }


    private class MyLEScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if(NOTIFY_ALL_PACKETS)
                Log.d("WTF","ScanCallBack in thread: " + Thread.currentThread().getName());
            HandleResults(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                HandleResults(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d("WTF","BLE Scan Failed with code " + errorCode);
        }

        public void HandleResults(ScanResult result) {
            handlePacket(result.getDevice(), result.getRssi(),
                    Objects.requireNonNull(result.getScanRecord()).getBytes());
        }
    }

    // This is the core. We parse the BLE packets here
    private void handlePacket(BluetoothDevice device, int rssi, byte[] scanRecord) {
        // scanRecord contains everything behind MAC and before CRC, i.e, the 31-byte adv data part

        if(NOTIFY_ALL_PACKETS)
            Log.d("WTF","Parsing in thread: " + Thread.currentThread().getName());

        int company_id_start;       // 9 for connectable
        long this_time = 0;

        // !!! The two dev boards have different comp id (59 for board1 and ff for board2)
        if(NodeManager.getAppStatus() != NodeManager.APP_STATUS_IDLE &&
                ((scanRecord[5] == (byte)0xff && scanRecord[5 + 1] == (byte)0x00) ||
                (scanRecord[9] == (byte)0xff && scanRecord[9 + 1] == (byte)0x00))         )
        {

            if (scanRecord[5] == (byte)0xff) company_id_start = 5;
            else company_id_start = 9;

            NodeManager.totalNodePackets ++;

            Log.d("WTF","---------------------------------------" );
            Log.d("WTF","Node pkt received!");
            Log.d("WTF","Addr: " +  device.getAddress());
            String nodeID = scanRecord[company_id_start+2] + NodeManager.nodeIDBase + "";  // !!! node base if for testing !!!
            Log.d("WTF","Node: " +  nodeID);
            String DataString = byteToHexString(scanRecord[company_id_start+3]) + byteToHexString(scanRecord[company_id_start+4]) +
                    byteToHexString(scanRecord[company_id_start+5]) + byteToHexString(scanRecord[company_id_start+6]);
            Log.d("WTF","Data: " +  DataString );
            this_time = System.currentTimeMillis();
            Log.d("WTF","Interval from last packet: " +  (this_time - last_time));
            last_time = this_time;
            Log.d("WTF","---------------------------------------" );

            if (NodeManager.getAppStatus() == NodeManager.APP_STATUS_ADDINGNODE)
            {
                if (NodeManager.addNode(nodeID, device)) // true if this new node is discovered for the first time
                {
                    syncPreferences.edit().putString("Info", "N;" + nodeID).apply();  // sync: new node
                }
            }
            else if (NodeManager.getAppStatus() == NodeManager.APP_STATUS_RECORDING)
            {
                Log.d("PKTA","P1:"+System.currentTimeMillis());
                int humidityValsi7021 = 0;
                float temperatureValsi7021 = 0;
                int humidityValsht85 = 0;
                float temperatureValsht85 = 0;
                if (MainActivity.SI7021) {
                    humidityValsi7021 = totalCount + Math.round(calcHumSI7021(Arrays.copyOfRange(scanRecord, company_id_start + 3, company_id_start + 5)));
                    Log.d("WTF", "Humidity SI7021: " + humidityValsi7021);
                    temperatureValsi7021 = totalCount + calcTempSI7021(Arrays.copyOfRange(scanRecord, company_id_start + 5, company_id_start + 7));
                    Log.d("WTF", "Temperature SI7021: " + temperatureValsi7021);
                }
                if (MainActivity.SHT85) {
                    humidityValsht85 = totalCount + Math.round(calcHumSHT85(Arrays.copyOfRange(scanRecord, company_id_start + 7, company_id_start + 9)));
                    Log.d("WTF", "Humidity SHT85: " + humidityValsht85);
                    temperatureValsht85 = totalCount + calcTempSHT85(Arrays.copyOfRange(scanRecord, company_id_start + 9, company_id_start + 11));
                    Log.d("WTF", "Temperature SHT85: " + temperatureValsht85);
                }
                if (TOTAL_COUNT_PP_ENABLE)
                    totalCount ++; // !!! total count is used to test with beacon to get changing data !!!
                NodeMeasurements mNodeMeasurements = new NodeMeasurements(this_time,
                        humidityValsi7021, temperatureValsi7021, humidityValsht85, temperatureValsht85);
                if (NodeManager.addOnePacketData(nodeID, mNodeMeasurements)) {
                    // Check node status upon receiving a new packet
                    mVerifier.checkDecon(nodeID);
                    syncPreferences.edit().putString("Info", "D;" + nodeID + ";" + this_time + ";"
                            + humidityValsi7021 + ";" + temperatureValsi7021 + ";" + humidityValsht85 + ";" + temperatureValsht85 + ";" + rssi).apply();  // sync: new data
                }

            }

        }


    }

    static public long calcHumSI7021(byte[] humBytes)
    {
        long humraw = ((humBytes[0] & 0xff) << 8) + (humBytes[1] & 0xff ); // bytes to unsigned byte in an integer.
        Log.d("WTF","HumidityRaw: "+ humraw);
        long temp = 125 * humraw;
        temp = temp >> 16;
        return (temp - 6);
    }

    static public float calcTempSI7021(byte[] tempBytes)
    {
        long tempraw = ((tempBytes[0] & 0xff) << 8) | (tempBytes[1] & 0xff ); // bytes to unsigned byte in an integer.
        Log.d("WTF","HumidityRaw: "+ tempraw);
        return (float) (Math.round(((float)((17572 * tempraw) >> 16) - 4685)/100 * 10) / 10.0);
    }

    static public float calcHumSHT85(byte[] humBytes)
    {
        long humraw = ((humBytes[0] & 0xff) << 8) + (humBytes[1] & 0xff ); // bytes to unsigned byte in an integer.
        Log.d("WTF","HumidityRaw: "+ humraw);
        return (float) 100 * humraw / 65535;
    }

    static public float calcTempSHT85(byte[] tempBytes)
    {
        long tempraw = ((tempBytes[0] & 0xff) << 8) | (tempBytes[1] & 0xff ); // bytes to unsigned byte in an integer.
        Log.d("WTF","HumidityRaw: "+ tempraw);
        return (float) (Math.round((float) (-45 + 175.0 * tempraw / 65535) * 10) / 10.0);
    }



    static public String byteToHexString(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }


}
