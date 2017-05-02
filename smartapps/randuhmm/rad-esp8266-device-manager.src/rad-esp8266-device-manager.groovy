/**
 *  Generic UPnP Service Manager
 *
 *  Copyright 2016 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "RAD-ESP8266 Device Manager",
    namespace: "randuhmm",
    author: "Jonny Morrill",
    description: "A device manager application for RAD-ESP8266 devices.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    singleInstance: true
)

preferences {
    page(name: "deviceDiscovery", title: "RAD-ESP8266 Discovery", content: "deviceDiscovery")
}

def deviceDiscovery() {
    def options = [:]
    def devices = getVerifiedDevices()
    devices.each {
        def value = it.value.name ?: "UPnP Device ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
        def key = it.value.mac
        options["${key}"] = value
    }

    ssdpSubscribe()
    ssdpDiscover()
    verifyDevices()

    return dynamicPage(name: "deviceDiscovery", title: "Discovery Started!", nextPage: "", refreshInterval: 5, install: true, uninstall: true) {
        section("Please wait while we discover your RAD-ESP8266 devices. Discovery can take several minutes, so sit back and relax! Select your device(s) below once they have been discovered.") {
            input "selectedDevices", "enum", required: false, title: "Select Devices (${options.size() ?: 0} found)", multiple: true, options: options
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    unsubscribe()
    unschedule()

    ssdpSubscribe()

    if (selectedDevices) {
        addDevices()
    }

    runEvery5Minutes("ssdpDiscover")
}

void ssdpDiscover() {
    sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:rad:device:esp:1", physicalgraph.device.Protocol.LAN))
}

void ssdpSubscribe() {
    subscribe(location, "ssdpTerm.urn:rad:device:esp8266:1", ssdpHandler)
}

Map verifiedDevices() {
    def devices = getVerifiedDevices()
    def map = [:]
    devices.each {
        def value = it.value.name ?: "UPnP Device ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
        def key = it.value.mac
        map["${key}"] = value
    }
    map
}

void verifyDevices() {
    log.debug "verifyDevices()"
    def devices = getDevices().findAll { it?.value?.verified != true }
    devices.each {
        int port = convertHexToInt(it.value.deviceAddress)
        String ip = convertHexToIP(it.value.networkAddress)
        String host = "${ip}:${port}"
        log.debug "verifyDevices(): $host"
        sendHubCommand(new physicalgraph.device.HubAction("""GET ${it.value.ssdpPath} HTTP/1.1\r\nHOST: $host\r\n\r\n""", physicalgraph.device.Protocol.LAN, host, [callback: deviceDescriptionHandler]))
    }
}

def getVerifiedDevices() {
    getDevices().findAll{ it.value.verified == true }
}

def getDevices() {
    if (!state.devices) {
        state.devices = [:]
    }
    state.devices
}

def addDevices() {
    log.debug "addDevices()"
    def devices = getDevices()

    selectedDevices.each { dni ->
        def selectedDevice = devices.find { it.value.mac == dni }
        def d
        if (selectedDevice) {
            d = getChildDevices()?.find {
                it.deviceNetworkId == selectedDevice.value.mac
            }
        }

        if (!d) {
            log.debug "Creating Generic UPnP Device with dni: ${selectedDevice.value.mac}"
            addChildDevice("randuhmm", "RAD-ESP8266", selectedDevice.value.mac, selectedDevice?.value.hub, [
                "label": selectedDevice?.value?.name ?: "RAD-ESP8266",
                "data": [
                    "mac": selectedDevice.value.mac,
                    "ip": selectedDevice.value.networkAddress,
                    "port": selectedDevice.value.deviceAddress
                ]
            ])
            
            selectedDevice?.value?.things.each { thing ->
            	def thing_dni = "${selectedDevice.value.mac}-${thing.name}"
                addChildDevice("randuhmm", thing.type, thing_dni, selectedDevice?.value.hub, [
                    "label": thing?.name ?: thing.type ,
                    "data": [
                    	"id": thing.name,
                        "mac": selectedDevice.value.mac,
                        "ip": selectedDevice.value.networkAddress,
                        "port": selectedDevice.value.deviceAddress
                    ]
                ])
            }
        }
    }
}

def ssdpHandler(evt) {
    def description = evt.description
    log.debug "ssdpHandler(): $description"
    def hub = evt?.hubId

    def parsedEvent = parseLanMessage(description)
    parsedEvent << ["hub":hub]

    def devices = getDevices()
    String ssdpUSN = parsedEvent.ssdpUSN.toString()
    log.debug "ssdpUSN = ${ssdpUSN}"
    if (devices."${ssdpUSN}") {
        def d = devices."${ssdpUSN}"
        if (d.networkAddress != parsedEvent.networkAddress || d.deviceAddress != parsedEvent.deviceAddress) {
            d.networkAddress = parsedEvent.networkAddress
            d.deviceAddress = parsedEvent.deviceAddress
            def child = getChildDevice(parsedEvent.mac)
            if (child) {
                child.sync(parsedEvent.networkAddress, parsedEvent.deviceAddress)
            }
        }
    } else {
        devices << ["${ssdpUSN}": parsedEvent]
    }
}

void deviceDescriptionHandler(physicalgraph.device.HubResponse hubResponse) {
    log.debug "deviceDescriptionHandler()"
    def body = hubResponse.json
    def devices = getDevices()
    def device = devices.find { it?.key?.contains(body.UDN) }
    if (device) {
        device.value << [
            name: body.deviceName,
            model: body.modelName,
            serialNumber: body.serialNum,
            verified: true,
            things: body.things
        ]
    }
}

private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

def dispatchEvent(name, mac, rawEvent) {
	String dni = "${mac}-${name}"
    def device = getChildDevice(dni)
    if(device) {
    	device.parse(rawEvent)
    }
}
