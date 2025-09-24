package dev.emmanuelrobinson.capacitorusbserial;

import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "UsbSerial")
public class UsbSerialPlugin extends Plugin {
    
    private static final String TAG = "UsbSerialPlugin";
    private UsbSerial implementation;
    
    public UsbSerialPlugin() {
        super();
        Log.d(TAG, "UsbSerialPlugin constructor called");
    }
    
    @Override
    public void load() {
        try {
            Log.d(TAG, "UsbSerialPlugin load() called");
            implementation = new UsbSerial(getContext(), this);
            Log.d(TAG, "UsbSerial implementation created successfully");
            implementation.initialize();
            Log.d(TAG, "UsbSerial implementation initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in UsbSerialPlugin load()", e);
            throw new RuntimeException("Failed to load USB Serial plugin", e);
        }
    }
    
    @Override
    protected void handleOnDestroy() {
        if (implementation != null) {
            implementation.cleanup();
        }
        super.handleOnDestroy();
    }
    
    @PluginMethod
    public void requestPermission(PluginCall call) {
        implementation.requestPermission(call);
    }
    
    @PluginMethod
    public void listDevices(PluginCall call) {
        implementation.listDevices(call);
    }
    
    @PluginMethod
    public void connect(PluginCall call) {
        implementation.connect(call);
    }
    
    @PluginMethod
    public void disconnect(PluginCall call) {
        implementation.disconnect(call);
    }
    
    @PluginMethod
    public void write(PluginCall call) {
        implementation.write(call);
    }
    
    @PluginMethod
    public void startListening(PluginCall call) {
        implementation.startListening(call);
    }
    
    @PluginMethod
    public void stopListening(PluginCall call) {
        implementation.stopListening(call);
    }
    
    // Public method to expose notifyListeners functionality to UsbSerial
    public void notifyListenersFromImplementation(String eventName, JSObject data) {
        this.notifyListeners(eventName, data);
    }
}