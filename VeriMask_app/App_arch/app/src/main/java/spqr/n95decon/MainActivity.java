// Copyright: Yan Long, PhD Candidate, EECS Dept. of Umich. Contact: yanlong@umich.edu

// The App was designed for fast prototyping so good coding practices were not followed.
// There are many aspects in the code that can be improved and maybe optimized if needed.
// For example:
// (1) we use NodeManager as a static global data holder which actually breaks the
// rule of OOP although we didn't observe run-time issues.
// (2) We use SharedPreferences as the communication channel between different activities which
// might affect the performance and cause loss of BLE packets when there are thousands of nodes
// despite the super long 10s packet interval.
// We have only tested the BLE stability when running the App in the foreground. The App will
// keep the screen always on.

package spqr.n95decon;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements  SharedPreferences.OnSharedPreferenceChangeListener{

    private static final int REQUEST_FINE_LOCATION = 1;
    private Intent intentBLE;
    private SharedPreferences syncPreferences;
    private Toast toast;
    private Button startStopRecordingBtn, startNewRoundBtn, exportBtn;
    private CheckBox profBox;
    private TextView countDown;
    private CountDownTimer mCountDownTimer;
    private boolean flagExporeted  = true;
    private boolean profilingCycle = false;
    private GridView gridView;
    private NodesViewAdapter mNodesViewAdapter;

    public static final boolean SI7021 = true;
    public static final boolean SHT85 = false;    // SHT85 is not used in the node V2


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("WTF","MainActicvity onCreate");


        // init UI objects
        initViews(this);

        // Check if BLE is supported
        BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            while(true);
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            while(true);
        }
        mBluetoothManager = null;
        mBluetoothAdapter = null;

        // get all permissions dynamically
        if (! hasPermissions())
        {
            try{requestPermissions();} catch(Exception e) { e.printStackTrace(); }
        }

        // get the intents for BLE service and start it
        intentBLE = new Intent(this, BLEService.class);

        // use sharedPreference to sync UI and data management
        syncPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        syncPreferences.edit().putString("Info", "N;" + (-1)).apply();
    }

    @Override
    protected void onResume() {

        super.onResume();

        syncPreferences.registerOnSharedPreferenceChangeListener(this);

        updateUI();
    }

    // update Node listing UI here
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        String newPacketInfo = syncPreferences.getString("Info","?");
        if (newPacketInfo.substring(0, 1).equals("D"))
            Log.d("WTF","New data while recording, no need updating listing page");
        else if (newPacketInfo.substring(0, 1).equals("N"))
        {
            // newPacketInfo is in the form of "N;nodeID". Parse by the delimiter ";" and get nodeID
            String nodeID = newPacketInfo.split(";")[1];
            updateUI();
            toastNotice("New Node Added: #" + nodeID);
        }
        else if (newPacketInfo.substring(0, 1).equals("S"))
        {
            // newPacketInfo is in the form of "N;nodeID". Parse by the delimiter ";" and get nodeID
            String nodeID = newPacketInfo.split(";")[1];
            updateUI();
            toastNotice("Node Status Changed: #" + nodeID);
        }
    }

    public void updateUI() {
        mNodesViewAdapter.notifyDataSetChanged();
    }

    private void initViews(final MainActivity mainActivity) {

        setContentView(R.layout.listing_prof);
        // Keep the screen always on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // init gridview and attach adapter
        gridView = (GridView)findViewById(R.id.gridview);
        mNodesViewAdapter = new NodesViewAdapter(this);
        gridView.setAdapter(mNodesViewAdapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                Intent intentDetail = new Intent(mainActivity, DetailActivity.class);
                intentDetail.putExtra("DEVICE_ID", NodeManager.allNodes[position]+"");
                startActivity(intentDetail);
            }
        });


        // init the profiling checkbox and timer count down
        profBox = findViewById(R.id.profCheckBox);
        profBox.setChecked(false);
        profBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                profilingCycle = isChecked;
            }
        });
        // the timer countdown for cycle heating time (Tmh in the paper)
        countDown = findViewById(R.id.textCountDown);
        countDown.setText("00:00");


        // init the buttons
        startStopRecordingBtn = findViewById(R.id.startStopRecordingBtn);

        startStopRecordingBtn.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                String nrmsg;
                if (NodeManager.getAppStatus() == NodeManager.APP_STATUS_ADDINGNODE)
                {
                    setRecordingSettings();
                }
                else if (NodeManager.getAppStatus() == NodeManager.APP_STATUS_RECORDING)
                {
                    NodeManager.finishRecording();
                    startStopRecordingBtn.setText("Start Decon Recording");
                    stopService(intentBLE);
                    mCountDownTimer.cancel();
                    countDown.setText("00:00");
                    countDown.setTextColor(Color.parseColor("#000000"));

                    if (profilingCycle){
                        nrmsg= "Searching for optimized settings...";
                        deconOptimization();
                    } else {
                        nrmsg = "Ready for a new round.";
                    }
                    toastNotice("Recording Stopped. "+nrmsg);

                }
                else // IDLE state
                    toastNotice("Cannot start recording before starting a new round");
            }
        });

        startNewRoundBtn = findViewById(R.id.startNewRoundBtn);
        startNewRoundBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (NodeManager.getAppStatus() == NodeManager.APP_STATUS_IDLE)
                {
                    if (flagExporeted) {
                        NodeManager.startNewRound();
                        startService(intentBLE);
                        toastNotice("New Round Started");
                        updateUI();
                        // gotta have this init to avoid not notifying the first new node to main activity UI
                        syncPreferences.edit().putString("Info", "N;" + (-1)).apply();
                    }
                    else {
                        // pop-up window discard/export
                        DialogFragment dialog = new NewRoundDialogFragment();
                        dialog.show(getSupportFragmentManager(), "newround");
                    }
                    profBox.setEnabled(true);
                }

                else if (NodeManager.getAppStatus() == NodeManager.APP_STATUS_RECORDING)
                    toastNotice("Cannot Start New Round While Recording");
                else   // We can directly start a new round in AddingNode
                {
                    NodeManager.startNewRound();
                    stopService(intentBLE);
                    startService(intentBLE);
                    toastNotice("New Round Started");
                    updateUI();
                    // gotta have this init to avoid not notifying the first new node to main activity UI
                    syncPreferences.edit().putString("Info", "N;" + (-1)).apply();
                }
            }
        });

        exportBtn = findViewById(R.id.exportBtn);
        exportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (NodeManager.getAppStatus() == NodeManager.APP_STATUS_IDLE) {
                    try {
                        NodeManager.exportRecordingData();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    flagExporeted = true;
                    toastNotice("Data exported");
                }
                else
                    toastNotice("Cannot export data now");
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void deconOptimization() {
        final OptimSetting optSet = NodeManager.searchOptSetting();
        LayoutInflater inflater = getLayoutInflater();
        View dialoglayout = inflater.inflate(R.layout.profiling_result, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        final TextView sumText = (TextView) dialoglayout.findViewById(R.id.textSummary);
        final TextView tempText = (TextView) dialoglayout.findViewById(R.id.textTemp);
        final TextView failsText = (TextView) dialoglayout.findViewById(R.id.textFails);
        final TextView timeText = (TextView) dialoglayout.findViewById(R.id.textTmh);
        String sumString = "Based on the selected working time of " + (int) NodeManager.totalWorkTime/(60*1000)  +
                " minutes and the collected data, we suggest the following setting adjustments to reach the maximum of "
                + optSet.optNumCycs*optSet.optMPC +" masks decontaminated in " + optSet.optNumCycs + " cycles ("+optSet.optMPC+" masks per cycle): ";
        sumText.setText(sumString);
        String tempString = ""+optSet.optTemp;
        tempText.setText(tempString);
        String failString = String.join(", ", optSet.optFailNodes);
        failsText.setText(failString);
        String timeString = "The recommended total cycle time is "+optSet.optTmh+" min. Press OK if you accept the changes.";
        timeText.setText(timeString);
        builder.setView(dialoglayout);
        final AlertDialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                NodeManager.cycleTime = optSet.optTmh*60*1000;
                NodeManager.heatingDeviceSetTemp = optSet.optTemp;
                alertDialog.dismiss();
            }
        });
        alertDialog.show();
    }

    private void setRecordingSettings() {
        LayoutInflater inflater = getLayoutInflater();
        View dialoglayout = inflater.inflate(R.layout.recording_settings, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        // Initialize things
        final EditText tempLowerText = (EditText) dialoglayout.findViewById(R.id.temp_lower);
        final EditText tempUpperText = (EditText) dialoglayout.findViewById(R.id.temp_upper);
        final EditText rHLowerText = (EditText) dialoglayout.findViewById(R.id.RH_lower);
        final EditText rHUpperText = (EditText) dialoglayout.findViewById(R.id.RH_upper);
        final EditText deconTimeText = (EditText) dialoglayout.findViewById(R.id.timeDecon);
        final EditText lostTimeText = (EditText) dialoglayout.findViewById(R.id.timeLost);
        final EditText outTimeText = (EditText) dialoglayout.findViewById(R.id.timeOut);
        final EditText mhTimeText = (EditText) dialoglayout.findViewById(R.id.timeMH);
        final EditText devTempText = (EditText) dialoglayout.findViewById(R.id.devTemp);
        final EditText workTimeText = (EditText) dialoglayout.findViewById(R.id.timeTotal);

        mhTimeText.setText(""+(int) NodeManager.cycleTime/(60*1000));
        devTempText.setText(""+NodeManager.heatingDeviceSetTemp);
        workTimeText.setText(""+(int) NodeManager.totalWorkTime/(60*1000));

        //vars for the setting numbers
        final int[] tempSetVals = {-1,1000};
        final int[] rHSetVals = {-1,1000};
        final int[] deconTime = {30};
        final int[] lostTime = {2};
        final int[] outTime = {2};
        final int[] mhTime = {50};
        final int[] devTemp = {70};
        final int[] workTime = {240};


        builder.setView(dialoglayout);
        final AlertDialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Set and Start", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                if (!tempLowerText.getText().toString().equals(""))
                    tempSetVals[0] = Integer.parseInt(tempLowerText.getText().toString());
                if (!tempUpperText.getText().toString().equals(""))
                    tempSetVals[1] = Integer.parseInt(tempUpperText.getText().toString());
                if (!rHLowerText.getText().toString().equals(""))
                    rHSetVals[0] = Integer.parseInt(rHLowerText.getText().toString());
                if (!rHUpperText.getText().toString().equals(""))
                    rHSetVals[1] = Integer.parseInt(rHUpperText.getText().toString());
                if (!deconTimeText.getText().toString().equals(""))
                    deconTime[0] = Integer.parseInt(deconTimeText.getText().toString());
                if (!lostTimeText.getText().toString().equals(""))
                    lostTime[0] = Integer.parseInt(lostTimeText.getText().toString());
                if (!outTimeText.getText().toString().equals(""))
                    outTime[0] = Integer.parseInt(outTimeText.getText().toString());
                if (!mhTimeText.getText().toString().equals(""))
                    mhTime[0] = Integer.parseInt(mhTimeText.getText().toString());
                if (!devTempText.getText().toString().equals(""))
                    devTemp[0] = Integer.parseInt(devTempText.getText().toString());
                if (!workTimeText.getText().toString().equals(""))
                    workTime[0] = Integer.parseInt(workTimeText.getText().toString());

                if (tempSetVals[0] >= tempSetVals[1])
                    finish();
                if (rHSetVals[0] >= tempSetVals[1])
                    finish();

                NodeManager.tempLower = tempSetVals[0];
                NodeManager.tempUpper = tempSetVals[1];
                NodeManager.rhLower = rHSetVals[0];
                NodeManager.rhUpper= rHSetVals[1];
                NodeManager.deconTime = deconTime[0] * 60 * 1000;
                NodeManager.errorLostGracePeriod = lostTime[0] * 60 * 1000;
                NodeManager.errorLostCheckRoof = lostTime[0];
                NodeManager.errorFailureGracePeriod = outTime[0] * 60 * 1000;
                NodeManager.heatingDeviceSetTemp = devTemp[0];
                NodeManager.cycleTime = mhTime[0] * 60 * 1000;
                NodeManager.totalWorkTime = workTime[0] * 60 * 1000;

                alertDialog.dismiss();

                toastNotice("Recording Started");
                startStopRecordingBtn.setText("Stop Recording");
                NodeManager.startRecording();
                flagExporeted = false;
                updateUI();
                profBox.setEnabled(false);   // cannot change the profiling status after recording started
                mCountDownTimer = new CountDownTimer((long) NodeManager.cycleTime, 1000) {

                    public void onTick(long millisUntilFinished) {
                        countDown.setText("" +new SimpleDateFormat("mm:ss").format(new Date( millisUntilFinished)));
                        countDown.setTextColor(Color.parseColor("#000000"));
                    }
                    public void onFinish() {
                        countDown.setText("00:00");
                        countDown.setTextColor(Color.parseColor("#ff0000"));
                    }
                }.start();
            }
        });
        alertDialog.show();

    }



    // permissioning stuff
    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean hasPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestPermissions() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_FINE_LOCATION);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults[0] + grantResults[1] + grantResults[2] != PackageManager.PERMISSION_GRANTED){
            toastNotice("Permissions not granted. Exiting App! Please give permissions when enter App again");
            Handler mScanHandler = new Handler();
            mScanHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    Log.d("WTF","Timer up");
                    finish();
                }}, 3000);
        }

    }

    // cliche UI stuff
    @Override
    protected void onPause() {
        syncPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
    @Override
    protected void onStart() {
        super.onStart();
    }
    @Override
    protected void onRestart() {
        super.onRestart();
    }
    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        stopService(intentBLE);
        super.onDestroy();
    }
    public void toastNotice(String text)
    {
        if (toast != null)
            toast.cancel();
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }


}
