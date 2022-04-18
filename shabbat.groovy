/*
 * Shabbat and Jewish holiday info driver
 *
 * Calls a web API to learn which days are shabbat/holidays, and what time havdalah is
 *
 */

metadata {
    definition(name: "Shabbat and Jewish holiday info switch", namespace: "ShabbatHolidayInfo", author: "Josh Rosenberg", importUrl: "https://raw.githubusercontent.com/rosenbergj/hubitat_shabbat_and_chag/main/shabbat.groovy") {
        capability "Sensor"
        capability "Actuator"
        capability "Momentary"

        attribute "retrievedAt", "string"
        attribute "sunrise", "string"
        attribute "sunset", "string"
        attribute "nightfall", "string"
        attribute "shabbatOrChagToday", "boolean"
        attribute "shabbatOrChagTonight", "boolean"
        attribute "shabbatOrChagNow", "boolean"
    }
}

preferences {
    section("URIs") {
        input name: "daysOfChag", type: "enum", options: ["1","2"], title: "Days of chag", description: "Number of days you observe chag (does not apply to Rosh Hashanah or Yom Kippur)", required: true
        input name: "hubVarStartTime", type: "string", title: "Hub variable for Shabbat/chag start", description: "Name of an already-created Hub variable in DateTime format. If Shabbat or a holiday starts today, that start time will be assigned to the specified variable.", required: false
        input name: "hubVarEndTime", type: "string", title: "Hub variable for Shabbat/chag end", description: "Name of an already-created Hub variable in DateTime format. If Shabbat or a holiday ends today, that end time (as 8.5 degrees) will be assigned to the specified variable.", required: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

// standard callback methods

// def installed() {
    // create child devices
// }

// def uninstalled () {}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    if (logEnable) runIn(1800, logsOff)
    push()
}

def parse(String description) {
    logDebug(description)
}

// capability commands

def push() {
    runCmd(devicePath, deviceMethod)
}

// custom methods

def runCmd(String varCommand, String method) {
    def lat = location.latitude.setScale(2, BigDecimal.ROUND_HALF_UP) // round for privacy
    def lon = location.longitude.setScale(2, BigDecimal.ROUND_HALF_UP)
    def url = "https://api.zmanapi.com/?lat=${lat}&lon=${lon}&chagdays=${settings.daysOfChag}"
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
        if (!isShabbatToday && isShabbatTonight) {
            if (settings.hubVarStartTime) {
                logDebug("Assigning Shabbat/chag start time to hub variable.")
                String sunsetTime = dateStringConvert(results.sunset)
                try {
                    success = setGlobalVar(settings.hubVarStartTime, sunsetTime)
                    if (!success) {
                        log.warn "Failed to assign time ${sunsetTime} to Hub variable ${settings.hubVarStartTime}"
                    }
                } catch (Exception e) {
                    log.warn "Failed to assign time ${sunsetTime} to Hub variable ${settings.hubVarStartTime}"
                }
            }
        }
        if (isShabbatToday && !isShabbatTonight) {
            if (settings.hubVarEndTime) {
                logDebug("Assigning Shabbat/chag end time to hub variable.")
                String nightfallTime = dateStringConvert(results.jewish_twilight_end)
                try {
                    success = setGlobalVar(settings.hubVarEndTime, nightfallTime)
                    if (!success) {
                        log.warn "Failed to assign time ${nightfallTime} to Hub variable ${settings.hubVarEndTime}"
                    }
                } catch (Exception e) {
                    log.warn "Failed to assign time ${nightfallTime} to Hub variable ${settings.hubVarEndTime}"
                }
            }
        }
    }
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