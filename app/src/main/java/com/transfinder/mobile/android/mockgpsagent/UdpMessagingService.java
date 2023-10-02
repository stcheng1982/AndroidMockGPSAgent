package com.transfinder.mobile.android.mockgpsagent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.transfinder.mobile.android.mockgpsagent.helpers.MockGpsEventCallback;
import com.transfinder.mobile.android.mockgpsagent.helpers.NotificationHelpers;
import com.transfinder.mobile.android.mockgpsagent.location.LocationBuilder;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class UdpMessagingService extends Service {

    public static final String UDP_PORT_PARAMETER_KEY = "UDP_PORT_NUMBER";
    public static final String ACTION_START_UDP_SERVICE = "START_UDP_SERVICE";
    public static final String ACTION_STOP_UDP_SERVICE = "STOP_UDP_SERVICE";
    public static final int UDP_DEFAULT_PORT = 9166;
    private static final String TAG = "UdpMessagingService";
    private static final int ID_SERVICE = 106;

    public static final String LDT_PATTERN = "yyyy-MM-dd HH:mm:ss a";
    public static final DateTimeFormatter LDT_FORMATTER = DateTimeFormatter.ofPattern(LDT_PATTERN);

    private IBinder mBinder = new UdpServiceBinder();
    private Handler mHandler;
    private String mLatestMockGpsEventContent;

    private MockGpsEventCallback mMockGpsEventCallback;

    private DatagramSocket mDataGramSocket;
    private Thread udpThread = null;
    private boolean isRunning = false;

    private StringBuffer currentMockGpsEventInfoBuffer = new StringBuffer();

    @Override
    public void onCreate() {
        super.onCreate();

        // Init Binder members
        mHandler = new Handler();
        mLatestMockGpsEventContent = "";

        // send notification
//        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//        String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel(notificationManager) : "";
//        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
//        Notification notification = notificationBuilder.setOngoing(true)
//                .setContentTitle("Nock GPS notification.")
//                .setCategory(NotificationCompat.CATEGORY_SERVICE)
//                .build();
//
//        startForeground(ID_SERVICE, notification);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String action = intent.getAction();

            switch (action) {
                case ACTION_START_UDP_SERVICE:
                    int udpPort = extractUdpPortFromParam(intent);
                    startForegroundService(udpPort);
                    break;
                case ACTION_STOP_UDP_SERVICE:
                    stopForegroundService();
                    break;
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class UdpServiceBinder extends Binder {
        UdpMessagingService getService() {
            return UdpMessagingService.this;
        }
    }

    public void registerMockGpsEventCallback(MockGpsEventCallback callback) {
        mMockGpsEventCallback = callback;
    }

    public void unregisterMockGpsEventCallback() {
        mMockGpsEventCallback = null;
    }

    public String getUdpHostAddressInfo() {
        if (!isRunning || mDataGramSocket == null) {
            return "";
        }

        // display host ip + port
        int udpSocktPort = mDataGramSocket.getLocalPort();
        String serverAddrInfo = GetLocalUdpServerAddressInfo(udpSocktPort);
        return serverAddrInfo;
    }

    public String getLatestMockGpsEventContent() {
        if (mLatestMockGpsEventContent == null) {
            return "";
        }

        return mLatestMockGpsEventContent;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(NotificationManager notificationManager){
        String channelId = "my_service_channelid";
        String channelName = "My Foreground Service";
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    private void finishForegroundSetup() {
        startForeground(NotificationHelpers.MOCK_GPS_AGENT_NOTIFICATION_IDENTIFIER,
                NotificationHelpers.getNotification(this));
        Log.d(TAG, "After start foreground");
    }

    private void startForegroundService(int port) {
        Log.i(TAG, "startForegroundService executed");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            finishForegroundSetup();
        }

        udpThread = new Thread(
            new Runnable() {
                @Override
                public void run() {
                    try {
                        StartUdpServer(port);

                        ReceiveUdpData();

                        StopUdpServer();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        );

        isRunning = true;
        udpThread.start();
    }

    private void stopForegroundService() {
        Log.i(TAG, "stopForegroundService executed");

        TryStoppingLocationService(); // Try stopping the Location Service (if it's alraady running as foreground svc)

        isRunning = false;
        if (udpThread != null) {

            try {
                udpThread.join(3000);
            } catch (InterruptedException ie) {
                Log.e(TAG, "InterruptedException: " + ie.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        stopForeground(true);
        stopSelf();
    }

    private void StartUdpServer(int port) {
        try {
            mDataGramSocket = new DatagramSocket(port);
            mDataGramSocket.setReuseAddress(true);
            mDataGramSocket.setSoTimeout(1000);
        } catch (SocketException e) {
            Log.e(TAG, "UDF Socket error: " + e.getMessage());
        }catch (Exception e) {
            Log.e(TAG, "Error in StartUdpServer: " + e.getMessage());
        }
    }

    private void StopUdpServer() {
        try {
            if (mDataGramSocket != null && mDataGramSocket.isBound())
            {
                mDataGramSocket.close();
                mDataGramSocket = null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in StopUdpServer: " + e.getMessage());
        }
    }

    private void ReceiveUdpData() {
        String text;
        JSONObject jsonObj;
        byte[] message = new byte[1500];
        DatagramPacket p = new DatagramPacket(message, message.length);

        try {
            while (isRunning) {
                try {
                    mDataGramSocket.receive(p);
                    text = new String(message, 0, p.getLength());
                    Log.d(TAG, "Received Mock GPS: " + text);
                    mLatestMockGpsEventContent = text;

//                    Date dtNow = new Date();

                    jsonObj = new JSONObject(text);
                    double lng = jsonObj.getDouble("lng");
                    double lat = jsonObj.getDouble("lat");
                    double alt = jsonObj.getDouble("alt");
                    float speed = 0.01F;
                    if (jsonObj.has("speed")) {
                        speed = (float)jsonObj.getDouble("speed");
                    }

                    float bearing = 0F;
                    if (jsonObj.has("bearing")) {
                        bearing = (float)jsonObj.getDouble("bearing");
                    }

                    // Try invoking LocationService
                    TryInvokingLocationService(lng, lat, alt, speed, bearing);
                    if (mMockGpsEventCallback != null) {
                        currentMockGpsEventInfoBuffer.delete(0, currentMockGpsEventInfoBuffer.length());
                        currentMockGpsEventInfoBuffer.append(String.format("Received On: %s\r\n", LDT_FORMATTER.format(LocalDateTime.now())));
                        currentMockGpsEventInfoBuffer.append(String.format("Event Content:\r\n%s\r\n", text));
                        mMockGpsEventCallback.postNewMockGpsEventInfo(currentMockGpsEventInfoBuffer.toString());
                    }

                } catch (SocketTimeoutException | NullPointerException ne) {
                    // no response received after 1 second. continue sending
//                    Log.e(TAG, "UDP socket timeout or null-pointer error: " + ne.getMessage());
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in ReceiveUdpData: " + e.getMessage());
        }

    }

    private void TryInvokingLocationService(double longitude, double latitude, double altitude, float speed, float bearing) {
        Intent locationServiceIntent = new Intent(getApplicationContext(), LocationService.class);

        // Put mock gpsEvent parameters
        String longitudeArg = String.format("%.8f", longitude);
        String latitudeArg = String.format("%.8f", latitude);
        String altitudeArg = String.format("%.2f", altitude);
        String speedArg = String.format("%.1f", speed);
        String bearingArg = String.format("%.1f", bearing);

        locationServiceIntent.putExtra(LocationBuilder.LONGITUDE_PARAMETER_KEY,  longitudeArg);
        locationServiceIntent.putExtra(LocationBuilder.LATITUDE_PARAMETER_KEY, latitudeArg);
        locationServiceIntent.putExtra(LocationBuilder.ALTITUDE_PARAMETER_KEY, altitudeArg);
        locationServiceIntent.putExtra(LocationBuilder.SPEED_PARAMETER_KEY, speedArg);
        locationServiceIntent.putExtra(LocationBuilder.BEARING_PARAMETER_KEY, bearingArg);

        startForegroundService(locationServiceIntent);
    }

    private void TryStoppingLocationService() {
        Intent locationServiceIntent = new Intent(getApplicationContext(), LocationService.class);
        stopService(locationServiceIntent);
    }

    private String GetLocalUdpServerAddressInfo(int port) {
        Context context = getApplicationContext();
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        String ip = null;
        if (ip == null) {
            ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        }

        return ip + ":" + port;
    }

    private static int extractUdpPortFromParam(Intent intent) {
        int value = UDP_DEFAULT_PORT;

        try {
            if (intent.hasExtra(UDP_PORT_PARAMETER_KEY)) {
                value = Integer.parseInt(intent.getStringExtra(UDP_PORT_PARAMETER_KEY));
                Log.i(TAG, String.format("Received UDP Port parameter: %s, value: %s", UDP_PORT_PARAMETER_KEY, value));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, String.format("%s should be a valid number. '%s' is given instead",
                    UDP_PORT_PARAMETER_KEY, intent.getStringExtra(UDP_PORT_PARAMETER_KEY)));
            value = UDP_DEFAULT_PORT;
        }

        return value;
    }
}
