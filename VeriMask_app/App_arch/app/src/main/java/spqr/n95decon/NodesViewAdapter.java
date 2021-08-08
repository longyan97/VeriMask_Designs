package spqr.n95decon;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

// This is for displaying all nodes in the main page
public class NodesViewAdapter extends BaseAdapter {

    private final Context context;

    public NodesViewAdapter (Context context){
        this.context = context;
    }


        @Override
    public int getCount() {
        return NodeManager.allNodes.length;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            final LayoutInflater layoutInflater = LayoutInflater.from(context);
            convertView = layoutInflater.inflate(R.layout.listing_onenode, null);
        }

        final TextView nodeIDTextView =  (TextView)convertView.findViewById(R.id.text_nodeID);
        final TextView nodeStatusTextView =  (TextView)convertView.findViewById(R.id.text_nodeStatusListing);


        nodeIDTextView.setText(""+NodeManager.allNodes[position]);

        String statusString = "";

        switch (NodeManager.getNodeData(""+NodeManager.allNodes[position]).getNodeStatus()) {
            case NodeData.NODE_STATUS_READY:
                statusString = "Ready";
                convertView.setBackgroundResource(R.drawable.circle_normal);
                break;
            case NodeData.NODE_STATUS_DECON_ESTABLISH:
            case NodeData.NODE_STATUS_DECON_INRANGE:
            case NodeData.NODE_STATUS_DECON_OUTRANGE:
                statusString = "Decon";
                convertView.setBackgroundResource(R.drawable.circle_normal);
                break;
            case NodeData.NODE_STATUS_ERROR_LOST:
            case NodeData.NODE_STATUS_ERROR_FAILURE:
                statusString = "ERROR";
                convertView.setBackgroundResource(R.drawable.circle_error);
                break;
            case NodeData.NODE_STATUS_DONE:
                statusString = "Done";
                convertView.setBackgroundResource(R.drawable.circle_done);
                break;
        }
        nodeStatusTextView.setText(statusString);


        return convertView;
    }
}
