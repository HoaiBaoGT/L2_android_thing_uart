/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.loopback;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.Pwm;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;

import java.io.IOException;

import static com.google.android.things.pio.Gpio.*;
import static com.google.android.things.pio.PeripheralManager.*;

/**
 * Example activity that provides a UART loopback on the
 * specified device. All data received at the specified
 * baud rate will be transferred back out the same UART.
 */
public class LoopbackActivity extends Activity {
    private static final String TAG = "LoopbackActivity";

    // UART Configuration Parameters
    private static final int BAUD_RATE = 115200;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = 1;

    private static final int CHUNK_SIZE = 512;

    private HandlerThread mInputThread;
    private Handler mInputHandler;

    private UartDevice mLoopbackDevice;

    //
    private Gpio LED_RED;
    private Gpio LED_BLUE;
    private Gpio LED_GREEN;
    private Pwm PWM_PIN;
    private double count_time;

    //

    private Runnable mTransferUartRunnable = new Runnable() {
        @Override
        public void run() {
            switch(){
                case "3":
                    count_time = 0;
                    mInputHandler.post(type3);
                    break;
            }
            transferUartData();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Loopback Created");

        // Create a background looper thread for I/O
        try{
            String pinName = BoardDefaults.LEDRed();
            LED_RED = getInstance().openGpio(pinName);
            LED_RED.setDirection(DIRECTION_OUT_INITIALLY_LOW);
            pinName = BoardDefaults.LEDBlue();
            LED_BLUE = getInstance().openGpio(pinName);
            LED_BLUE.setDirection(DIRECTION_OUT_INITIALLY_LOW);
            pinName = BoardDefaults.LEDRed();
            LED_GREEN = getInstance().openGpio(pinName);
            LED_GREEN.setDirection(DIRECTION_OUT_INITIALLY_LOW);
            //PWM  setting
            pinName = BoardDefaults.PWM_Pin();
            PWM_PIN = getInstance().openPwm(pinName);
            PWM_PIN.setPwmFrequencyHz(0.5);
            PWM_PIN.setPwmDutyCycle(0);
            PWM_PIN.setEnabled(true);
            //
        }catch (IOException e){
            Log.e(TAG, "Unable to setting I/O device", e);
        }


        Log.i(TAG, "Setting pin is done.");
        //
        mInputThread = new HandlerThread("InputThread");
        mInputThread.start();
        mInputHandler = new Handler(mInputThread.getLooper());

        // Attempt to access the UART device
        try {
            openUart(BoardDefaults.getUartName(), BAUD_RATE);
            // Read any initially buffered data
            mInputHandler.post(mTransferUartRunnable);
        } catch (IOException e) {
            Log.e(TAG, "Unable to open UART device", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Loopback Destroyed");

        // Terminate the worker thread
        if (mInputThread != null) {
            mInputThread.quitSafely();
        }

        // Attempt to close the UART device
        try {
            closeUart();
        } catch (IOException e) {
            Log.e(TAG, "Error closing UART device:", e);
        }
    }

    /**
     * Callback invoked when UART receives new incoming data.
     */
    private UartDeviceCallback mCallback = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uart) {
            // Queue up a data transfer
            transferUartData();
            //Continue listening for more interrupts
            return true;
        }

        @Override
        public void onUartDeviceError(UartDevice uart, int error) {
            Log.w(TAG, uart + ": Error event " + error);
        }
    };

    /* Private Helper Methods */

    /**
     * Access and configure the requested UART device for 8N1.
     *
     * @param name Name of the UART peripheral device to open.
     * @param baudRate Data transfer rate. Should be a standard UART baud,
     *                 such as 9600, 19200, 38400, 57600, 115200, etc.
     *
     * @throws IOException if an error occurs opening the UART port.
     */
    private void openUart(String name, int baudRate) throws IOException {
        mLoopbackDevice = getInstance().openUartDevice(name);
        // Configure the UART
        mLoopbackDevice.setBaudrate(baudRate);
        mLoopbackDevice.setDataSize(DATA_BITS);
        mLoopbackDevice.setParity(UartDevice.PARITY_NONE);
        mLoopbackDevice.setStopBits(STOP_BITS);

        mLoopbackDevice.registerUartDeviceCallback(mInputHandler, mCallback);
    }

    /**
     * Close the UART device connection, if it exists
     */
    private void closeUart() throws IOException {
        if (mLoopbackDevice != null) {
            mLoopbackDevice.unregisterUartDeviceCallback(mCallback);
            try {
                mLoopbackDevice.close();
            } finally {
                mLoopbackDevice = null;
            }
        }
    }

    /**
     * Loop over the contents of the UART RX buffer, transferring each
     * one back to the TX buffer to create a loopback service.
     *
     * Potentially long-running operation. Call from a worker thread.
     */
    private void transferUartData() {
        if (mLoopbackDevice != null) {
            // Loop until there is no more data in the RX buffer.
            try {
                byte[] buffer = new byte[CHUNK_SIZE];
                int read;
                while ((read = mLoopbackDevice.read(buffer, buffer.length)) > 0) {
                    mLoopbackDevice.write(buffer, read);
                }
            } catch (IOException e) {
                Log.w(TAG, "Unable to transfer data over UART", e);
            }
        }
    }
    /*
    main work
     */
    private Runnable type3 = new Runnable() {
        @Override
        public void run() {
            try{
                count_time = count_time + 10; //Thay doi duty cycle.
                if(100 < count_time){
                    count_time = 0;
                } //Reset ve 0 khi da tang len max.
                PWM_PIN.setPwmDutyCycle(count_time); //Set gia tri.
                mInputHandler.postDelayed(type3, 500); //Goi lai ham sau 0.5s.
            }catch (IOException e) {
                Log.w(TAG, "Type 3 funtion error", e);
            }
        }
    };
}
