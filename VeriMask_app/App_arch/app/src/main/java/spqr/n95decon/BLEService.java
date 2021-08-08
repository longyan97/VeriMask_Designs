// this one is to test thread/or not in service via class

package spqr.n95decon;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;

public class BLEService extends Service {

    BLE_scanner mBLE_scanner;
    DeconVerifier mVerifier;
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    Handler mHandlerCheck;
    Handler mHandlerAutoRestart;
    public final int SCANNING_CHECK_INTERVAL = 1000*30;    // check if BLE working every 30s
    private int errorLostCheckCount = 0;
    public final int SCANNING_AUTORESATRT_INTERVAL = 1000*60*25;    // auto-restart scanning every 25min to avoid the OS kill it
    static public final boolean FOREGROUND = false;
    private long totalPacketsLastCheck = -1;


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    // onCreate() is only called when the process starts
    public void onCreate() {
        Log.d("WTF","BLE service onCreate");
        super.onCreate();

    }

    @Override
    // onStartCommand() is called whenever a client calls startService()
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("WTF","BLE service onStartCommand");
        if (mVerifier == null) {
            mVerifier = new DeconVerifier(this);
        }
        if (mBLE_scanner == null) {
            mBLE_scanner = new BLE_scanner(this, mVerifier);
            mBLE_scanner.startScanning();
        }


        if (FOREGROUND)
            makeForegroundService();

        mHandlerCheck = new Handler();
        mHandlerAutoRestart = new Handler();
        scanningAutoRestarter.run();
        scanningChecker.run();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d("WTF","BLE service onDestroy");
        if (mBLE_scanner != null) {
            mBLE_scanner.stopScanning();
            mBLE_scanner = null;
        }
        if (mVerifier != null) {
            mVerifier = null;
        }
        mHandlerCheck.removeCallbacks(scanningChecker);
        mHandlerAutoRestart.removeCallbacks(scanningAutoRestarter);
        super.onDestroy();
    }



    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void makeForegroundService()
    {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText("Decontaminating")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    Runnable scanningChecker = new Runnable() {
        @Override
        public void run() {
            if (NodeManager.getAppStatus() != NodeManager.APP_STATUS_IDLE) {
                Log.d("WTF_CHECK", "Scanning restart checking!");
                Log.d("WTF_CHECK", NodeManager.totalNodePackets + " vs " + totalPacketsLastCheck);
                if (NodeManager.totalNodePackets - totalPacketsLastCheck <= 0) {
                    Log.d("WTF_CHECK", "Scanning pause detected! Restarting scanning");
                    mBLE_scanner.stopScanning();
                    mBLE_scanner.startScanning();

                } else
                    Log.d("WTF_CHECK", "Scanning np!");

                // check the error_lost states for each node
                if (NodeManager.getAppStatus() == NodeManager.APP_STATUS_RECORDING)
                {
                    if (errorLostCheckCount >= NodeManager.errorLostCheckRoof) {
                        errorLostCheckCount = 0;
                        mVerifier.checkComm();
                    } else
                        errorLostCheckCount ++;

                }

            }
            else
                Log.d("WTF_CHECK", "State error: checking scanning while idle!");

            totalPacketsLastCheck = NodeManager.totalNodePackets;
            mHandlerCheck.postDelayed(scanningChecker, SCANNING_CHECK_INTERVAL);
        }
    };

    Runnable scanningAutoRestarter = new Runnable() {
        @Override
        public void run() {
            if (NodeManager.getAppStatus() != NodeManager.APP_STATUS_IDLE) {
                Log.d("WTF_CHECK", "Scanning auto restarting after 25min!");
                mBLE_scanner.stopScanning();
                try {
                    NodeManager.exportRecordingData();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mBLE_scanner.startScanning();
                mHandlerAutoRestart.postDelayed(scanningAutoRestarter, SCANNING_AUTORESATRT_INTERVAL);
            }
            else
                Log.d("WTF_CHECK", "State error: auto restarting scanning while idle!");
        }
    };

}
