package com.transfinder.mobile.android.mockgpsagent;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.transfinder.mobile.android.mockgpsagent.databinding.FragmentFirstBinding;
import com.transfinder.mobile.android.mockgpsagent.helpers.MockGpsEventCallback;
import com.transfinder.mobile.android.mockgpsagent.helpers.UdpMessagingHelpers;

public class FirstFragment extends Fragment implements MockGpsEventCallback {

    private static final String TAG = "FirstFragment";

    private static final String START_UDP_BUTTON_LABEL = "Start UDP Host";
    private static final String STOP_UDP_BUTTON_LABEL = "Stop UDP Host";

    @SuppressLint("StaticFieldLeak")
    static TextView udpServerAddr;

    @SuppressLint("StaticFieldLeak")
    static TextView latestUdpMessage;

    @SuppressLint("StaticFieldLeak")
    private static Button startButton;

    private Activity mActivity;
    private FragmentFirstBinding binding;

    private UdpMessagingService.UdpServiceBinder mBinder;
    private UdpMessagingService mUdpSvc;


    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "onServiceConnected: connected to service");
            UdpMessagingService.UdpServiceBinder binder = (UdpMessagingService.UdpServiceBinder) iBinder;
            mBinder = binder;

            mUdpSvc = mBinder.getService();
            mUdpSvc.registerMockGpsEventCallback(FirstFragment.this);

            String udpHostAddress = mUdpSvc.getUdpHostAddressInfo();
            udpServerAddr.setText(udpHostAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected: disconnected from service");
            mBinder = null;
            mUdpSvc = null;
            udpServerAddr.setText("");
        }
    };

    private static boolean isRunning = false;


    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        mActivity = getActivity();
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        udpServerAddr = binding.udpServerAddr;
        latestUdpMessage = binding.latestUdfMessage;
        startButton = binding.startButton;

        return view;
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initUI();
    }

    private void initUI() {

        // check if service already running
        Boolean isUdpSvcRunning = isUdpServiceRunning();
        isRunning = isUdpSvcRunning;

        if (isRunning) {
            bindUdpMessagingService();
        }

        startButton.setText(isRunning ? STOP_UDP_BUTTON_LABEL : START_UDP_BUTTON_LABEL);
        startButton.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {

                Boolean isUdpSvcRunning = isUdpServiceRunning();

                if (isRunning) {
                    if (isUdpSvcRunning) {
                        unbindUdpMessagingService();
                        stopUdpMessagingService();
                    }

                    udpServerAddr.setText("");
                    startButton.setText(START_UDP_BUTTON_LABEL);
                } else {
                    if (!isUdpSvcRunning) {
                        startUdfMessagingService();
                        bindUdpMessagingService();
                    }

                    startButton.setText(STOP_UDP_BUTTON_LABEL);
                }

                isRunning = !isRunning;
            }
        });
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void postNewMockGpsEventInfo(String eventContent) {
//        Log.d(TAG, "postNewMockGpsEvent: " + eventContent);
        latestUdpMessage.setText(eventContent);
    }

    private void startUdfMessagingService() {
        int portNumber = UdpMessagingHelpers.UdpPort;
        Intent udpSvcIntent = new Intent(mActivity, UdpMessagingService.class);
        udpSvcIntent.putExtra(UdpMessagingService.UDP_PORT_PARAMETER_KEY,  String.valueOf(portNumber)); // Pass port number as parameter of Intent
        udpSvcIntent.setAction(UdpMessagingService.ACTION_START_UDP_SERVICE);
        mActivity.startForegroundService(udpSvcIntent);

    }

    private void stopUdpMessagingService() {
        Intent udpSvcIntent = new Intent(mActivity, UdpMessagingService.class);
        udpSvcIntent.setAction(UdpMessagingService.ACTION_STOP_UDP_SERVICE);
        mActivity.startForegroundService(udpSvcIntent);
    }

    private void bindUdpMessagingService() {
        Intent udpSvcIntent = new Intent(mActivity, UdpMessagingService.class);
        mActivity.bindService(udpSvcIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindUdpMessagingService() {
        if (mBinder != null) {
            if (mUdpSvc != null) {
                mUdpSvc.unregisterMockGpsEventCallback();
            }

            mActivity.unbindService(serviceConnection);
            mBinder = null;
            mUdpSvc = null;
        }
    }

    private boolean isUdpServiceRunning() {
        Context context = getContext();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (service.service.getClassName().equals(UdpMessagingService.class.getName())) {
//                if (service.foreground) {
//                    return true;
//                }
                return true;
            }
        }
        return false;
    }
}