package example.ASPIRE.MyoHMI_Android;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;

/**
 * Created by naoki on 15/04/15.
 */

public class MyoGattCallback extends BluetoothGattCallback {
    public static double superTimeInitial;
    /**
     * Service ID
     */
    private static final String MYO_CONTROL_ID = "d5060001-a904-deb9-4748-2c7f4a124842";
    private static final String MYO_EMG_DATA_ID = "d5060005-a904-deb9-4748-2c7f4a124842";
    private static final String MYO_IMU_DATA_ID = "d5060002-a904-deb9-4748-2c7f4a124842";
    /**
     * Characteristics ID
     */
    private static final String MYO_INFO_ID = "d5060101-a904-deb9-4748-2c7f4a124842";
    private static final String FIRMWARE_ID = "d5060201-a904-deb9-4748-2c7f4a124842";
    private static final String COMMAND_ID = "d5060401-a904-deb9-4748-2c7f4a124842";

    private static final String EMG_0_ID = "d5060105-a904-deb9-4748-2c7f4a124842";
    private static final String EMG_1_ID = "d5060205-a904-deb9-4748-2c7f4a124842";
    private static final String EMG_2_ID = "d5060305-a904-deb9-4748-2c7f4a124842";
    private static final String EMG_3_ID = "d5060405-a904-deb9-4748-2c7f4a124842";
    private static final String IMU_0_ID = "d5060402-a904-deb9-4748-2c7f4a124842";
    /**
     * android Characteristic ID (from Android Samples/BluetoothLeGatt/SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
     */
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    private Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private Queue<BluetoothGattCharacteristic> readCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();

    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCharacteristic_command;
    private BluetoothGattCharacteristic mCharacteristic_emg0;
    private BluetoothGattCharacteristic mCharacteristic_emg1;
    private BluetoothGattCharacteristic mCharacteristic_emg2;
    private BluetoothGattCharacteristic mCharacteristic_emg3;
    private BluetoothGattCharacteristic mCharacteristic_imu0;

    private MyoCommandList commandList = new MyoCommandList();

    private String TAG = "MyoGatt";

    private TextView textView;
    private String callback_msg;
    private Handler mHandler;

    private Plotter plotter;
    private ProgressBar progress;
    private emgConsumer consumer = new emgConsumer();

    private FeatureCalculator fcalc;//maybe needs to be later in process

    private TcpClient mTcpClient = new TcpClient();

    public MyoGattCallback(Handler handler, TextView view, ProgressBar prog, Plotter plot, View v) {
        mHandler = handler;
        textView = view;
        plotter = plot;
        progress = prog;
        fcalc = new FeatureCalculator(plotter);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        Log.d(TAG, "onConnectionStateChange: " + status + " -> " + newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            // GATT Connected
            // Searching GATT Service
//            mHandler.post(new Runnable() {
//                @Override
//                public void run() {
//                    progress.setVisibility(View.VISIBLE);
//                    textView.setText("");
//                }
//            });

            gatt.discoverServices();

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // GATT Disconnected
            stopCallback();
            Log.d(TAG, "Bluetooth Disconnected");
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {

        super.onServicesDiscovered(gatt, status);
        Log.d(TAG, "onServicesDiscovered received: " + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Find GATT Service
            BluetoothGattService service_emg = gatt.getService(UUID.fromString(MYO_EMG_DATA_ID));
            BluetoothGattService service_imu = gatt.getService(UUID.fromString(MYO_IMU_DATA_ID));
            if (service_emg == null || service_imu == null) {//should probably break this into another separate checker for IMU service
                Log.d(TAG, "No Myo Service !!");
            } else {
                Log.d(TAG, "Find Myo Data Service !!");
                // Getting CommandCharacteristic
                mCharacteristic_emg0 = service_emg.getCharacteristic(UUID.fromString(EMG_0_ID));
                mCharacteristic_emg1 = service_emg.getCharacteristic(UUID.fromString(EMG_1_ID));
                mCharacteristic_emg2 = service_emg.getCharacteristic(UUID.fromString(EMG_2_ID));
                mCharacteristic_emg3 = service_emg.getCharacteristic(UUID.fromString(EMG_3_ID));
                mCharacteristic_imu0 = service_imu.getCharacteristic(UUID.fromString(IMU_0_ID));
                if (mCharacteristic_emg0 == null || mCharacteristic_imu0 == null) {
                    callback_msg = "Not Found Data Characteristics";
                } else {
                    // Setting the notification
                    boolean registered_0 = gatt.setCharacteristicNotification(mCharacteristic_emg0, true);
                    boolean registered_1 = gatt.setCharacteristicNotification(mCharacteristic_emg1, true);
                    boolean registered_2 = gatt.setCharacteristicNotification(mCharacteristic_emg2, true);
                    boolean registered_3 = gatt.setCharacteristicNotification(mCharacteristic_emg3, true);
                    boolean iregistered_0 = gatt.setCharacteristicNotification(mCharacteristic_imu0, true);
                    if (!registered_0 || !iregistered_0) {
                        Log.d(TAG, "EMG-Data Notification FALSE !!");
                    } else {
                        Log.d(TAG, "EMG-Data Notification TRUE !!");
                        // Turn ON the Characteristic Notification
                        BluetoothGattDescriptor descriptor_0 = mCharacteristic_emg0.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor descriptor_1 = mCharacteristic_emg1.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor descriptor_2 = mCharacteristic_emg2.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor descriptor_3 = mCharacteristic_emg3.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor idescriptor_0 = mCharacteristic_imu0.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        if (descriptor_0 != null || idescriptor_0 != null) {
                            idescriptor_0.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_0.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_1.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_2.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_3.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptorWriteQueue.add(idescriptor_0);
                            descriptorWriteQueue.add(descriptor_0);
                            descriptorWriteQueue.add(descriptor_1);
                            descriptorWriteQueue.add(descriptor_2);
                            descriptorWriteQueue.add(descriptor_3);
                            consumeAllGattDescriptors();
                            Log.d(TAG, "Set descriptor");
                        } else {
                            Log.d(TAG, "No descriptor");
                        }
                    }
                }
            }

            BluetoothGattService service = gatt.getService(UUID.fromString(MYO_CONTROL_ID));
            if (service == null) {
                Log.d(TAG, "No Myo Control Service !!");
            } else {
                Log.d(TAG, "Find Myo Control Service !!");
                // Get the MyoInfoCharacteristic
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(UUID.fromString(MYO_INFO_ID));
                if (characteristic == null) {
                } else {
                    Log.d(TAG, "Find read Characteristic !!");
                    //put the characteristic into the read queue
                    readCharacteristicQueue.add(characteristic);
                    //if there is only 1 item in the queue, then read it.  If more than 1, we handle asynchronously in the callback above
                    //GIVE PRECEDENCE to descriptor writes.  They must all finish first.
                    if ((readCharacteristicQueue.size() == 1) && (descriptorWriteQueue.size() == 0)) {
                        mBluetoothGatt.readCharacteristic(characteristic);
                    }
                }

                // Get CommandCharacteristic
                mCharacteristic_command = service.getCharacteristic(UUID.fromString(COMMAND_ID));
                if (mCharacteristic_command == null) {
                } else {
                    Log.d(TAG, "Find command Characteristic !!");
                }
            }
        }
    }

    public void writeGattDescriptor(BluetoothGattDescriptor d) {
        //put the descriptor into the write queue
        descriptorWriteQueue.add(d);
        //if there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
        if (descriptorWriteQueue.size() == 1) {
            mBluetoothGatt.writeDescriptor(d);
        }
    }

    public void consumeAllGattDescriptors() {
        mBluetoothGatt.writeDescriptor(descriptorWriteQueue.element());//the rest will happen in callback
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Callback: Wrote GATT Descriptor successfully.");
        } else {
            Log.d(TAG, "Callback: Error writing GATT Descriptor: " + status);
        }
        descriptorWriteQueue.remove();  //pop the item that we just finishing writing
        //if there is more to write, do it!
        if (descriptorWriteQueue.size() > 0)
            mBluetoothGatt.writeDescriptor(descriptorWriteQueue.element());
        else if (readCharacteristicQueue.size() > 0)
            mBluetoothGatt.readCharacteristic(readCharacteristicQueue.element());
    }

    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

//        readCharacteristicQueue.remove();
        if (status == BluetoothGatt.GATT_SUCCESS) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    textView.setText("Myo Connected");
                    progress.setVisibility(View.INVISIBLE);
                }
            });
//            EmgFragment emgFrag = new EmgFragment();
//            emgFrag.clickedemg(v);
        } else {
            Log.d(TAG, "onCharacteristicRead error: " + status);
        }

//        if(readCharacteristicQueue.size() > 0)
//            mBluetoothGatt.readCharacteristic(readCharacteristicQueue.element());
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onCharacteristicWrite success");
        } else {
            Log.d(TAG, "onCharacteristicWrite error: " + status);
        }
    }

    long last_send_never_sleep_time_ms = System.currentTimeMillis();
    final static long NEVER_SLEEP_SEND_TIME = 10000;  // Milli Second

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (EMG_0_ID.equals(characteristic.getUuid().toString()) || EMG_1_ID.equals(characteristic.getUuid().toString()) || EMG_2_ID.equals(characteristic.getUuid().toString()) || EMG_3_ID.equals(characteristic.getUuid().toString())) {

            long systemTime_ms = System.currentTimeMillis();
            superTimeInitial = systemTime_ms;
            byte[] emg_data = characteristic.getValue();

            Number[] emg_dataObj = ArrayUtils.toObject(emg_data);

            ArrayList<Number> emg_data_list1 = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(emg_dataObj, 0, 8)));
            ArrayList<Number> emg_data_list2 = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(emg_dataObj, 8, 16)));
            DataVector dvec1 = new DataVector(true, 1, 8, emg_data_list1, systemTime_ms);
            DataVector dvec2 = new DataVector(true, 2, 8, emg_data_list2, systemTime_ms);

//            dvec1.printDataVector("dvec");
//            dvec2.printDataVector("dvec");

            fcalc.pushFeatureBuffer(dvec1);
            fcalc.pushFeatureBuffer(dvec2);

//            new ConnectTask().execute(emg_data);

//            consumer.consume(emg_data);
            plotter.pushPlotter(emg_data);

            if (systemTime_ms > last_send_never_sleep_time_ms + NEVER_SLEEP_SEND_TIME) {
                // set Myo [Never Sleep Mode]
                Log.d("trying", "to unsleep");
                setMyoControlCommand(commandList.sendUnSleep());
                last_send_never_sleep_time_ms = systemTime_ms;
            }
        } else if (IMU_0_ID.equals(characteristic.getUuid().toString())) {
            long systemTime_ms = System.currentTimeMillis();
            byte[] imu_data = characteristic.getValue();
            plotter.pushPlotter(imu_data);
            Number[] emg_dataObj = ArrayUtils.toObject(imu_data);
//            ArrayList<Number> imu_data_list = new ArrayList<>(Arrays.asList(emg_dataObj));
//            DataVector dvec = new DataVector(false, 0, 20, imu_data_list, systemTime_ms);
            ArrayList<Number> imu_data_list1 = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(emg_dataObj, 0, 10)));
            ArrayList<Number> imu_data_list2 = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(emg_dataObj, 10, 20)));
            DataVector dvec1 = new DataVector(true, 1, 10, imu_data_list1, systemTime_ms);
            DataVector dvec2 = new DataVector(true, 2, 10, imu_data_list2, systemTime_ms);
//            dvec1.printDataVector("IMU1");
//            dvec2.printDataVector("IMU2");
            fcalc.pushIMUFeatureBuffer(dvec1);
            fcalc.pushIMUFeatureBuffer(dvec2);
        }
    }

    public void setBluetoothGatt(BluetoothGatt gatt) {
        mBluetoothGatt = gatt;
    }

    public boolean setMyoControlCommand(byte[] command) {
        if (mCharacteristic_command != null) {
            mCharacteristic_command.setValue(command);
            int i_prop = mCharacteristic_command.getProperties();
            if (i_prop == BluetoothGattCharacteristic.PROPERTY_WRITE) {
                if (mBluetoothGatt.writeCharacteristic(mCharacteristic_command)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void stopCallback() {
        // Before the closing GATT, set Myo [Normal Sleep Mode].
        setMyoControlCommand(commandList.sendNormalSleep());
        descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
        readCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();
        if (mCharacteristic_command != null) {
            mCharacteristic_command = null;
        }
        if (mCharacteristic_emg0 != null) {
            mCharacteristic_emg0 = null;
        }
        if (mBluetoothGatt != null) {
            mBluetoothGatt = null;
        }
    }

//    public class ConnectTask extends AsyncTask<byte[], String, TcpClient> {
//
//        @Override
//        protected TcpClient doInBackground(byte[]... message) {
//
//            mTcpClient.sendBytes(message[0]);
//
//            return null;
//        }
//        @Override
//        protected void onProgressUpdate(String... values) {
//            super.onProgressUpdate(values);
//            //response received from server
//            Log.d("test", "response " + values[0]);
//            //process server response here....
//        }
//    }

}