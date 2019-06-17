package se.drbyt.robomowremotecontrol;

import android.app.Service;
        import android.content.Context;
        import android.content.Intent;
        import android.os.IBinder;
        import android.support.annotation.Nullable;
        import android.util.Log;


public class RobomowService extends Service {

    ControlServer server;
    Context context;
    GPSTracker tracker;

    public RobomowService(Context applicationContext) {
        super();
        Log.i("RobomowService", "Started!");
    }

    public RobomowService() {
    }

    public void BootStrapRobotControl(){
        context = this.getApplication().getApplicationContext();
        server  = new ControlServer();
        server.Start(6667, context, this);
        tracker  = new GPSTracker(context, server);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
       super.onStartCommand(intent, flags, startId);
       BootStrapRobotControl();
       return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("EXIT", "ondestroy!");
        Intent broadcastIntent = new Intent(this, SensorRestarterBroadcastReceiver.class);
        sendBroadcast(broadcastIntent);
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}