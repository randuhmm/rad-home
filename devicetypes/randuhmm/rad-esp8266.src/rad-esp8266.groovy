/**
 *  RAD-ESP8266
 *
 *  Copyright 2016 Jonny Morrill
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy
 *  of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 *
 */
metadata {

    definition (name: "RAD-ESP8266", namespace: "randuhmm",
                author: "Jonny Morrill") {
        capability "Polling"
        capability "Refresh"
    }

    simulator {}

    tiles {
        standardTile("refresh", "device.refresh", inactiveLabel: false,
                     decoration: "flat") {
            state "default", action:"refresh.refresh",
                icon: "st.secondary.refresh"
        }
        main (["refresh"])
        details(["refresh"])
    }
}

// parse events into attributes
def parse(String rawEvent) {
    log.debug "${rawEvent}"
    def parsedEvent = parseLanMessage(rawEvent)
    if(parsedEvent.headers.containsKey('RAD-NAME')) {
        def name = parsedEvent.headers['RAD-NAME']
        parent.dispatchEvent(name, parsedEvent.mac, rawEvent)
    }
    []
}

def refresh() {
    log.debug "Executing 'refresh()'"
}


// =============================================================================
// Helper Methods Below This Line
// =============================================================================

// gets the address of the hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" +
        device.hub.getDataValue("localSrvPortTCP")
}

// gets the address of the device
private getHostAddress() {
    def ip = getDataValue("ip")
    def port = getDataValue("port")

    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 2) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }

    log.debug "Using IP: $ip and port: $port for device: ${device.id}"
    return convertHexToIP(ip) + ":" + convertHexToInt(port)
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [
        convertHexToInt(hex[0..1]),
        convertHexToInt(hex[2..3]),
        convertHexToInt(hex[4..5]),
        convertHexToInt(hex[6..7])
    ].join(".")
}