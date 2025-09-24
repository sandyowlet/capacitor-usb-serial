package dev.emmanuelrobinson.capacitorusbserial;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsbSerial implements SerialInputOutputManager.Listener {
    private static final String TAG = "UsbSerial";
    private static final String ACTION_USB_PERMISSION = "dev.emmanuelrobinson.capacitorusbserial.USB_PERMISSION";
    
    private final Context context;
    private final UsbSerialPlugin plugin;
    private UsbManager usbManager;
    private UsbSerialPort serialPort;
    private SerialInputOutputManager ioManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UsbDevice currentDevice;
    
    private PluginCall pendingPermissionCall = null;
    
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Broadcast received with action: " + action);
            
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    
                    Log.d(TAG, "USB permission result for device " + (device != null ? device.getDeviceName() + " (ID: " + device.getDeviceId() + ")" : "null") + ": " + granted);
                    
                    // Debug: Log all intent extras
                    android.os.Bundle extras = intent.getExtras();
                    if (extras != null) {
                        Log.d(TAG, "Intent extras:");
                        for (String key : extras.keySet()) {
                            Object value = extras.get(key);
                            Log.d(TAG, "  " + key + " = " + value + " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                        }
                    } else {
                        Log.d(TAG, "No intent extras");
                    }
                    
                    if (pendingPermissionCall != null) {
                        JSObject ret = new JSObject();
                        ret.put("granted", granted);
                        if (granted) {
                            Log.d(TAG, "Permission granted, resolving call");
                            pendingPermissionCall.resolve(ret);
                        } else {
                            Log.d(TAG, "Permission denied, rejecting call");
                            pendingPermissionCall.reject("USB permission denied by user");
                        }
                        pendingPermissionCall = null;
                    } else {
                        Log.w(TAG, "Received permission result but no pending call");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // Notify about device attachment
                    JSObject deviceInfo = createDeviceInfo(device);
                    plugin.notifyListenersFromImplementation("deviceAttached", deviceInfo);
                    Log.d(TAG, "USB device attached: " + device.getDeviceName());
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // Notify about device detachment
                    JSObject event = new JSObject();
                    event.put("deviceId", device.getDeviceId());
                    plugin.notifyListenersFromImplementation("deviceDetached", event);
                    Log.d(TAG, "USB device detached: " + device.getDeviceName());
                    
                    // If this was our connected device, handle disconnection
                    if (currentDevice != null && currentDevice.equals(device)) {
                        disconnect();
                        plugin.notifyListenersFromImplementation("connectionStateChanged", new JSObject()
                            .put("connected", false)
                            .put("deviceId", device.getDeviceId()));
                    }
                }
            }
        }
    };
    
    public UsbSerial(Context context, UsbSerialPlugin plugin) {
        this.context = context;
        this.plugin = plugin;
    }
    
    public void initialize() {
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        
        Log.d(TAG, "Registering broadcast receiver with actions: " + ACTION_USB_PERMISSION);
        
        // Try registering with EXPORTED flag to receive system broadcasts
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(usbReceiver, filter);
        }
        
        Log.d(TAG, "USB broadcast receiver registered successfully");
    }
    
    public void cleanup() {
        try {
            context.unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
        
        // Cancel any pending permission calls
        if (pendingPermissionCall != null) {
            pendingPermissionCall.reject("Plugin cleanup - permission cancelled");
            pendingPermissionCall = null;
        }
        
        disconnect();
        executor.shutdown();
    }
    
    public void requestPermission(PluginCall call) {
        Integer deviceId = call.getInt("deviceId");
        UsbDevice device = null;
        
        if (deviceId != null) {
            for (UsbDevice d : usbManager.getDeviceList().values()) {
                if (d.getDeviceId() == deviceId) {
                    device = d;
                    break;
                }
            }
        } else {
            // Get first available device
            if (!usbManager.getDeviceList().isEmpty()) {
                device = usbManager.getDeviceList().values().iterator().next();
            }
        }
        
        if (device == null) {
            call.reject("No USB device found");
            return;
        }
        
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "Device already has permission");
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
        } else {
            Log.d(TAG, "Requesting permission for device: " + device.getDeviceName() + " (ID: " + device.getDeviceId() + ")");
            
            // Store the call and device to respond to later
            pendingPermissionCall = call;
            final UsbDevice permissionDevice = device;
            
            // Create intent with the exact action string
            Intent permissionIntent = new Intent(ACTION_USB_PERMISSION);
            permissionIntent.setPackage(context.getPackageName()); // Ensure it's delivered to our app
            Log.d(TAG, "Created intent with action: " + ACTION_USB_PERMISSION + " and package: " + context.getPackageName());
            
            PendingIntent pendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                pendingIntent = PendingIntent.getBroadcast(context, 0, permissionIntent, 
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                pendingIntent = PendingIntent.getBroadcast(context, 0, permissionIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT);
            }
            
            Log.d(TAG, "Created PendingIntent, requesting permission from UsbManager...");
            usbManager.requestPermission(device, pendingIntent);
            Log.d(TAG, "Permission request sent to system");
            
            // Alternative approach: Poll for permission changes
            final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            final int[] pollCount = {0};
            final Runnable pollPermission = new Runnable() {
                @Override
                public void run() {
                    pollCount[0]++;
                    Log.d(TAG, "Polling permission status, attempt: " + pollCount[0]);
                    
                    if (usbManager.hasPermission(permissionDevice)) {
                        Log.d(TAG, "Permission granted detected via polling");
                        if (pendingPermissionCall != null) {
                            JSObject ret = new JSObject();
                            ret.put("granted", true);
                            pendingPermissionCall.resolve(ret);
                            pendingPermissionCall = null;
                        }
                        return;
                    }
                    
                    // Continue polling for up to 30 seconds
                    if (pollCount[0] < 60 && pendingPermissionCall != null) {
                        handler.postDelayed(this, 500); // Poll every 500ms
                    } else if (pendingPermissionCall != null) {
                        Log.w(TAG, "Permission polling timed out");
                        pendingPermissionCall.reject("Permission request timeout - user did not respond");
                        pendingPermissionCall = null;
                    }
                }
            };
            
            // Start polling after a short delay
            handler.postDelayed(pollPermission, 1000);
        }
    }
    
    public void listDevices(PluginCall call) {
        JSONArray devicesArray = new JSONArray();
        
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager);
        
        for (UsbSerialDriver driver : availableDrivers) {
            UsbDevice device = driver.getDevice();
            try {
                JSONObject deviceObj = new JSONObject();
                deviceObj.put("deviceId", device.getDeviceId());
                deviceObj.put("vendorId", device.getVendorId());
                deviceObj.put("productId", device.getProductId());
                deviceObj.put("deviceName", device.getDeviceName());
                
                // Only access privileged properties if we have permission
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        if (usbManager.hasPermission(device)) {
                            deviceObj.put("manufacturerName", device.getManufacturerName());
                            deviceObj.put("serialNumber", device.getSerialNumber());
                        } else {
                            deviceObj.put("manufacturerName", JSONObject.NULL);
                            deviceObj.put("serialNumber", JSONObject.NULL);
                        }
                    } catch (SecurityException e) {
                        Log.w(TAG, "Cannot access device properties without permission for device " + device.getDeviceId(), e);
                        deviceObj.put("manufacturerName", JSONObject.NULL);
                        deviceObj.put("serialNumber", JSONObject.NULL);
                    }
                }
                
                devicesArray.put(deviceObj);
            } catch (JSONException e) {
                Log.e(TAG, "Error creating device JSON", e);
            }
        }
        
        JSObject ret = new JSObject();
        ret.put("devices", devicesArray);
        call.resolve(ret);
    }
    
    public void connect(PluginCall call) {
        Integer deviceId = call.getInt("deviceId");
        if (deviceId == null) {
            call.reject("Device ID is required");
            return;
        }
        
        JSObject serialOptions = call.getObject("serialOptions", new JSObject());
        int baudRate = serialOptions.getInteger("baudRate", 115200);
        int dataBits = serialOptions.getInteger("dataBits", 8);
        int stopBits = serialOptions.getInteger("stopBits", UsbSerialPort.STOPBITS_1);
        int parity = UsbSerialPort.PARITY_NONE;
        
        String parityStr = serialOptions.getString("parity", "none");
        switch (parityStr) {
            case "odd": parity = UsbSerialPort.PARITY_ODD; break;
            case "even": parity = UsbSerialPort.PARITY_EVEN; break;
            case "mark": parity = UsbSerialPort.PARITY_MARK; break;
            case "space": parity = UsbSerialPort.PARITY_SPACE; break;
        }
        
        UsbDevice device = null;
        for (UsbDevice d : usbManager.getDeviceList().values()) {
            if (d.getDeviceId() == deviceId) {
                device = d;
                break;
            }
        }
        
        if (device == null) {
            call.reject("Device not found");
            return;
        }
        
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager);
        
        UsbSerialDriver driver = null;
        for (UsbSerialDriver d : availableDrivers) {
            if (d.getDevice().getDeviceId() == deviceId) {
                driver = d;
                break;
            }
        }
        
        if (driver == null) {
            call.reject("No driver for device");
            return;
        }
        
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            call.reject("Cannot open device");
            return;
        }
        
        try {
            serialPort = driver.getPorts().get(0);
            serialPort.open(connection);
            serialPort.setParameters(baudRate, dataBits, stopBits, parity);
            
            currentDevice = device;
            
            // Start IO manager
            ioManager = new SerialInputOutputManager(serialPort, this);
            ioManager.start();
            
            JSObject ret = new JSObject();
            ret.put("connected", true);
            call.resolve(ret);
            
            plugin.notifyListenersFromImplementation("connectionStateChanged", new JSObject()
                .put("connected", true)
                .put("deviceId", deviceId));
                
        } catch (IOException e) {
            JSObject errorEvent = new JSObject();
            errorEvent.put("message", "Failed to connect: " + e.getMessage());
            plugin.notifyListenersFromImplementation("error", errorEvent);
            call.reject("Failed to connect: " + e.getMessage());
        }
    }
    
    public void disconnect(PluginCall call) {
        disconnect();
        JSObject ret = new JSObject();
        ret.put("disconnected", true);
        call.resolve(ret);
    }
    
    private void disconnect() {
        if (ioManager != null) {
            ioManager.stop();
            ioManager = null;
        }
        
        if (serialPort != null) {
            try {
                serialPort.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing port", e);
            }
            serialPort = null;
        }
        
        currentDevice = null;
    }
    
    public void write(PluginCall call) {
        String data = call.getString("data");
        if (data == null) {
            call.reject("No data provided");
            return;
        }
        
        if (serialPort == null) {
            call.reject("Not connected");
            return;
        }
        
        try {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            serialPort.write(bytes, 1000);
            
            JSObject ret = new JSObject();
            ret.put("bytesWritten", bytes.length);
            call.resolve(ret);
        } catch (IOException e) {
            JSObject errorEvent = new JSObject();
            errorEvent.put("message", "Write failed: " + e.getMessage());
            plugin.notifyListenersFromImplementation("error", errorEvent);
            call.reject("Write failed: " + e.getMessage());
        }
    }
    
    public void startListening(PluginCall call) {
        if (serialPort == null) {
            call.reject("Not connected");
            return;
        }
        call.resolve();
    }
    
    public void stopListening(PluginCall call) {
        call.resolve();
    }
    
    @Override
    public void onNewData(byte[] data) {
        String dataStr = new String(data, StandardCharsets.UTF_8);
        String hexStr = bytesToHex(data);
        
        JSObject event = new JSObject();
        event.put("data", dataStr);
        event.put("hexData", hexStr);
        event.put("timestamp", System.currentTimeMillis());
        if (currentDevice != null) {
            event.put("deviceId", currentDevice.getDeviceId());
        }
        
        plugin.notifyListenersFromImplementation("dataReceived", event);
    }
    
    @Override
    public void onRunError(Exception e) {
        Log.e(TAG, "Runner error", e);
        
        // Notify about the error
        JSObject errorEvent = new JSObject();
        errorEvent.put("message", "Serial communication error: " + e.getMessage());
        plugin.notifyListenersFromImplementation("error", errorEvent);
    }
    
    private JSObject createDeviceInfo(UsbDevice device) {
        JSObject deviceInfo = new JSObject();
        deviceInfo.put("deviceId", device.getDeviceId());
        deviceInfo.put("vendorId", device.getVendorId());
        deviceInfo.put("productId", device.getProductId());
        deviceInfo.put("deviceName", device.getDeviceName());
        
        // Only access privileged properties if we have permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                if (usbManager.hasPermission(device)) {
                    deviceInfo.put("manufacturerName", device.getManufacturerName());
                    deviceInfo.put("serialNumber", device.getSerialNumber());
                } else {
                    deviceInfo.put("manufacturerName", null);
                    deviceInfo.put("serialNumber", null);
                }
            } catch (SecurityException e) {
                Log.w(TAG, "Cannot access device properties without permission", e);
                deviceInfo.put("manufacturerName", null);
                deviceInfo.put("serialNumber", null);
            }
        }
        
        return deviceInfo;
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }
}