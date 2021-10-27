package com.example.cat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import bolts.Continuation;
import bolts.Task;

import com.example.cat.FileManager;

import com.mbientlab.metawear.DeviceInformation;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.GyroBmi160;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity implements ServiceConnection {
    private FileManager mFileManager = new FileManager();
    public char p_num = '1';
    public int GivenNUmber = 1;
    private String getHz_l_a = "";
    private BtleService.LocalBinder serviceBinder;


    private static final String[] deviceUUIDs = {"CA:A4:EA:4B:BD:88", "F8:78:EF:EC:B5:CC"};//"FD:0F:59:E2:F4:C5" "D4:25:5C:D6:2E:F5"

    //Data Catch Map for accel & gyro scope
    private Map<String, Accelerometer> accelerometerSensors = new HashMap<>();
    private Map<String, GyroBmi160> gyroSensors = new HashMap<>();

    private Map<String, TextView> sensorOutputs = new HashMap<>();
    private Map<String, TextView> gyrosensorOutputs = new HashMap<>();
    private Route streamRoute;

    private static String[] ACCESS_PERMISSION = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private static String[] STORAGE_PERMISSION = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    private static String[] BLUETOOTH_PERMISSION = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_PRIVILEGED
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(MainActivity.this, ACCESS_PERMISSION, 1);
        ActivityCompat.requestPermissions(MainActivity.this, STORAGE_PERMISSION, 1);
        ActivityCompat.requestPermissions(MainActivity.this, BLUETOOTH_PERMISSION, 1);

        mFileManager.createLogFile(p_num , "init");
        mFileManager.createMoFile(p_num, String.valueOf(GivenNUmber));

        // Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

        // 1st MMC setting
        String device_0 = deviceUUIDs[0];
        Switch metawearSwitch_0 = (Switch) findViewById(R.id.switchMetawear_0);
//        TextView metaweartext_0 = (TextView) findViewById(R.id.switchMetawear_0);
//        metaweartext_0.setText("1");
        TextView sensorOutput_0 = (TextView) findViewById(R.id.txtSensorOutput_0);
        TextView gyrosensorOutput_0 =(TextView) findViewById(R.id.txtGyroSensorOutput_0);

        sensorOutputs.put(device_0, sensorOutput_0);
        gyrosensorOutputs.put(device_0, gyrosensorOutput_0);
//        connectToMetawear(device_0);

        metawearSwitch_0.setOnCheckedChangeListener((compoundButton, enable) -> {
            if (enable) {
                connectToMetawear(device_0);
            } else {
                stopAccelerometer(device_0);
                stopGyro(device_0);
            }
        });
        metawearSwitch_0.setText("Device " + device_0);

        sensorOutput_0.setText("- -");
        gyrosensorOutput_0.setText("- -");

        // 2nd MMC setting
        String device_1 = deviceUUIDs[1];
        Switch metawearSwitch_1 = (Switch) findViewById(R.id.switchMetawear_1);
//        TextView metaweartext_1 = (TextView) findViewById(R.id.switchMetawear_1);
//        metaweartext_1.setText("2");
        TextView sensorOutput_1 = (TextView) findViewById(R.id.txtSensorOutput_1);
        TextView gyrosensorOutput_1 =(TextView) findViewById(R.id.txtGyroSensorOutput_1);

        sensorOutputs.put(device_1, sensorOutput_1);
        gyrosensorOutputs.put(device_1, gyrosensorOutput_1);
//        connectToMetawear(device_1);

        metawearSwitch_1.setOnCheckedChangeListener((compoundButton, enable) -> {
            if (enable) {
                connectToMetawear(device_1);
            } else {
                stopAccelerometer(device_1);
                stopGyro(device_1);
            }
        });
        metawearSwitch_1.setText("Device " + device_1);

        sensorOutput_1.setText("- -");
        gyrosensorOutput_1.setText("- -");

    }

    private void connectToMetawear(String deviceUUID) {
        BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothDevice btDevice = btManager.getAdapter().getRemoteDevice(deviceUUID);
        MetaWearBoard mwBoard = serviceBinder.getMetaWearBoard(btDevice);
        mFileManager.saveLogData("connectToMetawear", "try connect metawear" + deviceUUIDs[0] + ":" + deviceUUIDs[1]);

        mwBoard.connectAsync()
                .continueWithTask(task -> {
                    if (task.isCancelled()) {
                        return task;
                    }
                    return task.isFaulted() ? reconnect(mwBoard) : Task.forResult(null);
                })
                .continueWith(task -> {
                    if (!task.isCancelled()) {
                        startAccelerometer(mwBoard);
                        startGyro(mwBoard);
                        }
                    return null;
                });

        mwBoard.onUnexpectedDisconnect(new MetaWearBoard.UnexpectedDisconnectHandler() {
        @Override
        public void disconnected(int status) {
            mFileManager.saveLogData("MetaWear", "disconnected");
        }
    });
    }

    public static Task<Void> reconnect(final MetaWearBoard board) {
        return board.connectAsync()
                .continueWithTask(task -> {
                    if (task.isFaulted()) {
                        return reconnect(board);
                    } else if (task.isCancelled()) {
                        return task;
                    }
                    return Task.forResult(null);
                });
    }

    /**
     * Acc Sensor
     * @param mwBoard
     */
    private void startAccelerometer(MetaWearBoard mwBoard){
        Accelerometer accelerometer = accelerometerSensors.get(mwBoard.getMacAddress());
        if (accelerometer == null) {
            accelerometer = mwBoard.getModule(Accelerometer.class);
            accelerometerSensors.put(mwBoard.getMacAddress(), accelerometer);
        }

        getHz_l_a = String.valueOf(accelerometer.getOdr());
        TextView sensorOutput = sensorOutputs.get(mwBoard.getMacAddress());

        accelerometer.acceleration().addRouteAsync(source -> source.stream((data, env) -> {
        final Acceleration value = data.value(Acceleration.class);
        runOnUiThread(() -> sensorOutput.setText(getHz_l_a + "HZ : " + value.x() + ", " + value.y() + ", " + value.z()));
        if (mwBoard.getMacAddress() == deviceUUIDs[0]) {
            mFileManager.saveData("0", value.x(), value.y(), value.z(), "accel");
        } else if (mwBoard.getMacAddress() == deviceUUIDs[1]) {
            mFileManager.saveData("1", value.x(), value.y(), value.z(), "accel");
        }
        })).continueWith(task -> {
            streamRoute = task.getResult();
            accelerometerSensors.get(mwBoard.getMacAddress()).acceleration().start();
            accelerometerSensors.get(mwBoard.getMacAddress()).start();
            return null;
        });
    }

    protected void stopAccelerometer(String deviceUUID) {
        Accelerometer accelerometer = accelerometerSensors.get(deviceUUID);
        accelerometer.stop();
        accelerometer.acceleration().stop();
        if (streamRoute != null){
            streamRoute.remove();
        }
    }

    /**
     * Gyro Sensor
     * @param mwBoard
     */
    private void startGyro(MetaWearBoard mwBoard){
        GyroBmi160 gyroBmi160 = gyroSensors.get(mwBoard.getMacAddress());
        if (gyroBmi160 == null) {

            gyroBmi160 = mwBoard.getModule(GyroBmi160.class);
            gyroSensors.put(mwBoard.getMacAddress(), gyroBmi160);
        }

        TextView gyrosensorOutput = gyrosensorOutputs.get(mwBoard.getMacAddress());

        gyroBmi160.angularVelocity().addRouteAsync(source -> source.stream((data, env) -> {
            final AngularVelocity value = data.value(AngularVelocity.class);
            runOnUiThread(() -> gyrosensorOutput.setText(value.x() + ", " + value.y() + ", " + value.z()));
            mFileManager.saveData(mwBoard.getMacAddress(), value.x(), value.y(), value.z(), "gyro");
        })).continueWith(task -> {
            streamRoute = task.getResult();
            gyroSensors.get(mwBoard.getMacAddress()).angularVelocity().start();
            gyroSensors.get(mwBoard.getMacAddress()).start();
            return null;
        });
    }

    protected void stopGyro(String deviceUUID) {
        GyroBmi160 gyroBmi160 =gyroSensors.get(deviceUUID);
        gyroBmi160.stop();
        gyroBmi160.angularVelocity().stop();
        if (streamRoute != null){
            streamRoute.remove();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);

        //Stop Handler
        try {
            mFileManager.saveLogData("ondestroy", "end");
            stopAccelerometer(deviceUUIDs[0]);
            stopAccelerometer(deviceUUIDs[1]);
            stopGyro(deviceUUIDs[0]);
            stopGyro(deviceUUIDs[1]);
        }catch (Exception e){

        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i("Main","meta service connect");
        // Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    /***************************  Example API code below  *****************************/
//    //Finding Your Device
//    private final String MW_MAC_ADDRESS= "EC:2C:09:81:22:AC";
//    private MetaWearBoard board;
//
//    public void retrieveBoard() {
//        final BluetoothManager btManager=
//                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
//        final BluetoothDevice remoteDevice=
//                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);
//
//        // Create a MetaWear board object for the Bluetooth Device
//        board= serviceBinder.getMetaWearBoard(remoteDevice);
//        //Model
//        Log.i("MainActivity", "board model = " + board.getModel());
//
//        //Board Information
//        board.readDeviceInformationAsync()
//                .continueWith(new Continuation<DeviceInformation, Void>() {
//                    @Override
//                    public Void then(Task<DeviceInformation> task) throws Exception {
//                        Log.i("MainActivity", "Device Information: " + task.getResult());
//                        return null;
//                    }
//                });
//
//        //BLE Connection
//        board.connectAsync().continueWith(new Continuation<Void, Void>() {
//            @Override
//            public Void then(Task<Void> task) throws Exception {
//                if (task.isFaulted()) {
//                    Log.i("MainActivity", "Failed to connect");
//                } else {
//                    Log.i("MainActivity", "Connected");
//                }
//                return null;
//            }
//        });
//        board.disconnectAsync().continueWith(new Continuation<Void, Void>() {
//            @Override
//            public Void then(Task<Void> task) throws Exception {
//                Log.i("MainActivity", "Disconnected");
//                return null;
//            }
//        });
//
//        //Unexpected Disconnects
//        board.onUnexpectedDisconnect(new MetaWearBoard.UnexpectedDisconnectHandler() {
//            @Override
//            public void disconnected(int status) {
//                Log.i("MainActivity", "Unexpectedly lost connection: " + status);
//            }
//        });
//
//    }
}
