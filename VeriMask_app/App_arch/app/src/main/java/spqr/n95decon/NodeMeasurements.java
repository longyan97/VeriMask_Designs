package spqr.n95decon;


import java.util.Arrays;

public class NodeMeasurements {
    public int[] humiditySI7021 = {};
    public float[] temperatureSI7021 = {};
    public int[] humiditySHT85 = {};
    public float[] temperatureSHT85= {};
    public long[] timestamp = {};
    public int packetNum = 0;

    // constructor called by NodeData when adding a new node
    public NodeMeasurements()
    {

    }

    // constructor called by BLE_scanner when received a new packet
    public NodeMeasurements(long timestamp, int humiditySI7021, float temperatureSI7021, int humiditySHT85, float temperatureSHT85)
    {
        this.timestamp = new long[]{timestamp};
        this.humiditySI7021 = new int[]{humiditySI7021};
        this.temperatureSI7021 = new float[]{temperatureSI7021};
        this.humiditySHT85 = new int[]{humiditySHT85};
        this.temperatureSHT85 = new float[]{temperatureSHT85};

    }

    // add the new packet to the existing node's measurements
    public void addOnePacketMeasurement(NodeMeasurements onePacketMeasurement)
    {
        this.timestamp = Arrays.copyOf(this.timestamp, this.timestamp.length+1);
        this.timestamp[this.timestamp.length-1] = onePacketMeasurement.timestamp[0];

        this.humiditySI7021 = Arrays.copyOf(this.humiditySI7021, this.humiditySI7021.length+1);
        this.humiditySI7021[this.humiditySI7021.length-1] = onePacketMeasurement.humiditySI7021[0];

        this.temperatureSI7021 = Arrays.copyOf(this.temperatureSI7021, this.temperatureSI7021.length+1);
        this.temperatureSI7021[this.temperatureSI7021.length-1] = onePacketMeasurement.temperatureSI7021[0];

        this.humiditySHT85 = Arrays.copyOf(this.humiditySHT85, this.humiditySHT85.length+1);
        this.humiditySHT85[this.humiditySHT85.length-1] = onePacketMeasurement.humiditySHT85[0];

        this.temperatureSHT85 = Arrays.copyOf(this.temperatureSHT85, this.temperatureSHT85.length+1);
        this.temperatureSHT85[this.temperatureSHT85.length-1] = onePacketMeasurement.temperatureSHT85[0];
        packetNum ++;
    }

    // cut off all data after the specified timestamp. this is used or decon optimization
    public void cutOffData(long cutStamp){

    }

}
