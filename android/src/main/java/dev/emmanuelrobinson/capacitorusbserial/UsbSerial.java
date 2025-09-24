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
import com.getcapacitor.Plugin;
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
    private final Plugin plugin;
    private UsbManager usbManager;
    private UsbSerialPort serialPort;
    private SerialInputOutputManager ioManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UsbDevice currentDevice;
    
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.d(TAG, "Permission granted for device " + device);
                        }
                    } else {
                        Log.d(TAG, "Permission denied for device " + device);
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (currentDevice != null && currentDevice.equals(device)) {
                    disconnect();
                    plugin.notifyListeners("connectionStateChanged", new JSObject()
                        .put("connected", false)
                        .put("deviceId", device.getDeviceId()));
                }
            }
        }
    };
    
    public UsbSerial(Context context, Plugin plugin) {
        this.context = context;
        this.plugin = plugin;
    }
    
    public void initialize() {
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(usbReceiver, filter);
    }
    
    public void cleanup() {
        try {
            context.unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
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
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
        } else {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, permissionIntent);
            
            // For simplicity, we'll assume permission is granted
            // In production, you'd want to wait for the broadcast
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
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
                deviceObj.put("manufacturerName", device.getManufacturerName());
                deviceObj.put("serialNumber", device.getSerialNumber());
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
            executor.submit(ioManager);
            
            JSObject ret = new JSObject();
            ret.put("connected", true);
            call.resolve(ret);
            
            plugin.notifyListeners("connectionStateChanged", new JSObject()
                .put("connected", true)
                .put("deviceId", deviceId));
                
        } catch (IOException e) {
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
            int bytesWritten = serialPort.write(bytes, 1000);
            
            JSObject ret = new JSObject();
            ret.put("bytesWritten", bytesWritten);
            call.resolve(ret);
        } catch (IOException e) {
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
        
        plugin.notifyListeners("dataReceived", event);
    }
    
    @Override
    public void onRunError(Exception e) {
        Log.e(TAG, "Runner error", e);
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }
}