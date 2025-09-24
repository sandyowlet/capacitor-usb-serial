#import <Capacitor/Capacitor.h>

CAP_PLUGIN(UsbSerialPlugin, "UsbSerial",
    CAP_PLUGIN_METHOD(requestPermission, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(listDevices, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(connect, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(disconnect, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(write, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(read, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(startListening, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(stopListening, CAPPluginReturnPromise);
)