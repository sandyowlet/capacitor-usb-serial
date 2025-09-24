import { UsbSerial } from 'capacitor-usb-serial';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    UsbSerial.echo({ value: inputValue })
}
