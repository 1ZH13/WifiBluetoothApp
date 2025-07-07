package com.example.wifibluetoothhapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.*;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.os.Handler;



import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;

import java.util.ArrayList;
import java.util.List;

public class WifiActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    private ListView listView;
    private Button btnToggleWifi, btnScanWifi, btnBack;
    private ArrayAdapter<String> adapter;
    private List<ScanResult> scanResults = new ArrayList<>();
    private final int PERMISSIONS_REQUEST_CODE = 100;
    private String connectedSsid = null;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private List<ScanResult> displayScanResults = new ArrayList<>();
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "WifiPasswords";



    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                scanSuccess();
            } else {
                scanFailure();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        listView = findViewById(R.id.list_wifi_networks);
        btnToggleWifi = findViewById(R.id.btn_toggle_wifi);
        btnScanWifi = findViewById(R.id.btn_scan_wifi);
        btnBack = findViewById(R.id.btn_back_wifi);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);

        btnToggleWifi.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                startActivity(panelIntent);
            } else {
                boolean enabled = wifiManager.isWifiEnabled();
                wifiManager.setWifiEnabled(!enabled);
                Toast.makeText(this, enabled ? "Desactivando WiFi..." : "Activando WiFi...", Toast.LENGTH_SHORT).show();
                if (enabled) {
                    adapter.clear();
                    displayScanResults.clear();
                    adapter.notifyDataSetChanged();
                }
            }
        });

        btnScanWifi.setOnClickListener(v -> {
            if (!wifiManager.isWifiEnabled()) {
                Toast.makeText(this, "WiFi está desactivado. Actívalo para buscar redes.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (checkPermissions()) {
                btnScanWifi.setEnabled(false);
                btnScanWifi.setAlpha(0.5f);
                scanWifiNetworks();
                new android.os.Handler().postDelayed(() -> {
                    btnScanWifi.setEnabled(true);
                    btnScanWifi.setAlpha(1.0f);
                }, 5000);
            } else {
                requestPermissions();
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ScanResult selectedNetwork = displayScanResults.get(position);
            showConnectDialog(selectedNetwork);
        });

        btnBack.setOnClickListener(v -> finish());

        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    private boolean checkPermissions() {
        boolean locationPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return locationPermission;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                PERMISSIONS_REQUEST_CODE);
    }

    private void scanWifiNetworks() {
        boolean success = wifiManager.startScan();
        if (!success) {
            Toast.makeText(this, "Error al iniciar escaneo WiFi", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void scanSuccess() {
        if (!checkPermissions()) {
            Toast.makeText(this, "Permisos insuficientes para escanear WiFi", Toast.LENGTH_SHORT).show();
            return;
        }

        scanResults = wifiManager.getScanResults();
        List<String> displayList = new ArrayList<>();
        displayScanResults.clear();

        for (ScanResult result : scanResults) {
            if (result.SSID.equals(connectedSsid)) {
                displayList.add(result.SSID + " - Conectado ✅");
                displayScanResults.add(result);
            }
        }

        for (ScanResult result : scanResults) {
            if (!result.SSID.equals(connectedSsid)) {
                displayList.add(result.SSID + " - " + result.level + " dBm");
                displayScanResults.add(result);
            }
        }

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = (TextView) view.findViewById(android.R.id.text1);

                if (displayList.get(position).contains("Conectado")) {
                    text.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    text.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    text.setTextColor(getResources().getColor(android.R.color.white));
                    text.setTypeface(null, android.graphics.Typeface.NORMAL);
                }

                return view;
            }
        };

        listView.setAdapter(adapter);
        Toast.makeText(this, "Redes WiFi encontradas: " + scanResults.size(), Toast.LENGTH_SHORT).show();
    }



    private void scanFailure() {
        Toast.makeText(this, "Fallo al escanear redes WiFi", Toast.LENGTH_SHORT).show();
    }

    private void showConnectDialog(ScanResult result) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Conectar a: " + result.SSID);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Contraseña");

        String savedPassword = sharedPreferences.getString(result.SSID, null);
        if (savedPassword != null) {
            input.setText(savedPassword);
        }

        builder.setView(input);

        builder.setPositiveButton("Conectar", (dialog, which) -> {
            String password = input.getText().toString().trim();
            if (password.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa una contraseña", Toast.LENGTH_SHORT).show();
                return;
            }
            sharedPreferences.edit().putString(result.SSID, password).apply();
            connectToWifi(result, password);
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.setNeutralButton("Opciones", (dialog, which) -> showOptionsDialog(result));

        builder.show();
    }

    private void showOptionsDialog(ScanResult result) {
        AlertDialog.Builder optionsBuilder = new AlertDialog.Builder(this);
        optionsBuilder.setTitle("Opciones para: " + result.SSID);

        String[] options = {"Desconectar", "Olvidar red"};
        optionsBuilder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    disconnectFromNetwork(result.SSID);
                    break;
                case 1:
                    sharedPreferences.edit().remove(result.SSID).apply();
                    forgetWifiNetwork(result.SSID);
                    Toast.makeText(this, "Red olvidada: " + result.SSID, Toast.LENGTH_SHORT).show();
                    disconnectFromNetwork(result.SSID);
                    break;
            }
        });

        optionsBuilder.setNegativeButton("Cerrar", null);
        optionsBuilder.show();
    }




    @SuppressLint("MissingPermission")
    private boolean isConnecting = false;
    private Handler handler = new Handler();
    private Runnable resetConnectionStateRunnable;

    private void connectToWifi(ScanResult result, String password) {
        if (isConnecting) {
            Toast.makeText(this, "Ya hay una conexión en curso, espera unos segundos...", Toast.LENGTH_SHORT).show();
            return;
        }

        isConnecting = true;
        btnScanWifi.setEnabled(false);
        btnScanWifi.setAlpha(0.5f);

        resetConnectionStateRunnable = () -> {
            isConnecting = false;
            btnScanWifi.setEnabled(true);
            btnScanWifi.setAlpha(1f);
            Toast.makeText(WifiActivity.this, "Conexión expirada, intenta de nuevo.", Toast.LENGTH_SHORT).show();
        };
        handler.postDelayed(resetConnectionStateRunnable, 20000);

        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
            networkCallback = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder().setSsid(result.SSID);

            if (result.capabilities.toUpperCase().contains("WPA")) {
                builder.setWpa2Passphrase(password);
            }

            WifiNetworkSpecifier specifier = builder.build();

            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    runOnUiThread(() -> {
                        handler.removeCallbacks(resetConnectionStateRunnable);
                        isConnecting = false;
                        btnScanWifi.setEnabled(true);
                        btnScanWifi.setAlpha(1f);

                        connectedSsid = result.SSID;
                        sharedPreferences.edit().putString(result.SSID, password).apply();
                        scanWifiNetworks();

                        new AlertDialog.Builder(WifiActivity.this)
                                .setTitle("Conexión temporal establecida")
                                .setMessage("Conectado a \"" + result.SSID + "\". Esta conexión se perderá si cierras la app.")
                                .setPositiveButton("Abrir ajustes Wi-Fi", (dialog, which) -> {
                                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                                })
                                .setNegativeButton("Entendido", null)
                                .show();
                    });
                }

                @Override
                public void onUnavailable() {
                    runOnUiThread(() -> {
                        handler.removeCallbacks(resetConnectionStateRunnable);
                        isConnecting = false;
                        btnScanWifi.setEnabled(true);
                        btnScanWifi.setAlpha(1f);
                        Toast.makeText(WifiActivity.this, "No se pudo conectar a " + result.SSID, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onLost(Network network) {
                    runOnUiThread(() ->
                            Toast.makeText(WifiActivity.this, "Conexión perdida a " + result.SSID, Toast.LENGTH_SHORT).show());
                }
            };

            try {
                connectivityManager.requestNetwork(request, networkCallback);
                Toast.makeText(this, "Solicitando conexión a " + result.SSID, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                handler.removeCallbacks(resetConnectionStateRunnable);
                isConnecting = false;
                btnScanWifi.setEnabled(true);
                btnScanWifi.setAlpha(1f);
                Toast.makeText(this, "Error al conectar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = "\"" + result.SSID + "\"";

            if (result.capabilities.toUpperCase().contains("WPA")) {
                config.preSharedKey = "\"" + password + "\"";
            } else {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            }

            int netId = wifiManager.addNetwork(config);
            if (netId != -1) {
                wifiManager.disconnect();
                wifiManager.enableNetwork(netId, true);
                wifiManager.reconnect();
                Toast.makeText(this, "Intentando conectar a " + result.SSID, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Error al agregar la red", Toast.LENGTH_SHORT).show();
            }

            handler.removeCallbacks(resetConnectionStateRunnable);
            isConnecting = false;
            btnScanWifi.setEnabled(true);
            btnScanWifi.setAlpha(1f);
        }
    }



    @SuppressLint("MissingPermission")
    private void disconnectFromNetwork(String ssid) {
        btnScanWifi.setEnabled(false);
        btnScanWifi.setAlpha(0.5f);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (connectivityManager != null) {
                connectivityManager.bindProcessToNetwork(null);
                connectedSsid = null;
                Toast.makeText(this, "Desconectado de la red: " + ssid, Toast.LENGTH_SHORT).show();

                new android.os.Handler().postDelayed(() -> {
                    btnScanWifi.setEnabled(true);
                    btnScanWifi.setAlpha(1.0f);
                    scanWifiNetworks();
                }, 3000);
            } else {
                Toast.makeText(this, "No fue posible desconectarse", Toast.LENGTH_SHORT).show();
                btnScanWifi.setEnabled(true);
                btnScanWifi.setAlpha(1.0f);
            }
        } else {
            wifiManager.disconnect();
            connectedSsid = null;
            Toast.makeText(this, "Desconectado de la red: " + ssid, Toast.LENGTH_SHORT).show();

            new android.os.Handler().postDelayed(() -> {
                btnScanWifi.setEnabled(true);
                btnScanWifi.setAlpha(1.0f);
                scanWifiNetworks();
            }, 3000);
        }
    }



    @SuppressLint("MissingPermission")
    private void forgetWifiNetwork(String ssid) {

        btnScanWifi.setEnabled(false);
        btnScanWifi.setAlpha(0.5f);

        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();

        if (configuredNetworks == null || configuredNetworks.isEmpty()) {
            Toast.makeText(this, "No se encontraron redes configuradas", Toast.LENGTH_SHORT).show();
            btnScanWifi.setEnabled(true);
            btnScanWifi.setAlpha(1.0f);
            return;
        }

        boolean found = false;
        for (WifiConfiguration config : configuredNetworks) {
            if (config.SSID != null && config.SSID.equals("\"" + ssid + "\"")) {
                wifiManager.removeNetwork(config.networkId);
                wifiManager.saveConfiguration();
                Toast.makeText(this, "Red olvidada: " + ssid, Toast.LENGTH_SHORT).show();
                found = true;
                break;
            }
        }
        if (!found) {
            Toast.makeText(this, "Red no encontrada en configuraciones", Toast.LENGTH_SHORT).show();
            btnScanWifi.setEnabled(true);
            btnScanWifi.setAlpha(1.0f);
            return;
        }

        new android.os.Handler().postDelayed(() -> {
            btnScanWifi.setEnabled(true);
            btnScanWifi.setAlpha(1.0f);
            scanWifiNetworks();
        }, 3000);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!wifiManager.isWifiEnabled()) {
                adapter.clear();
                displayScanResults.clear();
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "WiFi Apagado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(wifiScanReceiver);

        if (networkCallback != null && connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                scanWifiNetworks();
            } else {
                Toast.makeText(this, "Permisos necesarios para escanear WiFi no concedidos", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}