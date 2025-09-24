import Foundation
import Capacitor
import ExternalAccessory
import Network

public class UsbSerial: NSObject {
    
    // MARK: - Properties
    
    private weak var plugin: CAPPlugin?
    private var session: EASession?
    private var accessory: EAAccessory?
    private let protocolString = "com.yourcompany.serial"
    private var listening = false
    private var readBuffer = Data()
    
    // For network-based serial (if using serial-over-network adapters)
    private var connection: NWConnection?
    private let queue = DispatchQueue(label: "dev.emmanuelrobinson.usbserial", qos: .userInitiated)
    
    // MARK: - Initialization
    
    init(plugin: CAPPlugin) {
        self.plugin = plugin
        super.init()
        
        // Register for accessory notifications
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(accessoryDidConnect(_:)),
            name: .EAAccessoryDidConnect,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(accessoryDidDisconnect(_:)),
            name: .EAAccessoryDidDisconnect,
            object: nil
        )
    }
    
    func cleanup() {
        disconnect()
        NotificationCenter.default.removeObserver(self)
    }
    
    // MARK: - Plugin Methods
    
    func requestPermission(_ call: CAPPluginCall) {
        // iOS handles MFi accessories differently than Android
        // Check if we have any available accessories
        let accessories = EAAccessoryManager.shared().connectedAccessories
        
        if !accessories.isEmpty {
            call.resolve(["granted": true])
        } else {
            // For iOS, we might be using network serial or Lightning accessories
            call.resolve(["granted": true])
        }
    }
    
    func listDevices(_ call: CAPPluginCall) {
        var devices: [[String: Any]] = []
        
        // List MFi accessories
        let accessories = EAAccessoryManager.shared().connectedAccessories
        for accessory in accessories {
            let device: [String: Any] = [
                "deviceId": accessory.connectionID,
                "deviceName": accessory.name,
                "manufacturerName": accessory.manufacturer,
                "serialNumber": accessory.serialNumber,
                "modelNumber": accessory.modelNumber,
                "firmwareRevision": accessory.firmwareRevision,
                "hardwareRevision": accessory.hardwareRevision,
                "protocolStrings": accessory.protocolStrings
            ]
            devices.append(device)
        }
        
        // You could also list known network serial devices here
        // For example, devices advertising via Bonjour
        
        call.resolve(["devices": devices])
    }
    
    func connect(_ call: CAPPluginCall) {
        guard let deviceId = call.getInt("deviceId") else {
            // Try network connection if no deviceId provided
            connectViaNetwork(call)
            return
        }
        
        // Disconnect any existing connection
        disconnect()
        
        let accessories = EAAccessoryManager.shared().connectedAccessories
        
        for accessory in accessories {
            if accessory.connectionID == deviceId {
                self.accessory = accessory
                
                // Find a compatible protocol
                var foundProtocol: String?
                for proto in accessory.protocolStrings {
                    if proto.contains("serial") || proto.contains("com.") {
                        foundProtocol = proto
                        break
                    }
                }
                
                // Use the found protocol or the default one
                let protocolToUse = foundProtocol ?? protocolString
                
                if accessory.protocolStrings.contains(protocolToUse) {
                    session = EASession(accessory: accessory, forProtocol: protocolToUse)
                    
                    if let session = session {
                        setupStreams(session: session)
                        
                        call.resolve([
                            "connected": true,
                            "deviceId": deviceId
                        ])
                        
                        plugin?.notifyListeners("connectionStateChanged", data: [
                            "connected": true,
                            "deviceId": deviceId
                        ])
                        return
                    }
                }
            }
        }
        
        call.reject("Failed to connect to accessory")
    }
    
    private func connectViaNetwork(_ call: CAPPluginCall) {
        // Network serial connection (for Wi-Fi or Ethernet serial adapters)
        let serialOptions = call.getObject("serialOptions") ?? [:]
        let host = serialOptions["host"] as? String ?? "192.168.1.100"
        let port = serialOptions["port"] as? Int ?? 23  // Default telnet port
        
        let endpoint = NWEndpoint.hostPort(
            host: NWEndpoint.Host(host),
            port: NWEndpoint.Port(rawValue: UInt16(port))!
        )
        
        connection = NWConnection(to: endpoint, using: .tcp)
        
        connection?.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                call.resolve([
                    "connected": true,
                    "deviceId": 0,
                    "type": "network"
                ])
                
                self?.plugin?.notifyListeners("connectionStateChanged", data: [
                    "connected": true,
                    "deviceId": 0,
                    "type": "network"
                ])
                
                self?.startReceivingNetworkData()
                
            case .failed(let error):
                call.reject("Network connection failed: \(error.localizedDescription)")
                
                plugin?.notifyListeners("error", data: [
                    "message": "Network connection failed: \(error.localizedDescription)"
                ])
                
            case .cancelled:
                self?.plugin?.notifyListeners("connectionStateChanged", data: [
                    "connected": false
                ])
                
            default:
                break
            }
        }
        
        connection?.start(queue: queue)
    }
    
    func disconnect(_ call: CAPPluginCall? = nil) {
        disconnect()
        call?.resolve(["disconnected": true])
    }
    
    private func disconnect() {
        listening = false
        
        // Disconnect EASession
        if let session = session {
            session.inputStream?.close()
            session.outputStream?.close()
            
            session.inputStream?.remove(from: .current, forMode: .default)
            session.outputStream?.remove(from: .current, forMode: .default)
            
            session.inputStream?.delegate = nil
            session.outputStream?.delegate = nil
        }
        
        session = nil
        accessory = nil
        
        // Disconnect network connection
        connection?.cancel()
        connection = nil
        
        readBuffer.removeAll()
    }
    
    func write(_ call: CAPPluginCall) {
        guard let data = call.getString("data") else {
            call.reject("No data provided")
            return
        }
        
        let bytes = [UInt8](data.utf8)
        var bytesWritten = 0
        
        // Try EASession first
        if let outputStream = session?.outputStream {
            bytesWritten = outputStream.write(bytes, maxLength: bytes.count)
        }
        // Try network connection
        else if let connection = connection {
            connection.send(content: Data(bytes), completion: .contentProcessed { error in
                if let error = error {
                    self?.plugin?.notifyListeners("error", data: [
                        "message": "Write failed: \(error.localizedDescription)"
                    ])
                    call.reject("Write failed: \(error.localizedDescription)")
                } else {
                    call.resolve(["bytesWritten": bytes.count])
                }
            })
            return
        } else {
            call.reject("Not connected")
            return
        }
        
        call.resolve(["bytesWritten": bytesWritten])
    }
    
    func read(_ call: CAPPluginCall) {
        if readBuffer.isEmpty {
            call.resolve([
                "data": "",
                "hexData": ""
            ])
        } else {
            let data = String(data: readBuffer, encoding: .utf8) ?? ""
            let hexData = readBuffer.map { String(format: "%02X", $0) }.joined()
            
            readBuffer.removeAll()
            
            call.resolve([
                "data": data,
                "hexData": hexData
            ])
        }
    }
    
    func startListening(_ call: CAPPluginCall) {
        listening = true
        call.resolve()
    }
    
    func stopListening(_ call: CAPPluginCall) {
        listening = false
        call.resolve()
    }
    
    // MARK: - Stream Setup
    
    private func setupStreams(session: EASession) {
        session.inputStream?.delegate = self
        session.outputStream?.delegate = self
        
        session.inputStream?.schedule(in: .current, forMode: .default)
        session.outputStream?.schedule(in: .current, forMode: .default)
        
        session.inputStream?.open()
        session.outputStream?.open()
    }
    
    // MARK: - Network Data Handling
    
    private func startReceivingNetworkData() {
        connection?.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, isComplete, error in
            if let data = data, !data.isEmpty {
                self?.handleReceivedData(data)
            }
            
            if error == nil && !isComplete {
                self?.startReceivingNetworkData()
            }
        }
    }
    
    private func handleReceivedData(_ data: Data) {
        // Buffer the data
        readBuffer.append(data)
        
        // Notify listeners if listening
        if listening {
            let dataStr = String(data: data, encoding: .utf8) ?? ""
            let hexStr = data.map { String(format: "%02X", $0) }.joined()
            
            plugin?.notifyListeners("dataReceived", data: [
                "data": dataStr,
                "hexData": hexStr,
                "timestamp": Date().timeIntervalSince1970 * 1000,
                "deviceId": accessory?.connectionID ?? 0
            ])
        }
    }
    
    // MARK: - Accessory Notifications
    
    @objc private func accessoryDidConnect(_ notification: Notification) {
        if let accessory = notification.userInfo?[EAAccessoryKey] as? EAAccessory {
            plugin?.notifyListeners("deviceAttached", data: [
                "deviceId": accessory.connectionID,
                "vendorId": 0, // EAAccessory doesn't expose vendor ID
                "productId": 0, // EAAccessory doesn't expose product ID
                "deviceName": accessory.name,
                "manufacturerName": accessory.manufacturer,
                "serialNumber": accessory.serialNumber
            ])
        }
    }
    
    @objc private func accessoryDidDisconnect(_ notification: Notification) {
        if let accessory = notification.userInfo?[EAAccessoryKey] as? EAAccessory {
            plugin?.notifyListeners("deviceDetached", data: [
                "deviceId": accessory.connectionID
            ])
            
            if self.accessory?.connectionID == accessory.connectionID {
                disconnect()
                plugin?.notifyListeners("connectionStateChanged", data: [
                    "connected": false,
                    "deviceId": accessory.connectionID
                ])
            }
        }
    }
}

// MARK: - StreamDelegate

extension UsbSerial: StreamDelegate {
    
    public func stream(_ aStream: Stream, handle eventCode: Stream.Event) {
        switch eventCode {
        case .hasBytesAvailable:
            if let inputStream = aStream as? InputStream {
                readData(from: inputStream)
            }
            
        case .hasSpaceAvailable:
            // Output stream is ready for writing
            break
            
        case .errorOccurred:
            disconnect()
            plugin?.notifyListeners("connectionStateChanged", data: ["connected": false])
            plugin?.notifyListeners("error", data: ["message": "Stream error occurred"])
            
        case .endEncountered:
            disconnect()
            plugin?.notifyListeners("connectionStateChanged", data: ["connected": false])
            
        default:
            break
        }
    }
    
    private func readData(from inputStream: InputStream) {
        let bufferSize = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }
        
        while inputStream.hasBytesAvailable {
            let bytesRead = inputStream.read(buffer, maxLength: bufferSize)
            if bytesRead > 0 {
                let data = Data(bytes: buffer, count: bytesRead)
                handleReceivedData(data)
            }
        }
    }
}