export interface UsbDevice {
  deviceId: number;
  vendorId: number;
  productId: number;
  deviceName: string;
  manufacturerName?: string;
  serialNumber?: string;
}

export interface UsbSerialOptions {
  baudRate?: number;
  dataBits?: number;
  stopBits?: number;
  parity?: 'none' | 'odd' | 'even' | 'mark' | 'space';
  dtr?: boolean;
  rts?: boolean;
}

export interface DataReceivedEvent {
  data: string;
  hexData: string;
  timestamp: number;
  deviceId: number;
}

export interface UsbSerialPlugin {
  /**
   * Request permission to access USB devices
   */
  requestPermission(options?: { deviceId?: number }): Promise<{ granted: boolean }>;

  /**
   * List all connected USB devices
   */
  listDevices(): Promise<{ devices: UsbDevice[] }>;

  /**
   * Connect to a USB device
   */
  connect(options: {
    deviceId: number;
    serialOptions?: UsbSerialOptions;
  }): Promise<{ connected: boolean }>;

  /**
   * Disconnect from the current device
   */
  disconnect(): Promise<{ disconnected: boolean }>;

  /**
   * Write data to the serial port
   */
  write(options: { data: string }): Promise<{ bytesWritten: number }>;

  /**
   * Read data from the serial port
   */
  read(): Promise<{ data: string }>;

  /**
   * Start listening for data
   */
  startListening(): Promise<void>;

  /**
   * Stop listening for data
   */
  stopListening(): Promise<void>;

  /**
   * Add listener for data received events
   */
  addListener(
    eventName: 'dataReceived',
    listenerFunc: (event: DataReceivedEvent) => void
  ): Promise<{ remove: () => void }>;

  /**
   * Add listener for connection state changes
   */
  addListener(
    eventName: 'connectionStateChanged',
    listenerFunc: (event: { connected: boolean; deviceId?: number }) => void
  ): Promise<{ remove: () => void }>;
}