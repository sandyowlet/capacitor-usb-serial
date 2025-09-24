# capacitor-usb-serial

Capacitor-USB-Serial is a native plugin for Capacitor that enables communication with USB serial devices on Android and iOS platforms. Seamlessly connect to IoT devices, microcontrollers, and embedded systems to receive real-time data streams directly into your cross-platform mobile apps for telemetry monitoring, drone tracking, or hardware integration applications.

## Install

```bash
npm install capacitor-usb-serial
npx cap sync
```

## API

<docgen-index>

* [`requestPermission(...)`](#requestpermission)
* [`listDevices()`](#listdevices)
* [`connect(...)`](#connect)
* [`disconnect()`](#disconnect)
* [`write(...)`](#write)
* [`read()`](#read)
* [`startListening()`](#startlistening)
* [`stopListening()`](#stoplistening)
* [`addListener('dataReceived', ...)`](#addlistenerdatareceived-)
* [`addListener('connectionStateChanged', ...)`](#addlistenerconnectionstatechanged-)
* [`addListener('deviceAttached', ...)`](#addlistenerdeviceattached-)
* [`addListener('deviceDetached', ...)`](#addlistenerdevicedetached-)
* [`addListener('error', ...)`](#addlistenererror-)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### requestPermission(...)

```typescript
requestPermission(options?: { deviceId?: number | undefined; } | undefined) => Promise<{ granted: boolean; }>
```

Request permission to access USB devices

| Param         | Type                                |
| ------------- | ----------------------------------- |
| **`options`** | <code>{ deviceId?: number; }</code> |

**Returns:** <code>Promise&lt;{ granted: boolean; }&gt;</code>

--------------------


### listDevices()

```typescript
listDevices() => Promise<{ devices: UsbDevice[]; }>
```

List all connected USB devices

**Returns:** <code>Promise&lt;{ devices: UsbDevice[]; }&gt;</code>

--------------------


### connect(...)

```typescript
connect(options: { deviceId: number; serialOptions?: UsbSerialOptions; }) => Promise<{ connected: boolean; }>
```

Connect to a USB device

| Param         | Type                                                                                                 |
| ------------- | ---------------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ deviceId: number; serialOptions?: <a href="#usbserialoptions">UsbSerialOptions</a>; }</code> |

**Returns:** <code>Promise&lt;{ connected: boolean; }&gt;</code>

--------------------


### disconnect()

```typescript
disconnect() => Promise<{ disconnected: boolean; }>
```

Disconnect from the current device

**Returns:** <code>Promise&lt;{ disconnected: boolean; }&gt;</code>

--------------------


### write(...)

```typescript
write(options: { data: string; }) => Promise<{ bytesWritten: number; }>
```

Write data to the serial port

| Param         | Type                           |
| ------------- | ------------------------------ |
| **`options`** | <code>{ data: string; }</code> |

**Returns:** <code>Promise&lt;{ bytesWritten: number; }&gt;</code>

--------------------


### read()

```typescript
read() => Promise<{ data: string; }>
```

Read data from the serial port

**Returns:** <code>Promise&lt;{ data: string; }&gt;</code>

--------------------


### startListening()

```typescript
startListening() => Promise<void>
```

Start listening for data

--------------------


### stopListening()

```typescript
stopListening() => Promise<void>
```

Stop listening for data

--------------------


### addListener('dataReceived', ...)

```typescript
addListener(eventName: 'dataReceived', listenerFunc: (event: DataReceivedEvent) => void) => Promise<{ remove: () => void; }>
```

Add listener for data received events

| Param              | Type                                                                                |
| ------------------ | ----------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'dataReceived'</code>                                                         |
| **`listenerFunc`** | <code>(event: <a href="#datareceivedevent">DataReceivedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; void; }&gt;</code>

--------------------


### addListener('connectionStateChanged', ...)

```typescript
addListener(eventName: 'connectionStateChanged', listenerFunc: (event: { connected: boolean; deviceId?: number; }) => void) => Promise<{ remove: () => void; }>
```

Add listener for connection state changes

| Param              | Type                                                                        |
| ------------------ | --------------------------------------------------------------------------- |
| **`eventName`**    | <code>'connectionStateChanged'</code>                                       |
| **`listenerFunc`** | <code>(event: { connected: boolean; deviceId?: number; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; void; }&gt;</code>

--------------------


### addListener('deviceAttached', ...)

```typescript
addListener(eventName: 'deviceAttached', listenerFunc: (event: DeviceAttachedEvent) => void) => Promise<{ remove: () => void; }>
```

Add listener for device attached events

| Param              | Type                                                                                    |
| ------------------ | --------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'deviceAttached'</code>                                                           |
| **`listenerFunc`** | <code>(event: <a href="#deviceattachedevent">DeviceAttachedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; void; }&gt;</code>

--------------------


### addListener('deviceDetached', ...)

```typescript
addListener(eventName: 'deviceDetached', listenerFunc: (event: DeviceDetachedEvent) => void) => Promise<{ remove: () => void; }>
```

Add listener for device detached events

| Param              | Type                                                                                    |
| ------------------ | --------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'deviceDetached'</code>                                                           |
| **`listenerFunc`** | <code>(event: <a href="#devicedetachedevent">DeviceDetachedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; void; }&gt;</code>

--------------------


### addListener('error', ...)

```typescript
addListener(eventName: 'error', listenerFunc: (event: ErrorEvent) => void) => Promise<{ remove: () => void; }>
```

Add listener for error events

| Param              | Type                                                                  |
| ------------------ | --------------------------------------------------------------------- |
| **`eventName`**    | <code>'error'</code>                                                  |
| **`listenerFunc`** | <code>(event: <a href="#errorevent">ErrorEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; void; }&gt;</code>

--------------------


### Interfaces


#### UsbDevice

| Prop                   | Type                |
| ---------------------- | ------------------- |
| **`deviceId`**         | <code>number</code> |
| **`vendorId`**         | <code>number</code> |
| **`productId`**        | <code>number</code> |
| **`deviceName`**       | <code>string</code> |
| **`manufacturerName`** | <code>string</code> |
| **`serialNumber`**     | <code>string</code> |


#### UsbSerialOptions

| Prop           | Type                                                        |
| -------------- | ----------------------------------------------------------- |
| **`baudRate`** | <code>number</code>                                         |
| **`dataBits`** | <code>number</code>                                         |
| **`stopBits`** | <code>number</code>                                         |
| **`parity`**   | <code>'none' \| 'odd' \| 'even' \| 'mark' \| 'space'</code> |
| **`dtr`**      | <code>boolean</code>                                        |
| **`rts`**      | <code>boolean</code>                                        |


#### DataReceivedEvent

| Prop            | Type                |
| --------------- | ------------------- |
| **`data`**      | <code>string</code> |
| **`hexData`**   | <code>string</code> |
| **`timestamp`** | <code>number</code> |
| **`deviceId`**  | <code>number</code> |


#### DeviceAttachedEvent

| Prop                   | Type                |
| ---------------------- | ------------------- |
| **`deviceId`**         | <code>number</code> |
| **`vendorId`**         | <code>number</code> |
| **`productId`**        | <code>number</code> |
| **`deviceName`**       | <code>string</code> |
| **`manufacturerName`** | <code>string</code> |
| **`serialNumber`**     | <code>string</code> |


#### DeviceDetachedEvent

| Prop           | Type                |
| -------------- | ------------------- |
| **`deviceId`** | <code>number</code> |


#### ErrorEvent

| Prop          | Type                |
| ------------- | ------------------- |
| **`message`** | <code>string</code> |

</docgen-api>
