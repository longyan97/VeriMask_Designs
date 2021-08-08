
package spqr.n95decon;


import android.bluetooth.BluetoothDevice;
import android.graphics.Path;
import android.os.Environment;
import android.util.Log;

import org.w3c.dom.Node;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class NodeManager {

    // Any operations should be governed by the state machine
    static public final int APP_STATUS_IDLE = 0;            // between stop recording -> start new round.
    static public final int APP_STATUS_ADDINGNODE = 1;      // between start new round -> start recording
    static public final int APP_STATUS_RECORDING = 2;       // between start recording -> stop recording
    static public int appStatus = APP_STATUS_IDLE;    // default into adding node when app launched
    static public final String FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/MaskDeconData/";

    static public int nodeIDBase = 0;    // don't change this. only for testing.

    static public int[] allNodes = {};     // used to store all nodes and sort, in order to provide info to NodesViewAdapter
    static public Map<String, NodeData> nodeDataMap = new HashMap<String, NodeData>();      // used to store data of a single round of recording
    static public long totalNodePackets = 0;


    // below are the decon settings
    static public int tempLower = -1;
    static public int tempUpper = 1000;
    static public int rhLower = -1;
    static public int rhUpper = 1000;
    static public float deconTime = 30*60*1000;   // 30min by default,  Tdecon in the paper
    static public float cycleTime = 50*60*1000;   // 50min by default, Tmh in the paper
    static public float totalWorkTime = 240*60*1000;   // 240min by default, Tmh in the paper
    static public int errorLostGracePeriod = 2*60*1000;   // 2min by default (worst case)
    static public int errorLostCheckRoof = 2; // used by BLE servce. 2min worst case scenario by default (check every 1min)
    static public int errorFailureGracePeriod = 2*60*1000;    // 2min by default
    static public int heatingDeviceSetTemp = 70;

    /* Group: UI interaction, state transitions
    ***************************************************************************************
     */
    static public int getAppStatus(){
        return appStatus;
    }

    // called by the Main activity when "StartNewRound" button is hit
    static public void startNewRound(){
        Log.d("WTF","Start New Round");
        nodeDataMap.clear();
        allNodes = new int[]{};
        totalNodePackets = 0;
        appStatus = APP_STATUS_ADDINGNODE;
    }

    // called by the Main activity when the "StartRecording" button is hit and going to log data in the next round
    static public void startRecording(){
        if (appStatus == APP_STATUS_ADDINGNODE) {
            Log.d("WTF","Start Recording");
            appStatus = APP_STATUS_RECORDING;
            for (NodeData node : nodeDataMap.values()) {
                node.setNodeStatus(node.NODE_STATUS_DECON_ESTABLISH);
            }
        }
        else
            Log.d("WTF","ERROR! Wrong state transition, now in " + appStatus);
    }

    // called by the Main activity when the "StopRecording" button is hit
    static public void finishRecording() {
        if (appStatus == APP_STATUS_RECORDING) {
            Log.d("WTF", "Finish Recording");
            appStatus = APP_STATUS_IDLE;
        }
        else
            Log.d("WTF","ERROR! Wrong state transition, now in " + appStatus);
    }

    // called by the Main activity when the "Export" button is hit
    // export all nodes' data of the last recording to csv files (one node one file)
    // storage format： MaskDeconData/TestRoundName/nodeID.csv
    static public void exportRecordingData() throws IOException {
        if (appStatus == APP_STATUS_IDLE) {
            // TODO: user input test round name , using round1/ as the placeholder
            String roundFolderName = FILE_PATH + "round1/";
            File roundFolder = new File(roundFolderName);
            if (!roundFolder.exists()) {
                roundFolder.mkdirs();
            }
            Log.d("WTF","Export! round folder: "+roundFolderName);
            for (Map.Entry<String, NodeData> node : nodeDataMap.entrySet())
            {
                nodeMeasurements2CSV(node.getKey(), node.getValue().getNodeRecordingData());
            }
        }
        else{
            // TODO: pop-up alter
            Log.d("WTF","Wrong! Can only export data when recording is completed, now in "+appStatus);
        }
    }



    /* Group: Adding and removing node
     ***************************************************************************************
     */

    // see if this newly-received node is already added to our list
    // create new entry for the node in the map if not added yet
    static public boolean addNode (String node, BluetoothDevice device)
    {
        if (nodeDataMap.containsKey(node))
        {
            Log.d("WTF","This node is already discovered and added");
            return false;
        } else{
            nodeDataMap.put(node, new NodeData(node, device));
            // we need to maintain an ascending array of nodes besides the data map for drawing gridview
            allNodes = Arrays.copyOf(allNodes, allNodes.length+1);
            allNodes[allNodes.length-1] = Integer.parseInt(node);
            Arrays.sort(allNodes);    // sort all nodes in ascending order for listing of nodes in gridview
            Log.d("WTF","New node added, #"+node);
            return true;
        }

    }


    /* Group: Data manipulation
     ***************************************************************************************
     */
    // add data of one packet to the node's 2-d array
    // called after parsing the packet by BLE_scanner when RECORDING
    static public boolean addOnePacketData (String node, NodeMeasurements measurement)
    {
        if (nodeDataMap.containsKey(node)) // only log data for nodes discovered before recording started
        {
            nodeDataMap.get(node).addPacket(measurement);
            Log.d("WTF","Node " + node + " new data added!");
            return true;
        }else{
            Log.d("WTF","This node not added before recording, ignore data");
            return false;
        }
    }

    // return all data of a node
    // called by NodeDetail activity when tries to make the plot
    static public NodeData getNodeData (String node)
    {
        if (nodeDataMap.containsKey(node)) // only log data for nodes discovered before recording started
        {
            return nodeDataMap.get(node);
        }else{
            Log.d("WTF","Error! Trying to access data of a node not added, #"+node);
            return null;
        }
    }



    static public void nodeMeasurements2CSV(String node, NodeMeasurements measurements) throws IOException {
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < measurements.packetNum; i ++) {
            builder.append(measurements.timestamp[i]+",");
            builder.append(measurements.humiditySI7021[i]+",");
            builder.append(measurements.temperatureSI7021[i]+",");
            builder.append(measurements.humiditySHT85[i]+",");
            builder.append(measurements.temperatureSHT85[i]+"\n");
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH + "round1/"+ node + ".txt"));
        writer.write(builder.toString());
        writer.close();
        Log.d("WTF","Write csv successful!");
    }



    // delete the whole recording folder
    // don't have to implement this function now
    static public void cleanAllRecordingStorage(){

    }

    public static OptimSetting searchOptSetting() {
        OptimSetting optSet = new OptimSetting();
        int searchTempLower = tempLower;
        int searchTempUpper = 100;
        if (tempLower == -1 || tempLower > 100){
            searchTempLower = 70;
        }
        float searchTmhLower = deconTime;
        float searchTmhUpper = cycleTime;
        for (int testTemp = searchTempLower; testTemp <= searchTempUpper; testTemp = testTemp+1){
            for (float testTmh = searchTmhLower; testTmh <= searchTmhUpper; testTmh = testTmh + 60*1000){
                int testMPC = 0;
                String[] testFailNodes = {};
                for (Map.Entry<String, NodeData> node : nodeDataMap.entrySet())
                {
                    String nodeID = node.getKey();
                    NodeData nodeData = node.getValue();
                    NodeMeasurements nodeMes = nodeData.getNodeRecordingData();
                    NodeData thisSettingNodeReplicate = new NodeData(nodeID+"c", null);
                    thisSettingNodeReplicate.setNodeStatus(NodeData.NODE_STATUS_DECON_ESTABLISH);
                    // test all the packets by copying them to the new virtual node
                    for (int p = 0; p < nodeData.getPacketNumber() ; p++){
                        if (nodeMes.timestamp[p] - nodeMes.timestamp[0]> testTmh){
                            break;
                        } else{
                            NodeMeasurements pktMeasurement = new NodeMeasurements(nodeMes.timestamp[p],
                                    nodeMes.humiditySI7021[p],
                                    // stretch the temperature curve
                                    nodeMes.temperatureSI7021[0]+(nodeMes.temperatureSI7021[p]
                                            - nodeMes.temperatureSI7021[0])*((float)testTemp/heatingDeviceSetTemp),
                                    nodeMes.humiditySHT85[p], nodeMes.temperatureSHT85[p]);
                            thisSettingNodeReplicate.addPacket(pktMeasurement);
                            thisSettingNodeReplicate.checkDeconOpt();
                        }
                    }
                    if (thisSettingNodeReplicate.getNodeStatus() == NodeData.NODE_STATUS_DONE){
                        testMPC ++;
                    } else {
                        testFailNodes = Arrays.copyOf(testFailNodes, testFailNodes.length+1);
                        testFailNodes[testFailNodes.length-1] = nodeID;
                    }
                }
                int testNumCycs = (int) (NodeManager.totalWorkTime/testTmh);
                if (testNumCycs*testMPC > optSet.optNumCycs*optSet.optMPC){
                    optSet.update(testTemp, (int) (testTmh/(60*1000)), testNumCycs, testMPC, testFailNodes);
                }
            }
        }
        return optSet;
    }



}
