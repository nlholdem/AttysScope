/**
 Copyright 2016 Bernd Porr, mail@berndporr.me.uk

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 **/

/**
 * Modified code from:
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html
 */

package uk.me.berndporr.www.attysplot;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.UUID;


/**
 * Created by bp1 on 14/08/16.
 */
public class AttysComm extends Thread {
    public final static int BT_CONNECTED = 0;
    public final static int BT_ERROR = 1;
    public final static int BT_RETRY = 2;
    public final static int BT_CONFIGURE = 3;
    public final static int BT_CSV_RECORDING = 4;

    public final static byte ADC_RATE_125HZ = 0;
    public final static byte ADC_RATE_250HZ = 1;
    public final static byte ADC_RATE_500Hz = 2;
    public final static byte ADC_DEFAULT_RATE = ADC_RATE_250HZ;
    public final static float[] ADC_SAMPLINGRATE = {125F,250F,500F,1000F};
    private byte adc_rate_index = ADC_DEFAULT_RATE;

    public final static byte ADC_GAIN_6 = 0;
    public final static byte ADC_GAIN_1 = 1;
    public final static byte ADC_GAIN_2 = 2;
    public final static byte ADC_GAIN_3 = 3;
    public final static byte ADC_GAIN_4 = 4;
    public final static byte ADC_GAIN_8 = 5;
    public final static byte ADC_GAIN_12 = 6;
    public final static float[] ADC_GAIN_FACTOR = {6.0F,1.0F,2.0F,3.0F,4.0F,8.0F,12.0F};
    private byte adc0_gain_index = 0;
    private byte adc1_gain_index = 0;

    public final static byte ADC_CURRENT_6NA = 0;
    public final static byte ADC_CURRENT_22NA = 1;
    public final static byte ADC_CURRENT_6UA = 2;
    public final static byte ADC_CURRENT_22UA = 3;
    private byte adc_current_index = 0;

    public final static byte ADC_MUX_NORMAL = 0;
    public final static byte ADC_MUX_SHORT = 1;
    public final static byte ADC_MUX_SUPPLY = 3;
    public final static byte ADC_MUX_TEMPERATURE = 4;
    public final static byte ADC_MUX_TEST_SIGNAL = 5;
    public final static byte ADC_MUX_ECG_EINTHOVEN = 6;
    private byte adc0_mux_index = ADC_MUX_NORMAL;
    private byte adc1_mux_index = ADC_MUX_NORMAL;

    public final static float ADC_REF = 2.42F; // Volt

    public final static float[] ACCEL_FULL_SCALE = {2,4,8,16}; // G
    public final static byte ACCEL_2G = 0;
    public final static byte ACCEL_4G = 1;
    public final static byte ACCEL_8G = 2;
    public final static byte ACCEL_16G = 3;
    private byte accel_full_scale_index = ACCEL_16G;

    public final static float[] GYRO_FULL_SCALE= {250,500,1000,2000}; //DPS
    public final static byte GYRO_250DPS = 0;
    public final static byte GYRO_500DPS = 1;
    public final static byte GYRO_1000DPS = 2;
    public final static byte GYRO_2000DPS = 3;
    private byte gyro_full_scale_index = GYRO_2000DPS;

    public final static float MAG_FULL_SCALE = 4800.0E-6F; // TESLA

    private BluetoothSocket mmSocket;
    private Scanner inScanner = null;
    private boolean doRun;
    private float[][] ringBuffer = null;
    final private int nMem = 1000;
    private int inPtr = 0;
    private int outPtr = 0;
    final private int nRawFieldsPerLine = 14;
    private ConnectThread connectThread = null;
    private boolean isConnected = false;
    final private int nChannels = 11;
    private InputStream mmInStream = null;
    private OutputStream mmOutStream = null;
    private static final String TAG = "AttysComm";
    private boolean fatalError = false;
    private Handler parentHandler;
    private BluetoothDevice bluetoothDevice;
    private byte[] adcMuxRegister = null;
    private int adcSamplingRate = ADC_DEFAULT_RATE;
    private byte[] adcGainRegister = null;
    private boolean[] adcCurrNegOn = null;
    private boolean[] adcCurrPosOn = null;
    private byte adcCurrent;
    private float gyroFullScaleRange;
    private float accelFullScaleRange;
    private byte expectedTimestamp = 0;

    private PrintWriter csvFileStream = null;
    private float timestamp = 0.0F;

    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private boolean connectionEstablished;

        public BluetoothSocket getBluetoothSocket() {
            return mmSocket;
        }

        public boolean hasActiveConnection() { return connectionEstablished; }

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            bluetoothDevice = device;
            BluetoothSocket tmp = null;
            connectionEstablished = false;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                //tmp = device.createRfcommSocketToServiceRecord(uuid);
                tmp = device.createInsecureRfcommSocketToServiceRecord(uuid);
            } catch (Exception e) {
                Log.d(TAG,"Could not get rfComm socket");
                parentHandler.sendEmptyMessage(BT_ERROR);
            }
            mmSocket = tmp;
            if (tmp != null) Log.d(TAG,"Got rfComm socket");
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            // mBluetoothAdapter.cancelDiscovery();

            if (mmSocket != null) {
                try {
                    if (mmSocket != null) {
                        mmSocket.connect();
                    }
                } catch (IOException connectException) {

                    parentHandler.sendEmptyMessage(BT_RETRY);

                    try {
                        sleep(3000);
                    } catch (InterruptedException e1) {}

                    try {
                        if (mmSocket != null) {
                            mmSocket.connect();
                        }
                    } catch (IOException e2) {

                        try {
                            sleep(5000);
                        } catch (InterruptedException e3) {}

                        try {
                            if (mmSocket != null) {
                                mmSocket.connect();
                            }
                        } catch (IOException e4) {

                            connectionEstablished = false;
                            fatalError = true;
                            Log.d(TAG, "Could not establish connection");
                            Log.d(TAG, e4.getMessage());
                            parentHandler.sendEmptyMessage(BT_ERROR);

                            try {
                                if (mmSocket != null) {
                                    mmSocket.close();
                                }
                            } catch (IOException closeException) {}
                            mmSocket = null;
                            return;
                        }
                    }
                }
                connectionEstablished = true;
                Log.d(TAG, "Connected to socket");
            }
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException e) { }
            mmSocket = null;
        }
    }


    public boolean hasActiveConnection() { return isConnected;}



    public AttysComm(BluetoothDevice device, Handler handler) {

        adcMuxRegister = new byte[2];
        adcMuxRegister[0] = 0;
        adcMuxRegister[1] = 0;
        adcGainRegister = new byte[2];
        adcGainRegister[0] = 0;
        adcGainRegister[1] = 0;
        adcCurrNegOn = new boolean[2];
        adcCurrNegOn[0] = false;
        adcCurrNegOn[1] = false;
        adcCurrPosOn = new boolean[2];
        adcCurrPosOn[0] = false;
        adcCurrNegOn[1] = false;

        // transmits errors etc
        parentHandler = handler;

        connectThread = new ConnectThread(device);
        // let's try to connect
        // b/c it's blocking we do it in another thread
        connectThread.start();

    }


    private void stopADC() {
        String s = "\r\n\r\n\r\nx=0\r";
        byte[] bytes = s.getBytes();
        for(int j=0;j<100;j++) {
            Log.d(TAG, "Trying to stop the data acquisition. Attempt #"+(j+1)+".");
            try {
                if (mmOutStream != null) {
                    mmOutStream.flush();
                    mmOutStream.write(bytes);
                    mmOutStream.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not send x=0 to the Attys.");
            }
            for (int i = 0; i < 100; i++) {
                if (inScanner != null) {
                    if (inScanner.hasNextLine()) {
                        String l = inScanner.nextLine();
                        if (l.equals("OK")) {
                            Log.d(TAG, "ADC stopped. Now in command mode.");
                            return;
                        }
                    } else {
                        yield();
                    }
                }
            }
        }
        Log.e(TAG, "Could not detect OK after x=0");
    }


    private void startADC() {
        String s = "x=1\r";
        byte[] bytes = s.getBytes();
        try {
            mmOutStream.flush();
            mmOutStream.write(13);
            mmOutStream.write(10);
            mmOutStream.write(bytes);
            mmOutStream.flush();
            Log.d(TAG, "ADC started. Now acquiring data.");
        } catch (IOException e) {
            Log.e(TAG, "Could not send x=1 to the Attys.");
        }
    }


    private void sendSyncCommand(String s) {
        byte[] bytes = s.getBytes();

        try {
            if (mmOutStream != null) {
                mmOutStream.flush();
                mmOutStream.write(10);
                mmOutStream.write(13);
                mmOutStream.write(bytes);
                mmOutStream.write(13);
                mmOutStream.flush();
            } else {
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not write to stream.");
        }
        for (int j = 0; j < 100; j++) {
            if (inScanner.hasNextLine()) {
                String l = inScanner.nextLine();
                if (l.equals("OK")) {
                    Log.d(TAG, "Sent successfully '" + s + "' to the Attys.");
                    return;
                }
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.e(TAG, "ATTYS hasn't replied with OK after command: "+s+".");
    }


    private void sendAsyncCommand(String s) {
        byte[] bytes = s.getBytes();

        try {
            mmOutStream.flush();
            mmOutStream.write(10);
            mmOutStream.write(13);
            mmOutStream.write(10);
            mmOutStream.write(13);
            mmOutStream.write(10);
            mmOutStream.write(13);
            mmOutStream.write(bytes);
            mmOutStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Could not write to stream.");
        }
    }


    private void setSamplingRate(int rate) {
        adcSamplingRate = rate;
        sendSyncCommand("r="+rate);
    }


    public float getSamplingRateInHz() {
        return ADC_SAMPLINGRATE[adcSamplingRate];
    }


    private void setFullscaleGyroRange(int range) {
        sendSyncCommand("g="+range);
        gyroFullScaleRange = GYRO_FULL_SCALE[range];
    }

    private void setFullscaleAccelRange(int range) {

        sendSyncCommand("t="+range);
        accelFullScaleRange = ACCEL_FULL_SCALE[range];
    }

    public float getGyroFullScaleRange() {
        return gyroFullScaleRange;
    }

    public float getAccelFullScaleRange() {
        return accelFullScaleRange;
    }

    public float getMagFullScaleRange() { return MAG_FULL_SCALE; }

    public float getADCFullScaleRange(int channel) {
        return ADC_REF / ADC_GAIN_FACTOR[adcGainRegister[channel]];
    }

    private void setGainMux(int channel, byte gain, byte mux) {
        int v = (mux & 0x0f) | ((gain & 0x0f) << 4);
        switch (channel) {
            case 0:
                sendSyncCommand("a=" + v);
                break;
            case 1:
                sendSyncCommand("b=" + v);
                break;
        }
        adcGainRegister[channel] = gain;
        adcMuxRegister[channel] = mux;
    }

    private void setADCGain(int channel,byte gain) {
        setGainMux(channel,gain,adcMuxRegister[channel]);
    }

    private void setADCMux(int channel, byte mux) {
        setGainMux(channel, adcGainRegister[channel],mux);
    }

    public void setAccel_full_scale_index(byte idx) { accel_full_scale_index = idx; }
    public void setGyro_full_scale_index(byte idx) { gyro_full_scale_index = idx; }
    public void setAdc_samplingrate_index(byte idx) { adc_rate_index = idx; }
    public void setAdc0_gain_index(byte idx) { adc0_gain_index = idx; }
    public void setAdc1_gain_index(byte idx) { adc1_gain_index = idx; }
    public void setAdc0_mux_index(byte idx) { adc0_mux_index = idx; }
    public void setAdc1_mux_index(byte idx) { adc1_mux_index = idx; }

    private boolean sendInit() {
        stopADC();
        // switching to base64 binary format
        sendSyncCommand("d=1");
        setSamplingRate(adc_rate_index);
        setFullscaleGyroRange(gyro_full_scale_index);
        setFullscaleAccelRange(accel_full_scale_index);
        setGainMux(0,adc0_gain_index,adc0_mux_index);
        setGainMux(1,adc1_gain_index,adc1_mux_index);
        startADC();
        return true;
    }

    public java.io.FileNotFoundException startRec(File file) {
        timestamp = 0;
        try {
            csvFileStream = new PrintWriter(file);
            parentHandler.sendEmptyMessage(BT_CSV_RECORDING);
        } catch (java.io.FileNotFoundException e) {
            csvFileStream = null;
            return e;
        }
        return null;
    }


    public void stopRec() {
        if (csvFileStream != null) {
            csvFileStream.close();
            csvFileStream = null;
        }
    }


    public boolean isRecording() {
        return (csvFileStream != null);
    }


    public void saveData(float[] data) {
        if (csvFileStream != null) {
            csvFileStream.format("%f,", timestamp);
            for (int i = 0; i < (data.length - 1); i++) {
                if (csvFileStream != null) {
                    csvFileStream.format("%f,", data[i]);
                }
            }
            if (csvFileStream != null) {
                csvFileStream.format("%f\n", data[data.length - 1]);
            }
            timestamp = timestamp + 1.0F / getSamplingRateInHz();
        }
    }


    public void run() {

        long[] data = new long[12];
        byte[] raw = null;

        try {
            // let's wait until we are connected
            if (connectThread != null) {
                connectThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            isConnected = false;
        }

        if (connectThread != null) {
            if (!connectThread.hasActiveConnection()) {
                isConnected = false;
                return;
            }
        } else {
            return;
        }

        try {
            Log.d(TAG, "Getting streams");
            mmSocket = connectThread.getBluetoothSocket();
            if (mmSocket != null) {
                mmInStream = mmSocket.getInputStream();
                mmOutStream = mmSocket.getOutputStream();
                inScanner = new Scanner(mmInStream);
                ringBuffer = new float[nMem][nChannels];
                isConnected = true;
            }
        } catch (IOException e) {
            Log.d(TAG, "Could not get streams");
            isConnected = false;
            fatalError = true;
            mmInStream = null;
            mmOutStream = null;
            inScanner = null;
            ringBuffer = null;
            parentHandler.sendEmptyMessage(BT_ERROR);
        }

        // we only enter in the main loop if we have connected
        doRun = isConnected;

        parentHandler.sendEmptyMessage(BT_CONFIGURE);
        sendInit();

        Log.d(TAG, "Starting main data acquistion loop");
        parentHandler.sendEmptyMessage(BT_CONNECTED);
        // Keep listening to the InputStream until an exception occurs
        while (doRun) {
            try {
                // Read from the InputStream
                if ((inScanner != null) && inScanner.hasNextLine()) {
                    // get a line from the Attys
                    String oneLine;
                    if (inScanner != null) {
                        oneLine = inScanner.nextLine();
                        //Log.d(TAG,oneLine);
                    } else {
                        return;
                    }
                    if (!oneLine.equals("OK")) {
                        // we have a real sample
                        try {
                            raw = Base64.decode(oneLine, Base64.DEFAULT);

                            // adc data def there
                            if (raw.length > 9) {
                                for (int i = 0; i < 2; i++) {
                                    long v = (raw[i * 4] & 0xff)
                                            | ((raw[i * 4 + 1] & 0xff) << 8)
                                            | ((raw[i * 4 + 2] & 0xff) << 16);
                                    data[9 + i] = v;
                                }
                            }

                            // all data def there
                            if (raw.length > 27) {
                                for (int i = 0; i < 9; i++) {
                                    long v = (raw[10 + i * 2] & 0xff)
                                            | ((raw[10 + i * 2 + 1] & 0xff) << 8);
                                    data[i] = v;
                                }
                            }

                            // check that the timestamp is the expected one
                            byte timestamp = 0;
                            if (raw.length > 8) {
                                timestamp = raw[9];
                                if (Math.abs(timestamp-expectedTimestamp)==1) {
                                    Log.d(TAG, String.format("sample lost"));
                                }
                            }
                            // update timestamp
                            expectedTimestamp = ++timestamp;

                        } catch (Exception e) {
                            // this is triggered if the base64 is too short or any data is too short
                            // this leads to data processed from the previous sample instead
                            Log.d(TAG, "reception error: " + oneLine);
                            expectedTimestamp++;
                        }

                        // acceleration
                        for (int i = 0; i < 3; i++) {
                            float norm = 0x8000;
                            try {
                                ringBuffer[inPtr][i] = ((float) data[i] - norm) / norm *
                                        accelFullScaleRange;
                            } catch (Exception e) {
                                ringBuffer[inPtr][i] = 0;
                            }
                        }

                        // gyroscope
                        for (int i = 3; i < 6; i++) {
                            float norm = 0x8000;
                            try {
                                ringBuffer[inPtr][i] = ((float) data[i] - norm) / norm *
                                        gyroFullScaleRange;
                            } catch (Exception e) {
                                ringBuffer[inPtr][i] = 0;
                            }
                        }

                        // magnetometer
                        for (int i = 6; i < 9; i++) {
                            float norm = 0x8000;
                            try {
                                ringBuffer[inPtr][i] = ((float) data[i] - norm) / norm *
                                        MAG_FULL_SCALE;
                            } catch (Exception e) {
                                ringBuffer[inPtr][i] = 0;
                            }
                        }

                        for (int i = 9; i < 11; i++) {
                            float norm = 0x800000;
                            try {
                                ringBuffer[inPtr][i] = ((float) data[i] - norm) / norm *
                                        ADC_REF / ADC_GAIN_FACTOR[adcGainRegister[i - 9]] * 2;
                            } catch (Exception e) {
                                ringBuffer[inPtr][i] = 0;
                            }
                        }

                        if (csvFileStream != null) {
                            saveData(ringBuffer[inPtr]);
                        }

                        inPtr++;
                        if (inPtr == nMem) {
                            inPtr = 0;
                        }

                    } else {
                        Log.d(TAG, "OK caught from the Attys");
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "System error message:" + e.getMessage());
                Log.d(TAG, "Could not read from stream. Closing down.");
                break;
            }
        }
    }

    public boolean hasFatalError() { return fatalError; }

    public float[] getSampleFromBuffer() {
        if (inPtr != outPtr) {
            // Log.d(TAG,String.format("get:outPtr=%d,data =%f",outPtr,ringBuffer[outPtr][10]));
            float[] sample = ringBuffer[outPtr];
            outPtr++;
            if (outPtr == nMem) {
                outPtr = 0;
            }
            return sample;
        } else {
            return null;
        }
    }

    public boolean isSampleAvilabale() {
        return (inPtr != outPtr);
    }

    public int getNumSamplesAvilable() {
        int n = 0;
        int tmpOutPtr = outPtr;
        while (inPtr != tmpOutPtr) {
            tmpOutPtr++;
            n++;
            if (tmpOutPtr == nMem) {
                tmpOutPtr = 0;
            }
        }
        return n;
    }

    public int getnChannels() {
        return nChannels;
    }


    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
        connectThread.cancel();
        doRun = false;
        if (inScanner != null) {
            inScanner.close();
        }
        if (mmInStream != null) {
            try {
                mmInStream.close();
            } catch (IOException e) {}
        }
        if (mmOutStream != null) {
            try {
                mmOutStream.close();
            } catch (IOException e) {
            }
        }
        if (mmSocket != null) {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
        mmSocket = null;
        isConnected = false;
        fatalError = false;
        mmInStream = null;
        mmOutStream = null;
        inScanner = null;
        ringBuffer = null;
        connectThread = null;
    }
}
