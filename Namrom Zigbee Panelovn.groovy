metadata {
   definition (name: "Namron Zigbee Panelovn", namespace: "Tjomp", author: "Tjomp") {
      capability "TemperatureMeasurement"
      capability "ThermostatHeatingSetpoint"
      capability "ThermostatOperatingState"
      capability "PowerMeter"
      capability "Refresh"
      capability "Configuration"

   }

   preferences {
        input name: "tempCalibration", type: "number", title: "Temperature Calibration", description: "Number between -3 and 3 degrees to calibrate temperature sensor", range: "-3..3", defaultValue: 0
        input name: "pollRate", type: "number", title: "Poll Rate", description: "Number of seconds between 15 and 300. Sets how often sensors readings are updated", range: "15..300", defaultValue: 30
   }
}

def installed() {
   log.debug "installed()"
}

def configure() {
    def cmds = ["zdo bind 0x${device.deviceNetworkId} 1 0x019 0x201 {${device.zigbeeId}} {}", "delay 200",]
    
    cmds += zigbee.configureReporting(0x201, 0x0000, 0x29, 10, pollRate)
    cmds += zigbee.configureReporting(0xB04, 0x050B, 0x29, 10, pollRate)
    return cmds + refresh()
}

def updated() {
    def cmds = ["zdo bind 0x${device.deviceNetworkId} 1 0x019 0x201 {${device.zigbeeId}} {}", "delay 200",]

    if (tempCalibration != null) {
        tempCalibration = tempCalibration * 10
        cmds += zigbee.writeAttribute(0x201, 0x0010, 0x28, (byte) tempCalibration)
    }
    
    return cmds + refresh()
}

def parse(String description) {
    def descMap = zigbee.parseDescriptionAsMap(description)
    def map = [:]

    if (description?.startsWith("read attr -")) {
        if (descMap.cluster == "0201" && descMap.attrId == "0000")
        {
            map.name = "temperature"
            map.value = getTemperature(descMap.value)
            sendEvent(name:"temperature", value:map.value)
        }
        else if (descMap.cluster == "0201" && descMap.attrId == "0010") {
            map.name = "temperaturCalibration"
            map.value = Integer.parseInt(descMap.value, 16)/10
            sendEvent(name:"temperaturCalibration", value:map.value)
        }
        else if (descMap.cluster == "0201" && descMap.attrId == "0012") {
            map.name = "heatingSetpoint"
            map.value = getTemperature(descMap.value)
            sendEvent(name:"heatingSetpoint", value:map.value)
        }
        else if (descMap.cluster == "0B04" && descMap.attrId == "050B") {
            def power = Math.round(Integer.parseInt(descMap.value, 16)/10)
            map.value = power
            sendEvent(name:"power", value:map.value)

            map.name = "thermostatOperatingState"
            if (power < 10) {
                map.value = "idle"
            }
            else {
                map.value = "heating"
            }
             sendEvent(name:"thermostatOperatingState", value:map.value)
        }
    }

    def result = null
    if (map) {
        result = createEvent(map)
    }
    return result
}

def refresh() {
    def cmds = []
    
    cmds += zigbee.readAttribute(0x201, 0x0000) //Read LocalTemperature Attribute
    cmds += zigbee.readAttribute(0x201, 0x0010) //Read LocalTemperatureCalibration
    cmds += zigbee.readAttribute(0x201, 0x0012) //Read OccupiedHeatingSetpoint
    
    cmds += zigbee.readAttribute(0x0b04, 0x050b) // Read ActivePower 
    return cmds
}   

def setHeatingSetpoint(temperature) {
    if (temperature != null) {
        def scale = getTemperatureScale()
        def degrees = new BigDecimal(temperature).setScale(1, BigDecimal.ROUND_HALF_UP)
        def celsius = (scale == "C") ? degrees as Float : (fahrenheitToCelsius(degrees) as Float).round(2)
        int celsius100 = Math.round(celsius * 100)
        
        zigbee.writeAttribute(0x201, 0x0012, 0x29, celsius100)
    }
}

def getTemperature(value) {
    if (value != null) {
        def celsius = Integer.parseInt(value, 16) / 100
        if (getTemperatureScale() == "C") {
            return celsius
        }
        else {
            return Math.round(celsiusToFahrenheit(celsius))
        }
    }
}

