# Hubitat Shabbat and Jewish holiday info driver

## Purpose

The point of this driver is to enable a Hubitat system to be able to better handle the timing of Shabbat and Jewish holidays. A virtual device running this driver will provide information which can be used as the basis of automations, including (but not limited to) Rule Machine triggers and conditions.

The two main benefits of this driver over built-in Hubitat behavior (date/time and sunset triggers) are:

1. Certain holidays are treated the same as Shabbat. (It's the holidays you'd expect if you're interested in this driver: Rosh Hashanah, Yom Kippur, start of Sukkot, Shemini Atzeret/Simchat Torah, start and end of Pesach, and Shavuot.) The driver can be configured to "observe" 1 or 2 days of the relevant holidays depending on your practice.
2. Havdalah/nightfall time is dynamic (calculated as the sun being 8.5 degrees below the horizon) rather than a fixed amount of time after sunset.

## Installation and setup

1. Make sure that in Hub Details, your time zone, latitude, and longitude are set correctly.
2. Install the device driver code following [these instructions](https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Drivers). You can find the [driver code here](https://raw.githubusercontent.com/rosenbergj/hubitat_shabbat_and_chag/main/shabbat.groovy). (Hopefully this will be in the Hubitat Package Manager soon...)
3. Create a new virtual device, and assign the Type as "Shabbat and Jewish holiday info switch". Give it any name, and save it.
4. Edit the preferences of that device to select the number of days of chag to observe, and save device. **This step is required**; without it, the switch will fail to retrieve any info.
5. (Optional) In your device Settings, go into the Hub Variables section. Create one or two new DateTime variables, which will indicate the start and end time of Shabbat*. Return to the preferences of your virtual device, and enter the variable names in the designated places. Save the device.

\* From now on we'll refer to "Shabbat" for simplicity, but what we mean is "a period of 1-3 days of Shabbat, chag, or both, in some combination".

That's it!

## Device behavior

* Within an hour or two after midnight every morning, the device will refresh by calling an external API to learn about whether Shabbat starts or ends later that day. Sometimes the device will want to refresh again later in the day to get more current info. You can also trigger another refresh at any time by "pushing" the button.
* Every time the device is refreshed, 3 child devices are updated:
    - A switch indicates whether it is Shabbat that **day** (i.e. before sundown), regardless of the current time. On = yes; off = no.
    - A switch indicates whether it is Shabbat that **night** (i.e. after nightfall), regardless of the current time. On = yes; off = no.
    - A switch indicates whether it is Shabbat right now. On = yes; off = no. **NOTE**: While every effort is made to be as accurate as possible, this should not be relied on as being accurate to the second.
* Every time the device is refreshed, various attributes are updated indicating the sunrise/sunset/nightfall times that day, as well as whether it is Shabbat that day, that night, and/or right now.
* If Shabbat is starting or ending later today, and if you have configured Hub Variables, those variables will be updated with the start/end time. (If Shabbat is not starting or ending later today, these times will be in the past and don't necessarily mean anything.) **NOTE**: While every effort is made to be as accurate as possible, this should not be relied on as being accurate to the second.
* At the moment Shabbat starts or ends (or as close as possible), the device attribute and child device indicating whether it's currently Shabbat are updated. A full refresh doesn't happen immediately, but it's scheduled for a few minutes later.

## Some suggested automations

* Use the "Shabbat Now" child switch as a trigger for Rule Machine or Simple Automation rules. (See caveat above regarding precision.)
* Make a Rule Machine rule that triggers at a Certain Time. Choose "Variable time", and pick the Hub Variable that you configured the device to use. Set the offset of your choice so that things trigger a certain number of minutes before or after the time stored in the variable.
* Make Rule Machine rules that trigger at a certain time every day, conditional (with a Required Condition) on the Shabbat Tonight switch being on.
* Make Rule Machine rules that trigger at a certain time every day, or a fixed amount of time before/after sunset, conditional (with a Required Condition) on the Shabbat Today switch being off *and* the Shabbat Tonight switch being on. These will only run on the day when Shabbat or a holiday starts, but not if a multi-day holiday is continuing.
* Any of these rules could change your Mode. Or, skip Rule Manager and set modes directly in Mode Manager based on the Shabbat Now switch being switched. (See caveat above regarding precision.)
* Add your own!

## Caveats

If you live somewhere where it doesn't get dark enough to see 3 stars, I'm not really sure what will happen. Probably the device will simply fail to work, repeatedly, until it once again gets dark enough where you are. I'm happy to work to fix this, but first I need you to tell me what common havdalah practices are in your community...
