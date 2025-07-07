package com.example.wifibluetoothhapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Set;

public class BluetoothActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private TextView tvDeviceName;
    private Button btnToggleBluetooth, btnScanBluetooth, btnBack;
    private ListView listPairedDevices, listDiscoveredDevices;
    private ArrayAdapter<String> pairedAdapter, discoveredAdapter;
    private ArrayList<String> pairedList = new ArrayList<>();
    private ArrayList<String> discoveredList = new ArrayList<>();
    private final int PERMISSIONS_REQUEST_CODE = 101;
    private Handler autoBluetoothScanHandler = new Handler();
    private Runnable autoBluetoothScanRunnable;


    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && checkBluetoothConnectPermission()) {
                    String deviceInfo = device.getName() + " - " + device.getAddress();
                    if (!discoveredList.contains(deviceInfo)) {
                        discoveredList.add(deviceInfo);
                        discoveredAdapter.notifyDataSetChanged();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(BluetoothActivity.this, "Búsqueda finalizada", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        tvDeviceName = findViewById(R.id.tv_device_name);
        btnToggleBluetooth = findViewById(R.id.btn_toggle_bluetooth);
        btnScanBluetooth = findViewById(R.id.btn_scan_bluetooth);
        btnBack = findViewById(R.id.btn_back_bluetooth);
        listPairedDevices = findViewById(R.id.list_bluetooth_devices);
        listDiscoveredDevices = findViewById(R.id.list_discovered_devices);

        pairedAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, pairedList) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);

                String item = getItem(position);
                String lastDevice = getSharedPreferences("BT_PREFS", MODE_PRIVATE).getString("last_device", "");

                if (item != null && item.contains(lastDevice)) {
                    textView.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
                    textView.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    textView.setTextColor(getResources().getColor(android.R.color.white));
                    textView.setTypeface(null, android.graphics.Typeface.NORMAL);
                }

                return view;
            }
        };
        discoveredAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, discoveredList);
        listPairedDevices.setAdapter(pairedAdapter);
        listPairedDevices.setOnItemClickListener((parent, view, position, id) -> {
            String selected = pairedList.get(position).replace(" (Guardado)", "").trim();
            SharedPreferences.Editor editor = getSharedPreferences("BT_PREFS", MODE_PRIVATE).edit();
            editor.putString("last_device", selected);
            editor.apply();

            Toast.makeText(this, "Seleccionado: " + selected, Toast.LENGTH_SHORT).show();

        });

        listDiscoveredDevices.setAdapter(discoveredAdapter);

        if (bluetoothAdapter != null) {
            String deviceName = bluetoothAdapter.getName();
            tvDeviceName.setText("Nombre del dispositivo: \"" + deviceName + "\"");
            tvDeviceName.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
        } else {
            tvDeviceName.setText("Bluetooth no disponible");
        }

        btnToggleBluetooth.setOnClickListener(v -> {
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!checkBluetoothConnectPermission()) {
                requestPermissions();
                return;
            }

            if (bluetoothAdapter.isEnabled()) {
                Intent panelIntent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(panelIntent);
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        });


        btnScanBluetooth.setOnClickListener(v -> {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Toast.makeText(this, "Bluetooth desactivado. Actívalo para buscar dispositivos.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (checkPermissions()) {
                discoveredList.clear();
                discoveredAdapter.notifyDataSetChanged();
                bluetoothAdapter.startDiscovery();
                Toast.makeText(this, "Buscando dispositivos Bluetooth...", Toast.LENGTH_SHORT).show();
            } else {
                requestPermissions();
            }
        });

        btnBack.setOnClickListener(v -> finish());

        // Registrar receiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetoothReceiver, filter);
        autoBluetoothScanRunnable = new Runnable() {
            @Override
            public void run() {
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && checkPermissions()) {
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                    bluetoothAdapter.startDiscovery();
                }
                autoBluetoothScanHandler.postDelayed(this, 20000);
            }
        };

        autoBluetoothScanHandler.postDelayed(autoBluetoothScanRunnable, 5000);

        showPairedDevices();
    }

    @SuppressLint("MissingPermission")
    private void showPairedDevices() {
        if (!checkBluetoothConnectPermission()) {
            Toast.makeText(this, "Permiso BLUETOOTH_CONNECT requerido", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        pairedList.clear();

        SharedPreferences prefs = getSharedPreferences("BT_PREFS", MODE_PRIVATE);
        String lastConnected = prefs.getString("last_device", null);

        ArrayList<String> reorderedList = new ArrayList<>();
        if (pairedDevices != null && pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String info = device.getName() + " - " + device.getAddress();
                if (info.equals(lastConnected)) {
                    reorderedList.add(0, info + " (Guardado)");
                } else {
                    reorderedList.add(info + " (Guardado)");
                }
            }
            pairedList.addAll(reorderedList);
            pairedAdapter.notifyDataSetChanged();
        }

    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private boolean checkBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSIONS_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
        autoBluetoothScanHandler.removeCallbacks(autoBluetoothScanRunnable);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                Toast.makeText(this, "Permisos otorgados", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permisos necesarios no concedidos", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
