package se.drbyt.robomowremotecontrol;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.TextView;

import java.security.Permission;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback{
    Intent mServiceIntent;
    private RobomowService mRobomowService;
    Context ctx;
    TextView txtInfo;
    public Context getCtx() {
        return ctx;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctx = this;
        setContentView(R.layout.activity_main);
        txtInfo = findViewById(R.id.txtInfo);
        CheckPermissions();
    }


    public void CheckPermissions(){
        Permissions perms = new Permissions();
        if(perms.HasAllPermissions(this)){
            Start();
        } else {
            perms.AskPermissions(this);
        }
    }

    public void Start() {
       ShowIP();
        mRobomowService = new RobomowService(getCtx());
        mServiceIntent = new Intent(getCtx(), mRobomowService.getClass());
        if (!isMyServiceRunning(mRobomowService.getClass())) {
            startService(mServiceIntent);
        }
    }


    private void ShowIP() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        SetText("IP Address: " + ip);
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.i ("isMyServiceRunning?", true+"");
                return true;
            }
        }
        Log.i ("isMyServiceRunning?", false+"");
        return false;
    }


    @Override
    protected void onDestroy() {
        stopService(mServiceIntent);
        Log.i("MAINACT", "onDestroy!");
        super.onDestroy();

    }


    /// HELPERS

    public void SetText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtInfo.setText(text);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == 666) {
                    Start();
                }
            }
        }
    }

}
