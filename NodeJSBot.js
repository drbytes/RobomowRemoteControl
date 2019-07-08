var async = require('async');
var noble = require('../index');
// Service ID's
const serviceUUIDs = "ff00a501d020913c123456d97200a6a6";
const authUUIDS = "ff00a502d020913c123456d97200a6a6";
const dataUUIDS = "ff00a503d020913c123456d97200a6a6";
var Mower;
var charData = null;
var FORWARD = -80, BACKWARD = 90, LEFT = -120, RIGHT = 35;
var safetyCounter = 0;
var mow = false;
var stop = false;
var showDebug = false;
var sendNop = true;
var nopData = new Buffer([-86, 5, 31, 27, 22]);
var NOPPER_TIMEOUT = 2000; // Send NOP every 2 sec
var MESSAGE_TIMEOUT = 500 // Send RC packet every half second

noble.on('stateChange', function (state) {
    if (state === 'poweredOn') {
        noble.startScanning();
    } else {
        noble.stopScanning();
    }
});

const readline = require('readline').createInterface({
    input: process.stdin,
    output: process.stdout
});

function getSpeed(cmd) {
    var parts = cmd.split(" ");
    if(parts.length > 1){
        return parts[1];
    } else return 5;
}

function WaitForInput() {
    readline.question(`Command: `, (cmd) => {
        cmd = cmd.toLowerCase();
        if (cmd.startsWith("forward")) {
            ControlRobot(FORWARD, 100, mow, getSpeed(cmd));
            console.log("Direction FORWARD, SPEED " + getSpeed(cmd));

        } else if (cmd.startsWith("backward")) {
            ControlRobot(BACKWARD, 100, mow, getSpeed(cmd));
            console.log("Direction BACKWARD, SPEED " + getSpeed(cmd));

        } else if (cmd.startsWith("left")) {
            ControlRobot(LEFT, 100, mow, getSpeed(cmd));
            console.log("Direction LEFT, SPEED " + getSpeed(cmd));

        } else if (cmd.startsWith("right")) {
            ControlRobot(RIGHT, 100, mow, getSpeed(cmd));
            console.log("Direction RIGHT, SPEED " + getSpeed(cmd));

        } else if (cmd.startsWith("stop")) {
            console.log("STOP");
            stop = true;

        } else if (cmd.startsWith("connect")) {
            console.log("CONNECTING");
            ConnectAndSetUp();

        } else if (cmd.startsWith("mow")) {
            if (mow)
                mow = false;
            else mow = true;
            console.log("MOW: " + mow.toString());

        } else if (cmd.startsWith("debug")) {
            if (showDebug)
                showDebug = false;
            else showDebug = true;
            console.log("DEBUG OUTPUT: " + showDebug.toString());

        } else if (cmd.startsWith("timeout")) {
            console.log("Timout set to " + getSpeed(cmd));
            MESSAGE_TIMEOUT = getSpeed(cmd);
        }

        WaitForInput();
    })
}

noble.on('discover', function (peripheral) {

    if (peripheral.advertisement != null &&
        peripheral.advertisement.localName != null &&
        peripheral.advertisement.localName.startsWith("Mo")) {
        noble.stopScanning();
        console.log('A Robomow named ' + peripheral.advertisement.localName + ' was found.');
        Mower = peripheral;
        ConnectAndSetUp()
    }
});

function ConnectAndSetUp() {
    Mower.connect(error => {
        console.log('Connected to', Mower.id);
        Mower.discoverSomeServicesAndCharacteristics(serviceUUIDs, authUUIDS, onAuthServiceDiscoverd);
    });
    Mower.on('disconnect', () => {
        StartNopper(false);
        console.log('disconnected, ...');
    });
}

function onAuthServiceDiscoverd(error, services, characteristics) {
    const authChar = characteristics[0];
    authChar.subscribe(error => {
        if (error) {
            console.error('Error subscribing to Auth ');
        } else {
            console.log('Processing auth request.');

            // Ok, do auth, down below I use the byte[] from my motherboard serial, please calculate your own.
            // !! Use getMoboSerNrToBytes(serial) to get the byte[] for your motherboard.
            // ie getMoboSerNrToBytes("6411800003071") will return the byte[] below but you need to fill the buffer
            // until it's 15 in length, padd right with 0.
            authChar.write(new Buffer([54, 52, 49, 49, 56, 48, 48, 48, 48, 51, 48, 55, 49, 0, 0]));
            authChar.read(function (error, data) {
                for (i = 0; i < data.length; i++) {
                    if (data[i] == 0) {
                        console.log("Not Authorised!");
                        return;
                    }
                }
                console.log("Authenticated!");

                // Connect to the data.
                Mower.discoverSomeServicesAndCharacteristics(serviceUUIDs, dataUUIDS, onDataServiceDiscoverd);
                // lets get Info services
                // Mower.discoverSomeServicesAndCharacteristics(serviceUUIDs, notifUUIDS, onNotifServiceDiscoverd);
            });
        }
    });
}

function onDataServiceDiscoverd(error, services, characteristics) {
    console.log('Connected to command service.');
    charData = characteristics[0];
    StartNopper(true);
    RunNopper();
    WaitForInput();
}

function ControlRobot(direction, speed, engageCutters, repeats) {
    stop = false;
    RepeatRobotMessage(direction, speed, engageCutters, 0, repeats);
}

function RepeatRobotMessage(direction, speed, engageCutters, current, max) {

    if (current < max) {
        StartNopper(false);
        setTimeout(() => {
            if (!stop) {
                DoControl(direction, speed, engageCutters);
                RepeatRobotMessage(direction, speed, engageCutters, ++current, max);
            }
        }, MESSAGE_TIMEOUT, 'REPEAT');
    } else {
        StartNopper(true);
    }
}

function DoControl(direction, speed, engageCutters) {

    var sum = 0;
    var looper = 0;
    var safety = ++safetyCounter % 255;
    var safetyBit = ((engageCutters ? 2 : 0) | safety << 4);

    var data = new Buffer([-86, 10, 31, 26, safetyBit, direction, speed, 0, 0, 0]);
    while (looper < data.length - 1) {
        sum += data[looper++];
    }
    data[data.length - 1] = ~sum;
    charData.write(data);

    if (showDebug) {
        printHexString(data);
    }
}

function printHexString(data) { // Shows output and uses java byte notation
    var res = "";
    var looper = 0;
    while (looper < data.length) {
        var p = data[looper++];
        if (p > 127) {
            p = p - 256;
        }
        res = res + p.toString() + " ";
    }
    console.log(res);
}

function getMoboSerNrToBytes(serialNr){
  // Creates the byte[] from the motherboard serial to pass it on to the auth challenge.
  // Please note that the auth should receive a 15 length array, so padd right with 0
  var bytes = []; 
  for (var i = 0; i <serialNr.length; ++i) {
      var code = str.charCodeAt(i);
      bytes = bytes.concat([code]);
  }

  console.log('Mobo result', bytes.join(', '));
  return bytes;
}

function StartNopper(enabled){
        sendNop = enabled;
}

function RunNopper(){
    setTimeout(() => {
        if(sendNop){
            charData.write(nopData);
        }
        RunNopper();
    }, NOPPER_TIMEOUT, 'NOP');
}
