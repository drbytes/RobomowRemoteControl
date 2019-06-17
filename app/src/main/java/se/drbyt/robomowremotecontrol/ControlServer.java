package se.drbyt.robomowremotecontrol;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.ParameterizedType;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ControlServer {
    private ServerSocket serverSocket;
    Handler updateConversationHandler;
    Thread serverThread = null;
    int SERVERPORT = 6000;
    RobotMessaging robotCtrl;
    RobomowService service;
    Context context;

    List<CommunicationThread> clients;

    public void Start(int port, Context ctx, RobomowService rService) {
        context = ctx;
        service = rService;
        SERVERPORT = port;
        updateConversationHandler = new Handler();
        this.serverThread = new Thread(new ServerThread());
        this.serverThread.start();
        clients = new ArrayList<>();
    }

    public void ConnectRobot() {
        robotCtrl = new RobotMessaging();
        robotCtrl.ConnectRobot(context, this);
    }

    public void DisconnectRobot() {
        if (robotCtrl != null)
            robotCtrl.DisconnectRobot();
    }


    public void PublishGPS(double latitude, double longitude) {
        for (CommunicationThread ding : clients) {
            ding.WriteBack("GPS:" + String.valueOf(latitude) + "," + String.valueOf(longitude));
        }
    }

    public void PublishMessage(String category, String msg) {
        for (CommunicationThread ding : clients) {
            ding.WriteBack(category + ": " + msg);
        }
    }

    class ServerThread implements Runnable {

        public void run() {
            Socket socket;
            try {
                serverSocket = new ServerSocket(SERVERPORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        socket = serverSocket.accept();
                        CommunicationThread commThread = new CommunicationThread(socket);
                        clients.add(commThread);
                        new Thread(commThread).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class CommunicationThread implements Runnable {
        Socket clientSocket;
        BufferedReader reader;
        BufferedWriter output;
        Boolean mow = false;
        final int FORWARD = -80, BACKWARD = 90, LEFT = -120, RIGHT = 35;
        final int TIME_BETWEEN_SEND_IN_MS = 200;

        public CommunicationThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            try {
                this.reader = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
                this.output = new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void WriteBack(String what) {
            try {
                if (!clientSocket.isClosed()) {
                    output.write(what + "\n");
                    output.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void SteerRobot(final int direction, final int speed, final int repeat, final Boolean activateBlades) {
            if (robotCtrl != null && robotCtrl.isConnected()) {
                robotCtrl.SendControlPackets(direction, speed, repeat, TIME_BETWEEN_SEND_IN_MS, activateBlades);
            } else {
                WriteBack("Not connected to robot!");
            }
        }

        private int getRepeat(String read) {
            try {
                if (read.contains("_")) {
                    String[] params = read.split("_");
                    return Integer.parseInt(params[1]);
                }
            } catch (Exception e) {
                PublishMessage("ERR", "No integer could be parsed");
            }
            return 5;
        }

        public void run() {
            WriteBack("Welcome!");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = reader.readLine().toUpperCase();
                    if (read == "QUIT") {
                        WriteBack("GOODBYE.");
                        reader.close();
                        output.close();
                        clientSocket.close();
                        Thread.currentThread().interrupt();
                    } else {
                        try
                        {
                            if (read.startsWith("FORWARD")) {
                                SteerRobot(FORWARD, 100, getRepeat(read), mow);
                            } else if (read.startsWith("BACKWARD")) {
                                SteerRobot(BACKWARD, 100, getRepeat(read), mow);
                            } else if (read.startsWith("LEFT")) {
                                SteerRobot(LEFT, 100, getRepeat(read), mow);
                            } else if (read.startsWith("RIGHT")) {
                                SteerRobot(RIGHT, 100, getRepeat(read), mow);
                            } else if (read.startsWith("STOP")) {
                                robotCtrl.StopSend(true);
                            } else if (read.startsWith("NAV")) {    //NAV:-75:100:ON
                                String[] params = read.split(":");
                                if (params.length >= 3) {
                                    SteerRobot(Integer.parseInt(params[1]), Integer.parseInt(params[2]), Integer.parseInt(params[3]), mow);
                                    WriteBack("NAV OK.");
                                }
                            } else if (read.startsWith("DISCONNECT")) {
                                DisconnectRobot();
                            } else if (read.startsWith("CONNECT")) {
                                ConnectRobot();
                            } else if (read.startsWith("MOW")) {
                                if (mow) {
                                    mow = false;
                                    WriteBack("Mow OFF");
                                } else {
                                    mow = true;
                                    WriteBack("Mow ON");
                                }
                            }

                            Log.d("Input", "RECV: " + read);
                        } catch (Exception e) {
                            WriteBack(e.toString());
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}