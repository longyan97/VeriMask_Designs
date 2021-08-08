// This class is redundant just for convenience. Should merge it with DeconVerifier

package spqr.n95decon;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class DeconVerifierOpt {


    public DeconVerifierOpt() {

    }

    // optimization check
    public void checkDecon(NodeData nodeData) {

        if (nodeData.getNodeStatus() == NodeData.NODE_STATUS_DECON_ESTABLISH){
            long timeFirst = nodeData.getFirstPacketTimeStamp();
            long timeNow = nodeData.getLastPacketTimeStamp();
            if (timeFirst != 0){ // have at least one packet
                if (lastPacketInDeconRange(nodeData, true)){
                    nodeData.setNodeStatus(NodeData.NODE_STATUS_DECON_INRANGE);
                } else {
                    if (timeNow - timeFirst > 20 * 60 * 1000) { // 20min hard-coded
                        nodeData.setNodeStatus(NodeData.NODE_STATUS_ERROR_FAILURE);
                    }
                }
            }
        }

        else if (nodeData.getNodeStatus() == NodeData.NODE_STATUS_DECON_INRANGE){
            if (lastPacketInDeconRange(nodeData, true)){
                float totalInRangeTime = nodeData.increaseInRangeTime();
                if (totalInRangeTime >= NodeManager.deconTime){
                    nodeData.setNodeStatus(NodeData.NODE_STATUS_DONE);
                }
            }
            else{   // falls out of range
                nodeData.setNodeStatus(NodeData.NODE_STATUS_DECON_OUTRANGE);
            }
        }

        else if ((nodeData.getNodeStatus() == NodeData.NODE_STATUS_DECON_OUTRANGE)){
            if (lastPacketInDeconRange(nodeData, true)) { // go back in range
                nodeData.clearOutOfRangeTime();
                nodeData.setNodeStatus(NodeData.NODE_STATUS_DECON_INRANGE);
            } else {
                float totalOutOfRangeTime = nodeData.increaseOutOfRangeTime();
                if (totalOutOfRangeTime >= NodeManager.errorFailureGracePeriod){
                    nodeData.setNodeStatus(NodeData.NODE_STATUS_ERROR_FAILURE);
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
