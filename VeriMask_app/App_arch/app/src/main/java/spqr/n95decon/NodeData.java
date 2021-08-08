package spqr.n95decon;

import android.bluetooth.BluetoothDevice;

// data and decon information for a single node
public class NodeData {

    static public final int NODE_STATUS_READY = 0;
    static public final int NODE_STATUS_ERROR_LOST = 1;
    static public final int NODE_STATUS_ERROR_FAILURE = 2;
    static public final int NODE_STATUS_DONE = 3;
    static public final int NODE_STATUS_DECON_ESTABLISH = 4;
    static public final int NODE_STATUS_DECON_INRANGE = 5;
    static public final int NODE_STATUS_DECON_OUTRANGE = 6;
    static public final String[] statusNames = {"Ready","E_Lost", "E_Fail","Done","D_EST","D_IN","D_OUT"};

    private String nodeID;
    private int nodeStatus;
    private NodeMeasurements myNodeMeasurements;
    private BluetoothDevice mDevice;
    private DeconVerifierOpt mVerifier;
    private float inRangeTime;
    private float outOfRangeTime;

    public NodeData(String id, BluetoothDevice device)
    {
        nodeStatus = NODE_STATUS_READY;
        nodeID = id;
        myNodeMeasurements = new NodeMeasurements();
        mDevice = device;
        inRangeTime = 0;
        outOfRangeTime = 0;
        this.mVerifier = new DeconVerifierOpt();
    }


    /* Group: Node data manipulation
     ***************************************************************************************
     */

    public void addPacket(NodeMeasurements onePacketMeasurement)
    {
        myNodeMeasurements.addOnePacketMeasurement(onePacketMeasurement);
    }

    public NodeMeasurements getNodeRecordingData() {
        return myNodeMeasurements;
    }

    public long getLastPacketTimeStamp() {
        if (myNodeMeasurements.timestamp.length > 0)
            return myNodeMeasurements.timestamp[myNodeMeasurements.timestamp.length-1];
        else
            return 0;
    }

    public long getSecondLastPacketTimeStamp() {
        if (myNodeMeasurements.timestamp.length > 1)
            return myNodeMeasurements.timestamp[myNodeMeasurements.timestamp.length-2];
        else
            return 0;
    }

    public long getFirstPacketTimeStamp() {
        if (myNodeMeasurements.timestamp.length > 0)
            return myNodeMeasurements.timestamp[0];
        else
            return 0;
    }


    public float getLastPacketTemp() {
        if (myNodeMeasurements.timestamp.length > 0)
            return myNodeMeasurements.temperatureSI7021[myNodeMeasurements.timestamp.length-1];
        else
            return 0;
    }

    public float getLastPacketRH() {
        if (myNodeMeasurements.timestamp.length > 0)
            return myNodeMeasurements.humiditySI7021[myNodeMeasurements.timestamp.length-1];
        else
            return 0;
    }

    public float getInRangeTime(){
        return inRangeTime;
    }

    public float increaseInRangeTime(){

        inRangeTime += getLastPacketTimeStamp()-getSecondLastPacketTimeStamp();
        return inRangeTime;
    }

    public float increaseOutOfRangeTime(){

        outOfRangeTime += getLastPacketTimeStamp()-getSecondLastPacketTimeStamp();
        return outOfRangeTime;
    }

    public void clearOutOfRangeTime(){
        outOfRangeTime = 0;
    }


    public int getPacketNumber(){
        return myNodeMeasurements.packetNum;
    }



    /* Group: Node information
     ***************************************************************************************
     */
    public String getNodeID() {
        return nodeID;
    }

    public BluetoothDevice getBleDevice() {return mDevice; }

    public int getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(int status){
        nodeStatus = status;
    }


    public void checkDeconOpt() {
        mVerifier.checkDecon(this);
    }
}
