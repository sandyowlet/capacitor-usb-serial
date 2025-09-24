package dev.emmanuelrobinson.capacitorusbserial;

import com.getcapacitor.Logger;

public class UsbSerial {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
}
