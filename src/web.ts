import { WebPlugin } from '@capacitor/core';
import type { UsbSerialPlugin, UsbDevice, DataReceivedEvent, DeviceAttachedEvent, DeviceDetachedEvent, ErrorEvent } from './definitions';

export class UsbSerialWeb extends WebPlugin implements UsbSerialPlugin {
  private port: any = null;
  private reader: any = null;
  private writer: any = null;
  private listening = false;
  private availablePorts: any[] = [];
  private connected = false;

  constructor() {
    super();
    this.checkWebSerialSupport();
    this.setupSerialPortListeners();
  }

  private checkWebSerialSupport(): void {
    if (!('serial' in navigator)) {
      console.error('Web Serial API not supported in this browser');
      this.notifyError('Web Serial API not supported. Please use Chrome/Edge 89+ or enable experimental web platform features.');
    }
  }

  async requestPermission(options?: { deviceId?: number }): Promise<{ granted: boolean }> {
    if (!('serial' in navigator)) {
      throw new Error('Web Serial API not supported');
    }
    
    try {
      // If deviceId is provided, try to use existing port
      if (options?.deviceId !== undefined) {
        const ports = await (navigator as any).serial.getPorts();
        if (ports[options.deviceId]) {
          this.port = ports[options.deviceId];
          return { granted: true };
        }
      }
      
      // Request new port with ESP32 filter
      const requestOptions = {
        filters: [
          { usbVendorId: 0x10C4 }, // Silicon Labs CP210x
          { usbVendorId: 0x1A86 }, // QinHeng Electronics CH340
          { usbVendorId: 0x0403 }, // FTDI
          { usbVendorId: 0x303A }, // Espressif (ESP32)
        ]
      };
      
      this.port = await (navigator as any).serial.requestPort(requestOptions);
      return { granted: true };
    } catch (e) {
      const errorMsg = 'Permission denied: ' + (e as Error).message;
      console.error(errorMsg);
      this.notifyError(errorMsg);
      return { granted: false };
    }
  }

  async listDevices(): Promise<{ devices: UsbDevice[] }> {
    if (!('serial' in navigator)) {
      throw new Error('Web Serial API not supported');
    }
    
    try {
      const ports = await (navigator as any).serial.getPorts();
      this.availablePorts = ports;
      
      const devices: UsbDevice[] = await Promise.all(
        ports.map(async (port: any, index: number) => {
          const info = port.getInfo ? port.getInfo() : {};
          return {
            deviceId: index,
            vendorId: info.usbVendorId || 0,
            productId: info.usbProductId || 0,
            deviceName: this.getDeviceName(info.usbVendorId, info.usbProductId, index),
            manufacturerName: info.manufacturerName,
            serialNumber: info.serialNumber,
          };
        })
      );
      
      console.log('Web Serial devices found:', devices);
      return { devices };
    } catch (error) {
      console.error('Error listing devices:', error);
      throw error;
    }
  }

  private getDeviceName(vendorId: number, _productId: number, index: number): string {
    const vendorMap: { [key: number]: string } = {
      0x10C4: 'Silicon Labs CP210x',
      0x1A86: 'QinHeng CH340',
      0x0403: 'FTDI',
      0x303A: 'Espressif ESP32',
    };
    
    const vendorName = vendorMap[vendorId] || 'Unknown';
    return `${vendorName} (Port ${index})`;
  }

  async connect(options: { 
    deviceId: number; 
    serialOptions?: any 
  }): Promise<{ connected: boolean }> {
    try {
      // Get the specific port for the deviceId
      if (!this.port) {
        const ports = await (navigator as any).serial.getPorts();
        this.port = ports[options.deviceId];
      }
      
      if (!this.port) {
        const errorMsg = `No port available for device ID ${options.deviceId}`;
        this.notifyError(errorMsg);
        throw new Error(errorMsg);
      }
      
      // Configure serial options
      const serialOptions = {
        baudRate: options.serialOptions?.baudRate || 115200,
        dataBits: options.serialOptions?.dataBits || 8,
        stopBits: options.serialOptions?.stopBits || 1,
        parity: options.serialOptions?.parity || 'none',
        bufferSize: 32768, // Increase buffer size for high-speed data
        flowControl: 'none'
      };
      
      console.log('Connecting with options:', serialOptions);
      
      await this.port.open(serialOptions);
      this.connected = true;
      
      // Set up readable stream
      const textDecoder = new TextDecoderStream();
      const readableStreamClosed = this.port.readable.pipeTo(textDecoder.writable);
      this.reader = textDecoder.readable.getReader();
      
      // Set up writable stream
      const textEncoder = new TextEncoderStream();
      const writableStreamClosed = textEncoder.readable.pipeTo(this.port.writable);
      this.writer = textEncoder.writable.getWriter();
      
      // Handle stream closures
      readableStreamClosed.catch(() => {
        console.log('Readable stream closed');
      });
      
      writableStreamClosed.catch(() => {
        console.log('Writable stream closed');
      });
      
      console.log('Connected to serial port successfully');
      
      this.notifyListeners('connectionStateChanged', { 
        connected: true, 
        deviceId: options.deviceId 
      });
      
      return { connected: true };
    } catch (error) {
      this.connected = false;
      const errorMsg = 'Connection failed: ' + (error as Error).message;
      console.error(errorMsg);
      this.notifyError(errorMsg);
      throw new Error(errorMsg);
    }
  }

  async disconnect(): Promise<{ disconnected: boolean }> {
    console.log('Disconnecting from serial port...');
    this.listening = false;
    this.connected = false;
    
    try {
      if (this.reader) {
        await this.reader.cancel();
        this.reader.releaseLock();
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
      console.log('Disconnected successfully');
      
      return { disconnected: true };
    } catch (error) {
      console.error('Error during disconnect:', error);
      // Still notify that we're disconnected
      this.notifyListeners('connectionStateChanged', { connected: false });
      return { disconnected: true };
    }
  }

  async write(options: { data: string }): Promise<{ bytesWritten: number }> {
    if (!this.writer || !this.connected) {
      const errorMsg = 'Cannot write: Not connected to any device';
      this.notifyError(errorMsg);
      throw new Error(errorMsg);
    }
    
    try {
      await this.writer.write(options.data);
      console.log('Data written to serial port:', options.data);
      return { bytesWritten: options.data.length };
    } catch (error) {
      const errorMsg = 'Write failed: ' + (error as Error).message;
      this.notifyError(errorMsg);
      throw new Error(errorMsg);
    }
  }

  async read(): Promise<{ data: string }> {
    if (!this.reader || !this.connected) {
      const errorMsg = 'Cannot read: Not connected to any device';
      this.notifyError(errorMsg);
      throw new Error(errorMsg);
    }
    
    try {
      const { value, done } = await this.reader.read();
      if (done) {
        throw new Error('Stream closed');
      }
      
      return { data: value || '' };
    } catch (error) {
      const errorMsg = 'Read failed: ' + (error as Error).message;
      this.notifyError(errorMsg);
      throw new Error(errorMsg);
    }
  }

  async startListening(): Promise<void> {
    if (!this.connected) {
      throw new Error('Cannot start listening: Not connected to any device');
    }
    
    console.log('Starting listening for serial data...');
    this.listening = true;
    this.listenForData();
  }

  async stopListening(): Promise<void> {
    console.log('Stopping listening for serial data...');
    this.listening = false;
  }

  private async listenForData() {
    console.log('Starting data listener for web serial...');
    let buffer = '';
    
    while (this.listening && this.reader && this.connected) {
      try {
        const { value, done } = await this.reader.read();
        
        if (done) {
          console.log('Reader stream ended');
          break;
        }
        
        if (!value) {
          continue;
        }
        
        // Accumulate data in buffer
        buffer += value;
        
        // Process complete lines
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // Keep incomplete line in buffer
        
        for (const line of lines) {
          if (line.trim()) {
            this.processDataLine(line.trim());
          }
        }
        
        // Also process the complete buffer as hex if it looks like raw data
        if (buffer.length > 0 && this.isHexData(buffer.trim())) {
          this.processDataLine(buffer.trim());
          buffer = '';
        }
        
      } catch (error) {
        if (this.listening) {
          console.error('Read error:', error);
          this.notifyError('Read error: ' + (error as Error).message);
        }
        break;
      }
    }
    
    console.log('Data listener stopped');
  }

  private processDataLine(line: string): void {
    // Convert string to hex representation
    const hexData = this.stringToHex(line);
    
    const event: DataReceivedEvent = {
      data: line,
      hexData: hexData,
      timestamp: Date.now(),
      deviceId: 0
    };
    
    console.log('Web Serial data received:', event);
    this.notifyListeners('dataReceived', event);
  }

  private stringToHex(str: string): string {
    // If the string is already hex format, return it uppercase
    if (this.isHexData(str)) {
      return str.toUpperCase();
    }
    
    // Convert string characters to hex
    return Array.from(str)
      .map((char: string) => char.charCodeAt(0).toString(16).padStart(2, '0'))
      .join('')
      .toUpperCase();
  }

  private isHexData(str: string): boolean {
    // Check if string contains only hex characters and is reasonable length
    return /^[0-9A-Fa-f\s]+$/.test(str.replace(/\s/g, '')) && str.replace(/\s/g, '').length >= 4;
  }

  private setupSerialPortListeners(): void {
    if (!('serial' in navigator)) {
      console.warn('Web Serial API not available, skipping port listeners setup');
      return;
    }

    console.log('Setting up Web Serial port listeners...');

    (navigator as any).serial.addEventListener('connect', async (event: any) => {
      console.log('Serial port connected:', event);
      
      try {
        const port = event.target;
        const info = port.getInfo ? port.getInfo() : {};
        
        const attachedEvent: DeviceAttachedEvent = {
          deviceId: this.availablePorts.length, // Assign next available ID
          vendorId: info.usbVendorId || 0,
          productId: info.usbProductId || 0,
          deviceName: this.getDeviceName(info.usbVendorId, info.usbProductId, this.availablePorts.length),
          manufacturerName: info.manufacturerName,
          serialNumber: info.serialNumber,
        };
        
        this.availablePorts.push(port);
        this.notifyListeners('deviceAttached', attachedEvent);
        
        // Auto-connect to ESP32 devices (ESP32 vendor IDs)
        const esp32VendorIds = [0x10C4, 0x1A86, 0x0403, 0x303A];
        if (esp32VendorIds.includes(info.usbVendorId)) {
          console.log('ESP32 device detected, triggering auto-connect notification');
          // Don't auto-connect here, let the service handle it
        }
      } catch (error) {
        console.error('Error handling device attachment:', error);
      }
    });

    (navigator as any).serial.addEventListener('disconnect', async (event: any) => {
      console.log('Serial port disconnected:', event);
      
      try {
        const port = event.target;
        const portIndex = this.availablePorts.indexOf(port);
        
        if (portIndex >= 0) {
          const detachedEvent: DeviceDetachedEvent = {
            deviceId: portIndex,
          };
          
          this.availablePorts.splice(portIndex, 1);
          this.notifyListeners('deviceDetached', detachedEvent);
          
          // If this was our connected port, disconnect
          if (this.port === port) {
            console.log('Connected port was removed, disconnecting...');
            await this.disconnect();
          }
        }
      } catch (error) {
        console.error('Error handling device detachment:', error);
      }
    });
  }

  private notifyError(message: string): void {
    const errorEvent: ErrorEvent = { message };
    this.notifyListeners('error', errorEvent);
  }
}