/*
 * Shabbat and Jewish holiday info driver
 *
 * Calls a web API to learn which days are shabbat/holidays, and what time havdalah is
 *
 */

metadata {
    definition(name: "Shabbat and Jewish Holiday Info", namespace: "ShabbatHolidayInfo", author: "Josh Rosenberg", importUrl: "https://raw.githubusercontent.com/rosenbergj/hubitat-shabbat-and-chag/main/shabbat.groovy") {
        capability "Sensor"
        capability "Actuator"
        capability "Momentary"
        capability "Refresh"

        attribute "retrievedAt", "string"
        attribute "sunrise", "string"
        attribute "sunset", "string"
        attribute "nightfall", "string"
        attribute "shabbatOrChagToday", "boolean"
        attribute "shabbatOrChagTonight", "boolean"
        attribute "shabbatOrChagNow", "boolean"
        attribute "secsUntilNextChange", "number"
    }
}

preferences {
    section("URIs") {
        input name: "daysOfChag", type: "enum", options: ["1","2"], title: "Days of chag", description: "Number of days to observe chag (does not apply to Rosh Hashanah or Yom Kippur)", required: true
        input name: "hubVarStartTime", type: "string", title: "Hub variable for Shabbat/chag start", description: "Name of an already-created Hub variable of type DateTime. If Shabbat or a holiday starts today, that start time will be assigned to the specified variable.", required: false
        input name: "hubVarEndTime", type: "string", title: "Hub variable for Shabbat/chag end", description: "Name of an already-created Hub variable of type DateTime. If Shabbat or a holiday ends today, that end time (as 8.5 degrees) will be assigned to the specified variable.", required: false
        input name: "debugOffset", type: "Number", title: "Debug Offset", description: "Number of minutes in the future (or past if negative) to pretend it is right now. May be weird; for debug purposes only.", required: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

// standard callback methods

def installed() {
    addChildDevice("hubitat", "Generic Component Switch", "${device.deviceNetworkId}-today",
        [label: "${device.displayName} (Shabbat Today)", isComponent: true])
    addChildDevice("hubitat", "Generic Component Switch", "${device.deviceNetworkId}-tonight",
        [label: "${device.displayName} (Shabbat Tonight)", isComponent: true])
    addChildDevice("hubitat", "Generic Component Switch", "${device.deviceNetworkId}-now",
        [label: "${device.displayName} (Shabbat Now)", isComponent: true])
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    if (logEnable) runIn(1800, logsOff)
    runEvery1Hour(callApiIfFirstTimeToday)
    push()
}

def parse(String description) {
    logDebug(description)
}

// capability commands

def push() {
    callApi()
}

def refresh() {
    callApi()
}

// methods to tell our child devices to do things

def childSwitchOn(String whichSwitch) {
    def cd = getChildDevice(device.deviceNetworkId + "-" + whichSwitch)
    cd.parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
}

def childSwitchOff(String whichSwitch) {
    def cd = getChildDevice(device.deviceNetworkId + "-" + whichSwitch)
    cd.parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
}

def startShabbatRightNow() {
    logDebug("Updating the shabbat-now attribute to true.")
    sendEvent(name: "shabbatOrChagNow", value: true)
    childSwitchOn("now")
}
def endShabbatRightNow() {
    logDebug("Updating the shabbat-now attribute to false.")
    sendEvent(name: "shabbatOrChagNow", value: false)
    childSwitchOff("now")
}

// methods to capture someone else triggering child devices

void componentRefresh(cd){
    log.info "received refresh request from ${cd.displayName}"
}

void componentOn(cd){
    log.info "received on request from ${cd.displayName}"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
}

void componentOff(cd){
    log.info "received off request from ${cd.displayName}"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
}

// custom methods

def callApi() {
    def lat = location.latitude.setScale(2, BigDecimal.ROUND_HALF_UP) // round for privacy
    def lon = location.longitude.setScale(2, BigDecimal.ROUND_HALF_UP)
    String offsetString = ""
    if (settings.debugOffset) {
        offsetString = "&offset=" + settings.debugOffset
    }
    def url = "https://api.zmanapi.com/?lat=${lat}&lon=${lon}&chagdays=${settings.daysOfChag}${offsetString}"
    def results = ""
    logDebug("Requesting data from ${url}")
    try {
        httpGet(url) { resp ->
            if (resp.success) {
                gotData = true
                logDebug("retrieved data from URL: " + resp.getData())
                results = resp.getData().results
            }
        }
    } catch (Exception e) {
        log.warn "Call to URL failed: ${e.message}"
    }
    if (results) {
        logDebug("Assigning results as attributes.")
        Boolean isShabbatToday = results.shabbat_or_yom_tov_today.toBoolean()
        Boolean isShabbatTonight = results.shabbat_or_yom_tov_tonight.toBoolean()
        Boolean isShabbatNow = results.shabbat_or_yom_tov_now.toBoolean()
        sendEvent(name: "retrievedAt", value: dateStringConvert(results.now))
        sendEvent(name: "sunrise", value: dateStringConvert(results.sunrise))
        sendEvent(name: "sunset", value: dateStringConvert(results.sunset))
        sendEvent(name: "nightfall", value: dateStringConvert(results.jewish_twilight_end))
        sendEvent(name: "shabbatOrChagToday", value: isShabbatToday)
        sendEvent(name: "shabbatOrChagTonight", value: isShabbatTonight)
        sendEvent(name: "shabbatOrChagNow", value: isShabbatNow)
        unschedule(startShabbatRightNow)
        unschedule(endShabbatRightNow)
	
	if (results.hanukkah_now) {
	    sendEvent(name: "hanukkahNow", value: results.hanukkah_now)
	} else {
	    sendEvent(name: "hanukkahNow", value: 0)
	}
	if (results.hanukkah_today) {
	    sendEvent(name: "hanukkahToday", value: results.hanukkah_today)
	} else {
	    sendEvent(name: "hanukkahToday", value: 0)
	}
	if (results.hanukkah_tonight) {
	    sendEvent(name: "hanukkahTonight", value: results.hanukkah_tonight)
	} else {
	    sendEvent(name: "hanukkahTonight", value: 0)
	}


        if (isShabbatToday) {
            logDebug("Turning on child 'today' switch.")
            childSwitchOn("today")
        } else {
            logDebug("Turning off child 'today' switch.")
            childSwitchOff("today")
        }
        if (isShabbatTonight) {
            logDebug("Turning on child 'tonight' switch.")
            childSwitchOn("tonight")
        } else {
            logDebug("Turning off child 'tonight' switch.")
            childSwitchOff("tonight")
        }
        if (isShabbatNow) {
            logDebug("Turning on child 'now' switch.")
            childSwitchOn("now")
        } else {
            logDebug("Turning off child 'now' switch.")
            childSwitchOff("now")
        }

        if (!isShabbatToday && isShabbatTonight) {
            int secsUntilNextChange = (stod(results.sunset).getTime() - stod(results.now).getTime())/1000
            String sunsetTime = dateStringConvert(results.sunset)
            sendEvent(name: "secsUntilNextChange", value: secsUntilNextChange)
            logDebug("Shabbat/chag is starting (or did start) in ${secsUntilNextChange} seconds.")
            if (settings.hubVarStartTime) {
                logDebug("Assigning Shabbat/chag start time to hub variable.")
                try {
                    success = setGlobalVar(settings.hubVarStartTime, sunsetTime)
                    if (!success) {
                        log.warn "Failed to assign time ${sunsetTime} to Hub variable ${settings.hubVarStartTime}"
                    }
                } catch (Exception e) {
                    log.warn "Failed to assign time ${sunsetTime} to Hub variable ${settings.hubVarStartTime}"
                }
            }
            if (secsUntilNextChange > 0) {
                if (secsUntilNextChange > 10) {
                    logDebug("Scheduling turning that switch on then.")
                    runOnce(sunsetTime, startShabbatRightNow)
                } else {
                    pauseExecution(secsUntilNextChange*1000)
                    startShabbatRightNow()
                }
                logDebug("And scheduling an another update for a couple minutes after that.")
                unschedule(push)
                runIn(secsUntilNextChange + 120, push)
            }
        }
        if (isShabbatToday && !isShabbatTonight) {
            int secsUntilNextChange = (stod(results.jewish_twilight_end).getTime() - stod(results.now).getTime())/1000
            String nightfallTime = dateStringConvert(results.jewish_twilight_end)
            sendEvent(name: "secsUntilNextChange", value: secsUntilNextChange)
            logDebug("Shabbat/chag is ending (or did end) in ${secsUntilNextChange} seconds.")
            if (settings.hubVarEndTime) {
                logDebug("Assigning Shabbat/chag end time to hub variable.")
                try {
                    success = setGlobalVar(settings.hubVarEndTime, nightfallTime)
                    if (!success) {
                        log.warn "Failed to assign time ${nightfallTime} to Hub variable ${settings.hubVarEndTime}"
                    }
                } catch (Exception e) {
                    log.warn "Failed to assign time ${nightfallTime} to Hub variable ${settings.hubVarEndTime}"
                }
            }
            if (secsUntilNextChange > 0) {
                if (secsUntilNextChange > 10) {
                    logDebug("Scheduling turning that switch off then.")
                    runOnce(nightfallTime, endShabbatRightNow)
                } else {
                    pauseExecution(secsUntilNextChange*1000)
                    endShabbatRightNow()
                }
                logDebug("And scheduling an another update for a couple minutes after that.")
                unschedule(push)
                runIn(secsUntilNextChange + 120, push)
            }
        }
    }
}

def callApiIfFirstTimeToday() {
    logDebug("Beginning hourly scheduled task.")
    Date dateTimeToday = timeToday("12:00", location.timeZone)
    if (!device.currentValue("retrievedAt")) {
        logDebug("Calling API to get data for the first time ever (${dateTimeToday.format("yyyy-MM-dd")})")
        return callApi()
    }
    Date dateTimeUpdated = Date.parse("yyyy-MM-dd'T'HH:mm:ss.sssXX", device.currentValue("retrievedAt"))
    if (dateTimeToday.format("yyyy-MM-dd") == dateTimeUpdated.format("yyyy-MM-dd")) {
        logDebug("Not calling API because we already have data from today (${dateTimeToday.format("yyyy-MM-dd")})")
    } else {
        logDebug("Calling API to get data for the first time today (${dateTimeToday.format("yyyy-MM-dd")})")
        callApi()
    }
}

Date stod(String datestring) {
    return Date.parse("yyyy-MM-dd'T'HH:mm:ssX", datestring)
}

String dtos(Date dt) {
    return dt.format("yyyy-MM-dd'T'HH:mm:ss.sssXX")
}

String dateStringConvert(String datestring) {
    Date dt = Date.parse("yyyy-MM-dd'T'HH:mm:ssX", datestring)
    return dt.format("yyyy-MM-dd'T'HH:mm:ss.sssXX")
}

// logging methods

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

private logDebug(msg) {
    if (logEnable) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
