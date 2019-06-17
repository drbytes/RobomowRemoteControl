package se.drbyt.robomowremotecontrol;

import android.os.Handler;
import android.os.Looper;

public class Nopper {

    RobotMessaging robotCtrl;
    Boolean pauzed = false;
    private int mInterval = 1500; // 500ms
    private Handler mHandler;

    Runnable nopSender = new Runnable() {
        @Override
        public void run() {
            try {
                if(!pauzed) {
                    robotCtrl.SendNoOperationKeepAlive();
                }
            } finally {
                mHandler.postDelayed(nopSender, mInterval);
            }
        }
    };

    public Boolean isRunning(){
        return !pauzed;
    }

    public void Pause(){
        this.pauzed = true;
    }
    public void Resume(){

        this.pauzed = false;
    }

    public void Start(RobotMessaging rM, int repeatInMs){
        mHandler = new Handler(Looper.getMainLooper());
        mInterval = repeatInMs;
        robotCtrl = rM;
        nopSender.run();
    }
}
