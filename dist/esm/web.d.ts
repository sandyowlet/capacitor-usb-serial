import { WebPlugin } from '@capacitor/core';
import type { UsbSerialPlugin, UsbDevice } from './definitions';
export declare class UsbSerialWeb extends WebPlugin implements UsbSerialPlugin {
    private port;
    private reader;
    private writer;
    private listening;
    private availablePorts;
    private connected;
    constructor();
    private checkWebSerialSupport;
    requestPermission(options?: {
        deviceId?: number;
    }): Promise<{
        granted: boolean;
    }>;
    listDevices(): Promise<{
        devices: UsbDevice[];
    }>;
    private getDeviceName;
    connect(options: {
        deviceId: number;
        serialOptions?: any;
    }): Promise<{
        connected: boolean;
    }>;
    disconnect(): Promise<{
        disconnected: boolean;
    }>;
    write(options: {
        data: string;
    }): Promise<{
        bytesWritten: number;
    }>;
    read(): Promise<{
        data: string;
    }>;
    startListening(): Promise<void>;
    stopListening(): Promise<void>;
    private listenForData;
    private processDataLine;
    private stringToHex;
    private isHexData;
    private setupSerialPortListeners;
    private notifyError;
}
