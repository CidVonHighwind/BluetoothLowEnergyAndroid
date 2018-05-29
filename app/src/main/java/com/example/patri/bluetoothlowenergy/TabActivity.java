package com.example.patri.bluetoothlowenergy;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.Console;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TabActivity extends AppCompatActivity {

    private BluetoothLeScanner bleScanner;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler = new Handler();

    private BluetoothGatt mBluetoothGatt, mBluetoothGattFan;

    private boolean mScanning;

    private Context context;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 1000000;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1234;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    private TextView textViewTemperature, textViewHumidity;
    private SeekBar seekBar;

    private float tempValue, humValue;

    private Handler refresh;

    UUID weatherUUID = UUID.fromString("00000002-0000-0000-FDFD-FDFDFDFDFDFD");
    UUID tempUUID = UUID.fromString("00002A1C-0000-1000-8000-00805F9B34FB");
    UUID humUUID = UUID.fromString("00002A6F-0000-1000-8000-00805F9B34FB");
    UUID notificationUUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    UUID fanUUID = UUID.fromString("00000001-0000-0000-FDFD-FDFDFDFDFDFD");
    UUID intensityUUID = UUID.fromString("10000001-0000-0000-FDFD-FDFDFDFDFDFD");

    List<BluetoothGattCharacteristic> bufferList = new ArrayList<BluetoothGattCharacteristic>();

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            Log.d("onDestroy", "disconnect weather");
        }
        if (mBluetoothGattFan != null) {
            mBluetoothGattFan.disconnect();
            Log.d("onDestroy", "disconnect fan");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab);

        context = this;

        textViewTemperature = findViewById(R.id.tvTemperature);
        textViewHumidity = findViewById(R.id.tvHumidity);

        seekBar = findViewById(R.id.fanSeekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d("seekbar", "changed: " + progress);
                WriteFan((int) (progress / 100f * 65535));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d("seekbar", "started");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d("seekbar", "stopped");
            }
        });

        refresh = new Handler(Looper.getMainLooper());

        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e("tabactivity", "BLE is not supported on this device");
            finish();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // No explanation needed; request the permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);

            // MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        } else {
            SetUpBLE();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e("onRequestPermissionResult", "permission was granted");

                    // start scanning for devices
                    SetUpBLE();
                } else {
                    Log.e("onRequestPermissionResult", "permission was not granted...");
                }
                return;
            }
        }
    }

    private void SetUpBLE() {
        // get the bluetooth le scanner
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        bleScanner = mBluetoothAdapter.getBluetoothLeScanner();

        // start scanning for devices
        scanLeDevice(true);
    }

    private void UpdateUI() {
        textViewTemperature.setText(tempValue + "°C");
        textViewHumidity.setText(humValue + "°%");
    }

    private boolean lastValueRead;

    private BluetoothGattCallback mLeGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            Log.d("onCharacteristicChanged", characteristic.getUuid().toString());

            // gatt.readCharacteristic(characteristic);

            bufferList.add(characteristic);
            if (bufferList.size() == 1)
                gatt.readCharacteristic(bufferList.get(0));
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {

            Log.d("onCharacteristicRead", characteristic.toString());
            Log.d("onCharacteristicRead", characteristic.getUuid().toString());
            if (characteristic.getUuid().equals(tempUUID)) {
                int tempInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
                tempValue = tempInt / 100f;
                Log.d("onCharacteristicRead", "temp: " + tempInt);
            }

            if (characteristic.getUuid().equals(humUUID)) {
                int humInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
                humValue = humInt / 100f;
                Log.d("onCharacteristicRead", "hum: " + humInt);
            }

            // update the ui values
            refresh.post(new Runnable() {
                public void run() {
                    UpdateUI();
                }
            });

            if (bufferList.size() > 0) {
                bufferList.remove(0);
                if (bufferList.size() > 0)
                    gatt.readCharacteristic(bufferList.get(0));
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            Log.d("onConnectionStateChange", "status: " + status + " newState: " + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("onConnectionStateChange", "connected");

                mBluetoothGatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            Log.d("onServicesDiscovered", gatt + " status: " + status);


            BluetoothGattCharacteristic tempCharacteristic = gatt.getService(weatherUUID).getCharacteristic(tempUUID);

            BluetoothGattDescriptor descriptor = tempCharacteristic.getDescriptor(notificationUUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.setCharacteristicNotification(tempCharacteristic, true);
            gatt.writeDescriptor(descriptor);
        }

        BluetoothGattCharacteristic humCharacteristic;

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

            Log.d("onCharacteristicWrite", "write");

            if (humCharacteristic == null) {
                humCharacteristic = gatt.getService(weatherUUID).getCharacteristic(humUUID);

                BluetoothGattDescriptor descriptorTwo = humCharacteristic.getDescriptor(notificationUUID);
                descriptorTwo.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.setCharacteristicNotification(humCharacteristic, true);
                gatt.writeDescriptor(descriptorTwo);
            } else {
                refresh.post(new Runnable() {
                    public void run() {
                        Toast toast = Toast.makeText(context, "weather connected", Toast.LENGTH_SHORT);
                        toast.show();
                    }
                });
            }
        }
    };

    private BluetoothGattCallback mLeGattFanCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d("onCharacteristicChanged", characteristic.getUuid().toString());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {

            Log.d("onCharacteristicRead", characteristic.toString());
            Log.d("onCharacteristicRead", characteristic.getUuid().toString());

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            Log.d("onConnectionStateChange", "status: " + status + " newState: " + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("onConnectionStateChangeFan", "connected");

                mBluetoothGattFan.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            Log.d("onServicesDiscoveredFan", gatt + " status: " + status);

            BluetoothGattCharacteristic fanCharacteristic = gatt.getService(fanUUID).getCharacteristic(intensityUUID);
            gatt.setCharacteristicNotification(fanCharacteristic, true);

            refresh.post(new Runnable() {
                public void run() {
                    Toast toast = Toast.makeText(context, "fan connected", Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
        }
    };

    private void WriteFan(int strength) {
        if (mBluetoothGattFan == null)
            return;

        BluetoothGattCharacteristic fanCharacteristic = mBluetoothGattFan.getService(fanUUID).getCharacteristic(intensityUUID);
        fanCharacteristic.setValue(strength, BluetoothGattCharacteristic.FORMAT_UINT16, 0);

        mBluetoothGattFan.writeCharacteristic(fanCharacteristic);
    }

    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public synchronized void onScanResult(int callbackType, ScanResult result) {
            Log.d("scanresults", result.toString());

            if (mBluetoothGatt == null && result.getDevice().getName().equals("IPVSWeather")) {
                mBluetoothGatt = result.getDevice().connectGatt(context, false, mLeGattCallback);
                Log.d("scanresults", "connect to weather");
            } else if (mBluetoothGattFan == null && result.getDevice().getName().equals("IPVS-LIGHT")) {
                mBluetoothGattFan = result.getDevice().connectGatt(context, false, mLeGattFanCallback);
                Log.d("scanresults", "connect to light");
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            super.onScanResult(callbackType, result);

            //stop scanning
            if (mBluetoothGatt != null && mBluetoothGattFan != null)
                scanLeDevice(false);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results)
                Log.d("bachscanresults", result.toString());

            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("scanfailed", "failed: " + errorCode);

            super.onScanFailed(errorCode);
        }
    };

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d("scanLeDevice", "stopped scanning");
                    mScanning = false;
                    bleScanner.stopScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            Log.d("scanLeDevice", "start scanning");
            mScanning = true;

            // create filters for the scan
            ScanFilter scanFilterOne = new ScanFilter.Builder()
                    .setDeviceName("IPVSWeather").build();
            ScanFilter scanFilterTwo = new ScanFilter.Builder()
                    .setDeviceName("IPVS-LIGHT").build();
            // .setServiceUuid(ParcelUuid.fromString("00000002-0000-0000-FDFD-FDFDFDFDFDFD"))

            List<ScanFilter> scanFilterList = new ArrayList<ScanFilter>();
            scanFilterList.add(scanFilterTwo);
            scanFilterList.add(scanFilterOne);

            // create the settings for the scan
            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
            settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
            settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

            bleScanner.startScan(scanFilterList, settingsBuilder.build(), mLeScanCallback);
            //bleScanner.startScan(mLeScanCallback);
        } else {
            mScanning = false;
            bleScanner.stopScan(mLeScanCallback);
        }
    }
}
