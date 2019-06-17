package se.drbyt.robomowremotecontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.UUID;

import static java.lang.System.arraycopy;

public class RobotMessaging {

    // YOU MUST SET THIS TO THE SERIAL NUMBER OF YOUR ROBOMOW MOTHERBOARD.
    private String MOTHERBOARD_SERIAL_NR = "6411800003071";

    private BluetoothAdapter blueAdapt;
    private BluetoothGatt mGatt;
    private BluetoothManager blueMgr;
    private Context context;
    private String TAG = "RobotMessaging";
    private int safetyCounter = 0;
    private boolean connected = false;
    private Boolean stopSendingPackets = false;
    // BLE SERVICES UIDS
    private static final UUID
            RBLE_AUTH_UUID_V_3_5 = UUID.fromString("ff00a502-d020-913c-1234-56d97200a6a6"),
            RBLE_DATA_UUID_V3_5 = UUID.fromString("ff00a503-d020-913c-1234-56d97200a6a6"),
            RBLE_SERVICE_UUID_V_3_5 = UUID.fromString("ff00a501-d020-913c-1234-56d97200a6a6"),
            RBLE_UARTDATA_UUID_V3_5 = UUID.fromString("ff00a506-d020-913c-1234-56d97200a6a6"),
            NOTIFICATION_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private ControlServer ctrlServer;
    private Nopper nopper;

    public void ConnectRobot(Context ctx, ControlServer ctrl) {
        context = ctx;
        ctrlServer = ctrl;
        StartScanning();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED)
                mGatt.discoverServices();
        }

        private boolean isAuthCharacteristic(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
            return bluetoothGattCharacteristic.getUuid().equals(RBLE_AUTH_UUID_V_3_5);
        }

        private boolean isAuthenticationSuccessful(byte[] resultArray) {
            for (int i = 0; i < resultArray.length; i++) {
                if (resultArray[i] == 0)
                    return false;
            }
            return true;
        }

        private boolean readAuthValue() {

            BluetoothGatt bluetoothGatt = mGatt;
            if (bluetoothGatt == null) {
                PublishError("BluetoothGatt is null");
                return false;
            }
            BluetoothGattService bluetoothGattService = bluetoothGatt.getService(RBLE_SERVICE_UUID_V_3_5);
            if (bluetoothGattService == null) {
                PublishError("Device found but no BLE service was found");
                return false;
            }
            BluetoothGattCharacteristic bluetoothGattCharacteristic = bluetoothGattService.getCharacteristic(RBLE_AUTH_UUID_V_3_5);
            if (bluetoothGattCharacteristic == null) {
                PublishError("Device found but no Auth has failed");
                return false;
            }

            return bluetoothGatt.readCharacteristic(bluetoothGattCharacteristic);
        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (isAuthCharacteristic(bluetoothGattCharacteristic)) {
                    if (readAuthValue()) {
                        PublishInfo("Device found and authenticated.");
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt blueGatt, BluetoothGattCharacteristic blueChar, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && isAuthCharacteristic(blueChar)) {
                if (isAuthenticationSuccessful(blueChar.getValue())) {
                    connected = true;
                    StartNopper();
                } else {
                    connected = false;
                }

                PublishAuth(connected);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                PublishInfo("Discovered services, attempting to authenticate...");
                SendAuthentication();
            } else {
                PublishError("Error discovering services, it ends here.");
            }
        }
    };


    private void StartNopper() {
        nopper = new Nopper();
        nopper.Start(this, 500);
    }

    private void PublishAuth(boolean b) {
        ctrlServer.PublishMessage("AUTH", b ? "OK" : "NOK");
    }

    public Boolean isConnected() {
        return connected;
    }

    public void StartScanning() {


        PublishInfo("Scanning for mower...");
        blueMgr = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        blueAdapt = blueMgr.getAdapter();
        blueAdapt.enable();
        blueAdapt.startLeScan(scan_callback);
    }

    public void ConnectMower(BluetoothDevice mower) {
        PublishInfo("Mower found, trying to connect and authenticate...");
        blueAdapt.stopLeScan(scan_callback);
        if (mGatt == null) {
            mGatt = mower.connectGatt(context.getApplicationContext(), true, gattCallback);

        } else {
            mGatt = mower.connectGatt(context.getApplicationContext(), true, gattCallback);
        }
    }

    private void SendAuthentication() {

        PublishInfo("Sending authentication to mower,...");

        byte[] fixedSizeArray = new byte[15];
        byte[] motherboardSerialInBytes = MOTHERBOARD_SERIAL_NR.getBytes(Charset.forName("ASCII"));

        if (motherboardSerialInBytes.length <= 15) {
            arraycopy(motherboardSerialInBytes, 0, fixedSizeArray, 0, motherboardSerialInBytes.length);
        }

        if (mGatt != null) {
            if (writeAuthValue(fixedSizeArray))
                PublishInfo("Connected and authenticated.");
            else PublishError("Error authenticating!");
        } else {
            PublishError("Lost connection!");
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback scan_callback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (device != null && device.getName() != null) {
                if (device.getName().startsWith("Mo")) {
                    ConnectMower(device);
                }
            }
        }
    };


    private boolean writeAuthValue(byte[] arrby) {
        int retryCount = 0;
        try {
            do {
                Thread.sleep(150);
            } while (mGatt == null && ++retryCount < 5);
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
        }
        if (mGatt == null) {
            return false;
        } else {
            try {

                BluetoothGattService service = mGatt.getService(RBLE_SERVICE_UUID_V_3_5);
                BluetoothGattCharacteristic charAuth = service.getCharacteristic(RBLE_AUTH_UUID_V_3_5);

                if (charAuth == null) {
                    return false;
                }
                boolean bl = charAuth.setValue(arrby);
                boolean bl2 = mGatt.writeCharacteristic(charAuth);

                if (!bl || !bl2) return false;
            } catch (Exception e) {
                blueAdapt.startLeScan(scan_callback);
                return false;
            }
        }
        PublishInfo("Connected!");
        return true;
    }


    public void SendNoOperationKeepAlive() {
        BluetoothGattService mSVC = mGatt.getService(RBLE_SERVICE_UUID_V_3_5);
        BluetoothGattCharacteristic mCH = mSVC.getCharacteristic(RBLE_DATA_UUID_V3_5);
        int sum = 0;
        int looper = 0;

        byte[] data = new byte[]{-86, 10, 31, 26, 0, 0, 0, 0, 0, -1};

        while (looper < data.length - 1) {
            sum += data[looper];
            ++looper;
        }

        data[data.length - 1] = (byte) (~sum);

        if (mCH.setValue(data)) {
            if (!mGatt.writeCharacteristic(mCH)) {
                PublishError(Arrays.toString(data) + " FAIL");
            }
//            else {
//                PublishInfo(Arrays.toString(data) + " OK!");
//            }
        }
    }

    public void DoControl(int direction, int speed, Boolean engageCutters, BluetoothGattCharacteristic dataChar) {
        nopper.Pause();
        int sum = 0;
        int looper = 0;
        int safetyBit = ++safetyCounter % 255;
        byte[] data = new byte[]{-86, 10, 31, 26, (byte) ((engageCutters ? 2 : 0) | safetyBit << 4), (byte) direction, (byte) speed, 0, 0, -1};

        while (looper < data.length - 1) {
            sum += data[looper];
            ++looper;
        }

        data[data.length - 1] = (byte) (~sum);

        if (dataChar.setValue(data)) {
            if (!mGatt.writeCharacteristic(dataChar)) {
                PublishError(Arrays.toString(data) + " FAIL");
            }
//            else {
//                PublishInfo(Arrays.toString(data) + " OK!");
//            }
        }

        nopper.Resume();
    }


    public void PublishInfo(String msg) {
        ctrlServer.PublishMessage("INFO", msg);
        Log.d(TAG, msg);
    }

    public void PublishError(String msg) {
        ctrlServer.PublishMessage("ERR", msg);
        Log.d(TAG, msg);
    }

    public void StopSend(Boolean yes) {
        stopSendingPackets = yes;
    }

    public Thread SendControlPackets(final int direction, final int speed, final int repeat, final int timeBetweenSendInMS, final Boolean activateBlades) {
        stopSendingPackets = false;
        BluetoothGattService robotService = mGatt.getService(RBLE_SERVICE_UUID_V_3_5);
        final BluetoothGattCharacteristic dataChar = robotService.getCharacteristic(RBLE_DATA_UUID_V3_5);

        final Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    nopper.Pause();
                    for (int i = 0; i < repeat; i++) {
                        if (!stopSendingPackets) {
                            DoControl(direction, speed, activateBlades, dataChar);
                            Thread.sleep(timeBetweenSendInMS);
                        } else break; // Stop for loop
                    }
                    nopper.Resume();
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        });

        t.start();
        return t;
    }

    public void DisconnectRobot() {
        try {
            if (null != mGatt)
                mGatt.disconnect();
            if (null != blueAdapt) {
                blueAdapt.cancelDiscovery();
            }
            if (null != nopper)
                nopper.Pause();
        } catch (Exception e) {
            PublishError(e.toString());
        }
    }
}
