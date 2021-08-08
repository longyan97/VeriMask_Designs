package spqr.n95decon;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.lang.Math;

public class DetailActivity extends AppCompatActivity implements  SharedPreferences.OnSharedPreferenceChangeListener{

    private String nodeID;
    private SharedPreferences syncPreferences;
    private Toast toast;
    Context context;
    NodeGattHandler mNodeGattHandler;




    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("WTF","onCreate in DetailActivity");
        context = this;

        setContentView(R.layout.detail);
        // Keep the screen always on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // use sharedPreference to sync UI and data management
        syncPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    }


    @Override
    protected void onResume() {
        Log.d("PKTA","U4:"+System.currentTimeMillis());
        Log.d("WTF", "DetailActivity onResume");
        super.onResume();

        nodeID = getIntent().getStringExtra("DEVICE_ID");    // get which node is chosen passed by MainActivity via intent

        syncPreferences.registerOnSharedPreferenceChangeListener(this);

        updateUI();

        // !!! added. connection part
        // We didn't use this in the end. This is for active connection. It works.
        mNodeGattHandler = new NodeGattHandler(this,
                Objects.requireNonNull(NodeManager.getNodeData(nodeID)).getBleDevice());
        final Button connectBtn = findViewById(R.id.btn_connect);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mNodeGattHandler.mConnected) {
                    Log.d("BLEConn", "About to connect!!!");
                    mNodeGattHandler.connectDevice();
                    connectBtn.setText("Connecting");
                } else {
                    Log.d("BLEConn", "About to disconnect!!!");
                    connectBtn.setText("Disconnecting");
                    mNodeGattHandler.disconnectGattServer();
                }
            }
        });
        // !!!
    }



    @SuppressLint("SetTextI18n")
    private void updateUI() {
        
        long timeStampBase = 0;
        LineChart chartTemp = findViewById(R.id.LC_temp);
        LineChart chartHum = findViewById(R.id.LC_hum);
        List<Entry> entriesTempSI7021 = new ArrayList<>();
        List<Entry> entriesTempSHT85 = new ArrayList<>();
        List<List<Entry>> entriesTempAll = new ArrayList<>();
        List<Entry> entriesHumSI7021 = new ArrayList<>();
        List<Entry> entriesHumSHT85 = new ArrayList<>();
        List<List<Entry>> entriesHumAll = new ArrayList<>();
        TextView textTemp = findViewById(R.id.text_temp);
        TextView textHum = findViewById(R.id.text_hum);
        TextView textStatus = findViewById(R.id.text_nodeStatusDetail);
        TextView textPktNum = findViewById(R.id.text_pktnum);
        TextView textNodeID = findViewById(R.id.text_nodeID);
        TextView textDeconTime = findViewById(R.id.text_decontime);


        NodeData mNodeData = NodeManager.getNodeData(nodeID);
        assert mNodeData != null;
        if (mNodeData.getNodeRecordingData().packetNum != 0)
            timeStampBase = mNodeData.getNodeRecordingData().timestamp[0];
        // This is clumsy right now. No need recreating the entry list everytime
        for (int i = 0; i< mNodeData.getNodeRecordingData().packetNum; i++)
        {
            if (MainActivity.SI7021) {
                entriesTempSI7021.add(new Entry((float) ((mNodeData.getNodeRecordingData().timestamp[i] - timeStampBase)*0.001),
                        mNodeData.getNodeRecordingData().temperatureSI7021[i]));
                entriesHumSI7021.add(new Entry((float) ((mNodeData.getNodeRecordingData().timestamp[i] - timeStampBase)*0.001),
                        mNodeData.getNodeRecordingData().humiditySI7021[i]));
            }

            if (MainActivity.SHT85) {
                entriesTempSHT85.add(new Entry((float) ((mNodeData.getNodeRecordingData().timestamp[i] - timeStampBase)*0.001),
                        mNodeData.getNodeRecordingData().temperatureSHT85[i]));
                entriesHumSHT85.add(new Entry((float) ((mNodeData.getNodeRecordingData().timestamp[i] - timeStampBase)*0.001),
                        mNodeData.getNodeRecordingData().humiditySHT85[i]));
            }
        }

        entriesTempAll.add(entriesTempSI7021);
        entriesTempAll.add(entriesTempSHT85);
        entriesHumAll.add(entriesHumSI7021);
        entriesHumAll.add(entriesHumSHT85);

        // set each chart
        setGraph(chartTemp, entriesTempAll, "Temperature", new String[]{"SI7021", "SHT85"}, new int[]{Color.RED, 0xFFCE8500}, R.drawable.fade_red, 65f);
        chartTemp.getDescription().setText("");
        setGraph(chartHum, entriesHumAll, "Humidity", new String[]{"SI7021", "SHT85"}, new int[]{Color.BLUE, 0xFF00DD6F}, R.drawable.fade_blue, 65f);
        chartHum.getDescription().setText("");



        if (mNodeData.getNodeRecordingData().packetNum != 0) {
            // display all 0 in the text box if sensor not enabled and thus no data added to the entry list in the above codes
            String strTemp = "";
            String strHum = "";
            if (MainActivity.SI7021 && !MainActivity.SHT85){
                strTemp += mNodeData.getNodeRecordingData().temperatureSI7021[entriesTempSI7021.size() - 1];
                strHum += mNodeData.getNodeRecordingData().humiditySI7021[entriesHumSI7021.size() - 1];
            } else if (!MainActivity.SI7021 && MainActivity.SHT85){
                strTemp += mNodeData.getNodeRecordingData().temperatureSHT85[entriesTempSI7021.size() - 1];
                strHum += mNodeData.getNodeRecordingData().humiditySHT85[entriesHumSI7021.size() - 1];
            } else{
                strTemp += (entriesTempSI7021.size() != 0 ? mNodeData.getNodeRecordingData().temperatureSI7021[entriesTempSI7021.size() - 1] : 0) + " ; " +
                        (entriesTempSHT85.size() != 0 ? mNodeData.getNodeRecordingData().temperatureSHT85[entriesTempSHT85.size() - 1] : 0);
                strHum += (entriesHumSI7021.size() != 0 ? mNodeData.getNodeRecordingData().humiditySI7021[entriesHumSI7021.size() - 1] : 0) + " ; " +
                        (entriesHumSHT85.size() != 0 ? mNodeData.getNodeRecordingData().humiditySHT85[entriesHumSHT85.size() - 1] : 0);
            }

            textTemp.setText(strTemp);
            textHum.setText(strHum);

        }
        textStatus.setText(NodeData.statusNames[mNodeData.getNodeStatus()]);
        textPktNum.setText(""+mNodeData.getNodeRecordingData().packetNum);
        textNodeID.setText(nodeID);
        textDeconTime.setText( ""+ Math.floor(10*mNodeData.getInRangeTime()/(60*1000))/10 );
    }


    public void setGraph(LineChart chart, List<List<Entry>> sensorEntries, String measure, String[] sensors, int[] graphColors, int fill, float VisibleXRange){
        List<ILineDataSet> dataSets = new ArrayList<>();
        LineDataSet tempLineDataSet;
        for (int i=0; i < sensorEntries.size();i++) {
            tempLineDataSet = new LineDataSet(sensorEntries.get(i), measure + sensors[i]);
            setGraphDesign(tempLineDataSet, graphColors[i], fill);
            dataSets.add(tempLineDataSet);
        }

        LineData data = new LineData(dataSets);
        chart.setData(data);
        chart.invalidate();
    }
    public void setGraphDesign(LineDataSet lineDataSet, int graphColor, int fill){
        lineDataSet.setDrawCircles(false);
        lineDataSet.setColor(graphColor);
        lineDataSet.setLineWidth(2.0f);
        if (! (MainActivity.SI7021 && MainActivity.SHT85)) {
            lineDataSet.setDrawFilled(true);
            if (Utils.getSDKInt() >= 18) {
                // fill drawable only supported on api level 18 and above
                Drawable drawable = ContextCompat.getDrawable(this, fill);
                lineDataSet.setFillDrawable(drawable);
            }
        }else{lineDataSet.setDrawFilled(false);}
        lineDataSet.setDrawValues(false);
    }
    



    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // new packet for this node is detected

        String newPacketInfo = syncPreferences.getString("Info","?");
        if (newPacketInfo.substring(0, 1).equals("N"))
            Log.d("WTF","New node while recording, impossible, state transition error!");
        else if (newPacketInfo.substring(0, 1).equals("D"))
        {
            // only update this detail page if the new packet belongs to this node
            String newPacketNodeID = newPacketInfo.split(";")[1];
            if (newPacketNodeID.equals(nodeID))
            {
                updateUI();
                // toast display is for debugging
                if (toast != null)
                    toast.cancel();
                toast = Toast.makeText(this, newPacketInfo, Toast.LENGTH_SHORT);
                toast.show();
            }
            else
                Log.d("WTF","New packet does not belong to this node, no need updating");
        }
        else if (newPacketInfo.substring(0, 1).equals("S"))
        {
            // newPacketInfo is in the form of "N;nodeID". Parse by the delimiter ";" and get nodeID
            String newPacketNodeID = newPacketInfo.split(";")[1];
            if (newPacketNodeID.equals(nodeID)) {
                updateUI();
                // toast display is for debugging
                if (toast != null)
                    toast.cancel();
                toast = Toast.makeText(this, "Node Status Changed: #" + newPacketNodeID, Toast.LENGTH_SHORT);
                toast.show();
            }
        }

    }




    @Override
    protected void onPause() {
        Log.d("PKTA","U5:"+System.currentTimeMillis());
        Log.d("WTF","DetailActivity onPause");
        syncPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d("WTF","DetailActivity onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        Log.d("WTF","DetailActivity onStart");
        super.onStart();
    }

    @Override
    protected void onRestart() {
        Log.d("WTF","DetailActivity onRestart");
        super.onRestart();
    }

    @Override
    protected void onStop() {
        Log.d("WTF","DetailActivity onStop");
        super.onStop();
    }


}
