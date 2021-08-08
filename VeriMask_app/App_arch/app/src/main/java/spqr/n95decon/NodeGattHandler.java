// We don't use this actually. This was for testing active communication.
// We found that actively connecting to a node will seriously delay the BLE receptions

package spqr.n95decon;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;

import java.util.List;
import java.util.UUID;

public class NodeGattHandler extends BluetoothGattCallback {

    public final static UUID SERVICE_UUID = UUID.fromString("00001523-1212-efde-1523-785feabcd123");
    private final static UUID CHARACTERISTIC_UUID = UUID.fromString("00001524-1212-efde-1523-785feabcd123");
    BluetoothDevice mDevice;
    private BluetoothGatt mGatt;
    public boolean mConnected = false;
    Context context;
    Button connectBtn;


    public NodeGattHandler(Context context, BluetoothDevice device) {
        this.context = context;
        mDevice = device;
        connectBtn = ((AppCompatActivity) context).findViewById(R.id.btn_connect);
    }


    public void connectDevice() {
        GattClientCallback mGattClientCallback = new GattClientCallback();
        mGatt = mDevice.connectGatt(context, false, mGattClientCallback);
    }

    // GATT device connection callback
    private class GattClientCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d("BLEConn","onConnectionStateChange newState: " + newState);

            if (status == BluetoothGatt.GATT_FAILURE) {
                Log.d("BLEConn","Connection Gatt failure status " + status);
                disconnectGattServer();
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                // handle anything not SUCCESS as failure
                Log.d("BLEConn","Connection not GATT sucess status " + status);
                disconnectGattServer();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLEConn","Connected to device " + gatt.getDevice().getAddress());
                mConnected = true;
                connectBtn.setText("Connected -> Disconnect");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLEConn","Disconnection terminated");
                disconnectGattServer();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d("BLEConn","onServicesDiscovered");
            super.onServicesDiscovered(gatt, status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLEConn","Device service discovery unsuccessful, status " + status);
                return;
            }

            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
            if (characteristic!= null)
                Log.d("BLEConn","Characteristic found!");
        }
    }

    public void disconnectGattServer() {
        Log.d("BLEConn","Closing Gatt connection");
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }
        mConnected = false;
        connectBtn.setText("Disconnected -> Connect");
    }

}
