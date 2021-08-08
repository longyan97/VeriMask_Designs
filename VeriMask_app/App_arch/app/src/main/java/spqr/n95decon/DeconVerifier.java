package spqr.n95decon;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.w3c.dom.Node;

import java.util.Objects;

public class DeconVerifier {
    private final Context context;
    private final SharedPreferences syncPreferences;

    public DeconVerifier(Context context) {
        this.context = context;
        this.syncPreferences = PreferenceManager.getDefaultSharedPreferences(this.context);
    }


    public void checkComm() {
        long timeNow = System.currentTimeMillis();
        for (String nodeID : NodeManager.nodeDataMap.keySet()) {

            if (NodeManager.getNodeData(nodeID).getNodeStatus() >=  NodeData.NODE_STATUS_DECON_ESTABLISH) {
                long timeLast = NodeManager.getNodeData(nodeID).getLastPacketTimeStamp();
                if (timeNow - timeLast > NodeManager.errorLostGracePeriod) {
                    NodeManager.getNodeData(nodeID).setNodeStatus(NodeData.NODE_STATUS_ERROR_LOST);
                    syncPreferences.edit().putString("Info", "S;" + nodeID + ";" + NodeData.NODE_STATUS_ERROR_LOST).apply();
                }
            }

        }
    }

    // real time check
    public void checkDecon(String nodeID) {
        NodeData nodeData = NodeManager.getNodeData(nodeID);
        long timeNow = System.currentTimeMillis();

        if (nodeData.getNodeStatus() == NodeData.NODE_STATUS_DECON_ESTABLISH){
            long timeFirst = nodeData.getFirstPacketTimeStamp();
            if (timeFirst != 0){ // have at least one packet

                if (lastPacketInDeconRange(nodeData, false)){
                    NodeManager.getNodeData(nodeID).setNodeStatus(NodeData.NODE_STATUS_DECON_INRANGE);
                    syncPreferences.edit().putString("Info", "S;" + nodeID + ";" + NodeData.NODE_STATUS_DECON_INRANGE).apply(); //deubug
                } else {
                    if (timeNow - timeFirst > 20 * 60 * 1000) { // 20min hard-coded
                        NodeManager.getNodeData(nodeID).setNodeStatus(NodeData.NODE_STATUS_ERROR_FAILURE);
                        syncPreferences.edit().putString("Info", "S;" + nodeID + ";" + NodeData.NODE_STATUS_ERROR_FAILURE).apply();
                    }
                }
            }
        }

        else if (nodeData.getNodeStatus() == NodeData.NODE_STATUS_DECON_INRANGE){
            if (lastPacketInDeconRange(nodeData, false)){
                float totalInRangeTime = nodeData.increaseInRangeTime();
                if (totalInRangeTime >= NodeManager.deconTime){
                    NodeManager.getNodeData(nodeID).setNodeStatus(NodeData.NODE_STATUS_DONE);
                    syncPreferences.edit().putString("Info", "S;" + nodeID + ";" + NodeData.NODE_STATUS_DONE).apply();
                }
            }
            else{   // falls out of range
                NodeManager.getNodeData(nodeID).setNodeStatus(NodeData.NODE_STATUS_DECON_OUTRANGE);
                syncPreferences.edit().putString("Info", "S;" + nodeID + ";" + NodeData.NODE_STATUS_DECON_OUTRANGE).apply(); //deubug
            }
        }

        else if ((nodeData.getNodeStatus() == NodeData.NODE_STATUS_DECON_OUTRANGE)){
            if (lastPacketInDeconRange(nodeData, false)) { // go back in range
                nodeData.clearOutOfRangeTime();
                NodeManager.getNodeData(nodeID).setNodeStatus(NodeData.NODE_STATUS_DECON_INRANGE);
                syncPreferences.edit().putString("Info", "S;" + nodeID + ";" + NodeData.NODE_STATUS_DECON_INRANGE).apply(); //deubug

            } else {
                float totalOutOfRangeTime = nodeData.increaseOutOfRangeTime();
                if (totalOutOfRangeTime >= NodeManager.errorFailureGracePeriod){
                    NodeManager.getNodeData(nodeID).setNodeStatus(NodeData.NODE_STATUS_ERROR_FAILURE);
                    syncPreferences.edit().putString("Info", "S;" + nodeID + ";" + NodeData.NODE_STATUS_ERROR_FAILURE).apply();
                }
            }
        }

    }


     public boolean lastPacketInDeconRange(NodeData nodeData, boolean tempOnly){
        long timeFirst = nodeData.getFirstPacketTimeStamp();
        if (timeFirst == 0)
            return false;
        else {
            if (tempOnly){
                return nodeData.getLastPacketTemp() >= NodeManager.tempLower && nodeData.getLastPacketTemp() <= NodeManager.tempUpper;
            } else {
                return nodeData.getLastPacketTemp() >= NodeManager.tempLower && nodeData.getLastPacketTemp() <= NodeManager.tempUpper &&
                        nodeData.getLastPacketRH() >= NodeManager.rhLower && nodeData.getLastPacketRH() <= NodeManager.rhUpper;
            }
        }
    }

}
