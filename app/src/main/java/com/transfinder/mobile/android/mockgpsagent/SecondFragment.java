package com.transfinder.mobile.android.mockgpsagent;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.transfinder.mobile.android.mockgpsagent.databinding.FragmentSecondBinding;
import com.transfinder.mobile.android.mockgpsagent.helpers.UdpMessagingHelpers;

public class SecondFragment extends Fragment {
    private static final String TAG = "SecondFragment";
    private FragmentSecondBinding binding;
    private EditText txtUdpHostPort;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initUI();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initUI() {

        txtUdpHostPort = binding.txtUdpHostPort;

        binding.btnCancelSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    cancelAndRestoreSettings();
                } catch (Exception e) {
                    Log.e(TAG, "btnCancelSettings clicked: " + e.getMessage());
                }
            }
        });

        binding.btnSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    validateAndSaveSettings();
                } catch (Exception e) {
                    Log.e(TAG, "btnSaveSettings clicked: " + e.getMessage());
                }

            }
        });

        populateUdfHostPortFromSettings();
    }

    private void cancelAndRestoreSettings() {
        populateUdfHostPortFromSettings();

        NavHostFragment.findNavController(SecondFragment.this)
                .navigate(R.id.action_SecondFragment_to_FirstFragment);
    }

    private void validateAndSaveSettings() {
        String portInputValue = String.valueOf(txtUdpHostPort.getText());
        try {
            int portNum = Integer.parseInt(portInputValue);
            if (portNum > 1000 && portNum <= 65535) {
                UdpMessagingHelpers.UdpPort = portNum;

                NavHostFragment.findNavController(SecondFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment);
            } else {
                showErrorAlert("Invalid Port number!");
            }

        } catch(NumberFormatException nfe) {
            Log.e(TAG, "validateAndSaveSettings: " + nfe.getMessage());
            showErrorAlert("Invalid Port number!");
        }
    }

    private void populateUdfHostPortFromSettings() {
        int port = UdpMessagingHelpers.UdpPort;
        txtUdpHostPort.setText(String.valueOf(port));
    }

    private void showErrorAlert(String errorMessage) {
        AlertDialog alertDialog = new AlertDialog.Builder(getContext()).create();
        alertDialog.setTitle("Error");
        alertDialog.setMessage(errorMessage);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }
}