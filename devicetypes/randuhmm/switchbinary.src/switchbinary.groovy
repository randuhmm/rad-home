/**
 *  SwitchBinary
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

    definition (name: "SwitchBinary", namespace: "randuhmm",
                author: "Jonny Morrill") {
        capability "Actuator"
        capability "Polling"
        capability "Refresh"
        capability "Sensor"
        capability "Switch"

        attribute "currentIP", "string"

        command "subscribe"
        //command "resubscribe"
        //command "unsubscribe"
        command "setOffline"
    }

    simulator { }

    tiles(scale: 2) {

        multiAttributeTile(name:"rich-control", type: "switch",
                           canChangeIcon: true) {
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off",
                    icon:"st.switches.switch.off", backgroundColor:"#79b821",
                    nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on",
                    icon:"st.switches.switch.on", backgroundColor:"#ffffff",
                    nextState:"turningOn"
                attributeState "turningOn", label:'${name}',
                    action:"switch.off", icon:"st.switches.switch.off",
                    backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "turningOff", label:'${name}',
                    action:"switch.on", icon:"st.switches.switch.on",
                    backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "offline", label:'${name}',
                    icon:"st.switches.switch.off", backgroundColor:"#ff0000"
            }
            tileAttribute ("currentIP", key: "SECONDARY_CONTROL") {
                attributeState "currentIP", label: ''
            }
        }

        standardTile("switch", "device.switch", width: 2, height: 2,
                     canChangeIcon: true) {
            state "on", label:'${name}', action:"switch.off",
                icon:"st.switches.switch.off", backgroundColor:"#79b821",
                nextState:"turningOff"
            state "off", label:'${name}', action:"switch.on",
                icon:"st.switches.switch.on", backgroundColor:"#ffffff",
                nextState:"turningOn"
            state "turningOn", label:'${name}', action:"switch.off",
                icon:"st.switches.switch.off", backgroundColor:"#79b821",
                nextState:"turningOff"
            state "turningOff", label:'${name}', action:"switch.on",
                icon:"st.switches.switch.on", backgroundColor:"#ffffff",
                nextState:"turningOn"
            state "offline", label:'${name}', icon:"st.switches.switch.off",
                backgroundColor:"#ff0000"
        }
        standardTile("refresh", "device.switch", inactiveLabel: false,
                     height: 2, width: 2, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh",
                icon:"st.secondary.refresh"
        }
        main(["switch"])
        details(["rich-control", "refresh"])
    }
}

// parse events into attributes
def parse(String rawEvent) {
    log.debug "${rawEvent}"
    def parsedEvent = parseLanMessage(rawEvent)
    
    def body = parsedEvent.json
    switch(body?.type) {
        case "state":
            if(parsedEvent?.json?.value == 255) {
                sendEvent(name: "switch", value: "on")
            } else {
                sendEvent(name: "switch", value: "off")
            }
            break;
    }
}

// handle hub response
// TODO

// handle commands
def on() {
    log.debug "Executing 'on'"
    new physicalgraph.device.HubAction(
      [
            method: "GET",
            path: "/command",
            headers: [
                HOST: getHostAddress()
            ],
            query: [type: "set", value: "255", name: getDataValue("id")]
        ],
        getDNI(),
        [
            callback: handleOn
        ]
    )
}

def handleOn(physicalgraph.device.HubResponse hubResponse) {
    log.debug "handleOn():"
    sendEvent(name: "switch", value: "on")
}

def off() {
    log.debug "Executing 'off'"
    new physicalgraph.device.HubAction(
      [
            method: "GET",
            path: "/command",
            headers: [
                HOST: getHostAddress()
            ],
            query: [type: "set", value: "0", name: getDataValue("id")]
        ],
        getDNI(),
        [
            callback: handleOff
        ]
    )
}

def handleOff(physicalgraph.device.HubResponse hubResponse) {
    log.debug "handleOff()"
    sendEvent(name: "switch", value: "off")
}

def subscribe() {
    subscribe(getHostAddress())
}

def subscribe(hostAddress) {
    log.debug "Executing 'subscribe()'"
    def address = getCallBackAddress()
    new physicalgraph.device.HubAction(
        method: "GET",
        path: "/subscribe",
        headers: [
            HOST: "${hostAddress}",
            CALLBACK: "<http://${address}/notify>",
            NT: "upnp:event",
            TIMEOUT: "Second-3600"
        ],
        query: [type: "state", name: getDataValue("id")]
    )
}

def refresh() {
      log.debug "Executing 'subscribe', then 'poll'"
      [subscribe(), poll()]
}

def subscribe(ip, port) {
    def existingIp = getDataValue("ip")
    def existingPort = getDataValue("port")
    if (ip && ip != existingIp) {
         log.debug "Updating ip from $existingIp to $ip"    
       updateDataValue("ip", ip)
       def ipvalue = convertHexToIP(getDataValue("ip"))
         sendEvent(name: "currentIP", value: ipvalue,
                   descriptionText: "IP changed to ${ipvalue}")
    }
    if (port && port != existingPort) {
        log.debug "Updating port from $existingPort to $port"
        updateDataValue("port", port)
    }
    subscribe("${ip}:${port}")
}

def sync(ip, port) {
    log.debug "Executing 'sync()'"
    def existingIp = getDataValue("ip")
    def existingPort = getDataValue("port")
    if (ip && ip != existingIp) {
        updateDataValue("ip", ip)
    }
    if (port && port != existingPort) {
        updateDataValue("port", port)
    }
}

def poll() {
    log.debug "Executing 'poll()'"
    if (device.currentValue("currentIP") != "Offline") {
        runIn(30, setOffline)
    }
    new physicalgraph.device.HubAction(
      [
            method: "GET",
            path: "/command",
            headers: [
                HOST: getHostAddress()
            ],
            query: [type: "get", name: getDataValue("id")]
        ],
        getDNI(),
        [
            callback: handlePoll
        ]
    )
}

def handlePoll(physicalgraph.device.HubResponse hubResponse) {
    log.debug "Executing 'handlePoll()'"
    unschedule("setOffline")
    def body = hubResponse.json
    if(body?.value == 255) {
        sendEvent(name: "switch", value: "on")
    } else {
        sendEvent(name: "switch", value: "off")
    }
}

private getDNI() {
    return getDataValue('mac') + "." + getDataValue('id')
}

def setOffline() {
    log.debug "Executing 'setOffline()'"
    sendEvent(name: "switch", value: "offline",
              descriptionText: "The device is offline")
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