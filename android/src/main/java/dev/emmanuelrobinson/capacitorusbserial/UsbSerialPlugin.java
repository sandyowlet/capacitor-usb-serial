package dev.emmanuelrobinson.capacitorusbserial;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "UsbSerial")
public class UsbSerialPlugin extends Plugin {
    
    private UsbSerial implementation;
    
    @Override
    public void load() {
        implementation = new UsbSerial(getContext(), this);
        implementation.initialize();
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
}