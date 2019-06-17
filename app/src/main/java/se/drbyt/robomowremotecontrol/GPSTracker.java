package se.drbyt.robomowremotecontrol;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;

import static android.content.Context.LOCATION_SERVICE;

public class GPSTracker implements LocationListener {

    private final Context mContext;
    boolean isGPSEnabled = false;
    boolean isNetworkEnabled = false;

    Location location;
    double latitude;
    double longitude;

    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1; // 1 meters
    private static final long MIN_TIME_BW_UPDATES = 30000; // half a minute
    protected LocationManager locationManager;
    ControlServer ctrlServer;

    public GPSTracker(Context context, ControlServer service) {
        this.mContext = context;
        ctrlServer = service;

        locationManager = (LocationManager) mContext.getApplicationContext().getSystemService(LOCATION_SERVICE);
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!isGPSEnabled && !isNetworkEnabled) {
            ctrlServer.PublishMessage("ERR", "No Location services available");
        } else {
            mHandler = new Handler();
            locationChecker.run();
        }
    }

    private int mInterval = 1000 * 30; // 60 seconds by default, can be changed later
    private Handler mHandler;


    Runnable locationChecker = new Runnable() {
        @Override
        public void run() {
            try {
                getLocation();
            } finally {
                mHandler.postDelayed(locationChecker, mInterval);
            }
        }
    };

    public Location getLocation() {
        try {


            try {
                if (isNetworkEnabled) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                    if (locationManager != null) {
                        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (location != null) {
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                        }
                    }
                }
                // If GPS enabled, get latitude/longitude using GPS Services
                if (isGPSEnabled) {
                    if (location == null) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                        if (locationManager != null) {
                            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                            }
                        }
                    }
                }
            } catch (SecurityException e) {
                ctrlServer.PublishMessage("ERR", e.toString());
            }

            ctrlServer.PublishGPS(latitude, longitude);

        } catch (
                Exception e) {
            ctrlServer.PublishMessage("ERR", e.toString());
        }

        return location;
    }


    /**
     * Stop using GPS listener
     */
    public void StopUsingGPS() {
        if (locationManager != null) {
            locationManager.removeUpdates(GPSTracker.this);
        }
    }

    public double getLatitude() {
        if (location != null) {
            latitude = location.getLatitude();
        }
        // return latitude
        return latitude;
    }

    public double getLongitude() {
        if (location != null) {
            longitude = location.getLongitude();
        }

        // return longitude
        return longitude;
    }


    @Override
    public void onLocationChanged(Location location) {
    }


    @Override
    public void onProviderDisabled(String provider) {
    }


    @Override
    public void onProviderEnabled(String provider) {
    }


    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }
}