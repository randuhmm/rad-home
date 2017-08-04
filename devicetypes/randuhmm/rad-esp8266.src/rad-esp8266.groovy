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

    definition (name: 'RAD-ESP8266', namespace: 'randuhmm',
                author: 'Jonny Morrill') {
        capability 'Polling'
        capability 'Refresh'
    }

    simulator {}

    tiles {
        standardTile('refresh', 'device.refresh', inactiveLabel: false,
                     decoration: 'flat') {
            state 'default', action:'refresh.refresh',
                icon: 'st.secondary.refresh'
        }
        main (['refresh'])
        details(['refresh'])
    }
}

// parse events into attributes
def parse(String rawEvent) {
    log.debug "${rawEvent}"
    def parsedEvent = parseLanMessage(rawEvent)
    if(parsedEvent.headers.containsKey('RAD-ID')) {
        def name = parsedEvent.headers['RAD-ID']
        parent.dispatchEvent(name, parsedEvent.mac, rawEvent)
    }
    []
}

def refresh() {
    log.debug 'Executing "refresh()"'
}
