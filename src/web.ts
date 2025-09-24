import { WebPlugin } from '@capacitor/core';
import type { UsbSerialPlugin, UsbDevice, DataReceivedEvent, DeviceAttachedEvent, DeviceDetachedEvent, ErrorEvent } from './definitions';

export class UsbSerialWeb extends WebPlugin implements UsbSerialPlugin {
  private port: any = null;
  private reader: any = null;
  private writer: any = null;
  private listening = false;

  constructor() {
    super();
    this.setupSerialPortListeners();
  }

  async requestPermission(): Promise<{ granted: boolean }> {
    if (!('serial' in navigator)) {
      throw new Error('Web Serial API not supported');
    }
    
    try {
      this.port = await (navigator as any).serial.requestPort();
      return { granted: true };
    } catch (e) {
      this.notifyError('Permission denied: ' + (e as Error).message);
      return { granted: false };
    }
  }

  async listDevices(): Promise<{ devices: UsbDevice[] }> {
    if (!('serial' in navigator)) {
      throw new Error('Web Serial API not supported');
    }
    
    const ports = await (navigator as any).serial.getPorts();
    const devices: UsbDevice[] = ports.map((_port: any, index: number) => ({
      deviceId: index,
      vendorId: 0,
      productId: 0,
      deviceName: `Serial Port ${index}`,
    }));
    
    return { devices };
  }

  async connect(options: { 
    deviceId: number; 
    serialOptions?: any 
  }): Promise<{ connected: boolean }> {
    if (!this.port) {
      const ports = await (navigator as any).serial.getPorts();
      this.port = ports[options.deviceId] || ports[0];
    }
    
    if (!this.port) {
      this.notifyError('No port available for connection');
      throw new Error('No port available');
    }
    
    const baudRate = options.serialOptions?.baudRate || 115200;
    
    await this.port.open({ baudRate });
    
    const textDecoder = new TextDecoderStream();
    this.port.readable.pipeTo(textDecoder.writable);
    this.reader = textDecoder.readable.getReader();
    
    const textEncoder = new TextEncoderStream();
    textEncoder.readable.pipeTo(this.port.writable);
    this.writer = textEncoder.writable.getWriter();
    
    this.notifyListeners('connectionStateChanged', { 
      connected: true, 
      deviceId: options.deviceId 
    });
    
    return { connected: true };
  }

  async disconnect(): Promise<{ disconnected: boolean }> {
    this.listening = false;
    
    if (this.reader) {
      await this.reader.cancel();
      this.reader = null;
    }
    
    if (this.writer) {
      await this.writer.close();
      this.writer = null;
    }
    
    if (this.port) {
      await this.port.close();
      this.port = null;
    }
    
    this.notifyListeners('connectionStateChanged', { connected: false });
    
    return { disconnected: true };
  }

  async write(options: { data: string }): Promise<{ bytesWritten: number }> {
    if (!this.writer) {
      this.notifyError('Cannot write: Not connected to any device');
      throw new Error('Not connected');
    }
    
    await this.writer.write(options.data);
    return { bytesWritten: options.data.length };
  }

  async read(): Promise<{ data: string }> {
    if (!this.reader) {
      this.notifyError('Cannot read: Not connected to any device');
      throw new Error('Not connected');
    }
    
    const { value, done } = await this.reader.read();
    if (done) {
      throw new Error('Stream closed');
    }
    
    return { data: value };
  }

  async startListening(): Promise<void> {
    this.listening = true;
    this.listenForData();
  }

  async stopListening(): Promise<void> {
    this.listening = false;
  }

  private async listenForData() {
    while (this.listening && this.reader) {
      try {
        const { value, done } = await this.reader.read();
        if (done) {
          break;
        }
        
        const hexData = Array.from(value as string)
          .map((c: string) => c.charCodeAt(0).toString(16).padStart(2, '0'))
          .join('');
        
        const event: DataReceivedEvent = {
          data: value,
          hexData: hexData.toUpperCase(),
          timestamp: Date.now(),
          deviceId: 0
        };
        
        this.notifyListeners('dataReceived', event);
      } catch (error) {
        this.notifyError('Read error: ' + (error as Error).message);
        break;
      }
    }
  }

  private setupSerialPortListeners(): void {
    if ('serial' in navigator) {
      (navigator as any).serial.addEventListener('connect', () => {
        const attachedEvent: DeviceAttachedEvent = {
          deviceId: 0, // Web Serial doesn't provide device ID
          vendorId: 0,
          productId: 0,
          deviceName: 'Web Serial Port',
        };
        this.notifyListeners('deviceAttached', attachedEvent);
      });

      (navigator as any).serial.addEventListener('disconnect', (event: any) => {
        const detachedEvent: DeviceDetachedEvent = {
          deviceId: 0,
        };
        this.notifyListeners('deviceDetached', detachedEvent);
        
        if (this.port === event.target) {
          this.disconnect();
        }
      });
    }
  }

  private notifyError(message: string): void {
    const errorEvent: ErrorEvent = { message };
    this.notifyListeners('error', errorEvent);
  }
}