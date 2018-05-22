package com.example.patri.bluetoothlowenergy;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
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

public class TabActivity extends AppCompatActivity {

    private BluetoothLeScanner bleScanner;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler = new Handler();

    private boolean mScanning;

    private Context context;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1234;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    private TextView textViewTemperature, textViewHumidity;
    private SeekBar seekBar;

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
                    Log.e("onRequestPermissionResult","permission was granted");

                    // start scanning for devices
                    SetUpBLE();
                } else {
                    Log.e("onRequestPermissionResult","permission was not granted...");
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

    private BluetoothGattCallback mLeGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic){

            Log.d("onCharacteristicChanged", gatt.toString());
            Log.d("onCharacteristicChanged", characteristic.toString());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status){

            Log.d("onCharacteristicRead", characteristic.toString());
        }
    };

    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d("scanresults", result.toString());

            result.getDevice().connectGatt(context, true, mLeGattCallback);

            super.onScanResult(callbackType, result);

            // stop scanning
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
            ScanFilter scanFilter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString("0000feb8-0000-1000-8000-00805f9b34fb")).build();

            List<ScanFilter> scanFilterList = new ArrayList<ScanFilter>();
            scanFilterList.add(scanFilter);

            // create the settings for the scan
            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
            settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
            settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

            bleScanner.startScan(scanFilterList, settingsBuilder.build(), mLeScanCallback);
        } else {
            mScanning = false;
            bleScanner.stopScan(mLeScanCallback);
        }
    }
}
