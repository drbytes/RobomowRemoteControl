

IMPORTANT: This is unfinished and half baked!

This is just something I threw toghether quickly to connect to a Robomow automatic lawn mower that supports BLE.
The android device you deploy this on needs to be at least Android 6 and have BLE hardware.
You also need to figure out what the serial number of the motherboard installed in the robot is and set it in RobotMessaging.java ( MOTHERBOARD_SERIAL_NR ). See below how to obtain your motherboard serial number.

After you installed the app you must allow the permissions and restart the app, 
after that the app will open a telnet socket where you can issue commands to the bot.

You can issue commands to steer the robot, either with it's blades engaged or not.
First issue a CONNECT command and see whether the AUTH passes. After that you can issue other commands, ie. FORWARD_10 will move the bot forward for 10 ticks.
See ControlServer.java for more commands

The telnet session will also report the GPS coordinates grabbed from the phone, to log where the bot has been.

I still need to finish this, it's more of a proof of concept and I'm actually spending most of my free time on NODEJS/RASPBERRY implementation.
See NodeJSBot.js for the barebones NodeJS implementation, you need to install the NOBLE NPM package.

======================================================

UPDATE

How to get the main_board id.

Navigate to http://appprod.robomow.com/CoupleAction.asmx?op=Couple

You need to enter three things; your email, serial number of the bot and your password and hit "invoke".

For me, that returns :

`<string>{"RossResponseCode":0,"Mainboard":"6411800003071","UserLevel":"Primary","version":"0.5","resultCode":1,"resultMessage":""}</string>`

Mainboard includes the right serialnumber, replace it in the code. Or fork my code and include a dialog that automates this :)

