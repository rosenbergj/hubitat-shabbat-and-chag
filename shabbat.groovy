/*
 * Shabbat and Jewish holiday info driver
 *
 * Calls a  web API to learn which days are shabbat/holidays, and what time havdalah is
 *
 */
metadata {
    definition(name: "Shabbat and Jewish holiday info switch", namespace: "ShabbatHolidayInfo", author: "Josh Rosenberg", importUrl: "https://raw.githubusercontent.com/rosenbergj/hubitat_shabbat_and_chag/main/shabbat.groovy") {
        capability "Sensor"
        capability "Actuator"
        capability "Momentary"

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
        input name: "daysOfChag", type: "number", range: "1..2", title: "Days of chag", description: "Number of days you observe chag (does not apply to Rosh Hashanah or Yom Kippur)", required: true, defaultValue: 2
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
    logDebug("Requesting data from ${url}")
    try {
        httpGet(url) { resp ->
            if (resp.success) {
                logDebug(resp.getData())
                def results = resp.getData().results
                sendEvent(name: "sunrise", value: results.sunrise)
                sendEvent(name: "sunset", value: results.sunset)
                sendEvent(name: "nightfall", value: results.jewish_twilight_end)
                sendEvent(name: "shabbatOrChagToday", value: results.shabbat_or_yom_tov_today.toBoolean())
                sendEvent(name: "shabbatOrChagTonight", value: results.shabbat_or_yom_tov_tonight.toBoolean())
                sendEvent(name: "shabbatOrChagNow", value: results.shabbat_or_yom_tov_now.toBoolean())
            }
        }
    } catch (Exception e) {
        log.warn "Call to on failed: ${e.message}"
    }
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