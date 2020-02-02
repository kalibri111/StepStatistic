package com.example.step_statistic;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.UUID;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    Button btnOn, btnOff;
    TextView statusText;
    ImageView btState, workState;
    Handler h;

    private static final int REQUEST_ENABLE_BT = 1;
    final int RECIEVE_MESSAGE = 1;        // Статус для Handler

    final String FILENAME = "step_statistic_log.csv";

    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder sb = new StringBuilder();
    private ConnectedThread mConnectedThread;

    // SPP UUID сервиса
    private static final UUID MY_UUID = UUID.fromString("0000acce-0000-1000-8000-00805f9b34fb");
    //private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    // MAC-адрес Bluetooth модуля
    private static String address = "DD:1E:67:BB:99:CD";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);



        btnOn = findViewById(R.id.bluetoothStart);                  // кнопка включения
        btnOff = findViewById(R.id.bluetoothStop);                // кнопка выключения
        statusText = findViewById(R.id.statusBluetooth);      // для вывода текста, полученного
        btState = findViewById(R.id.bluetoothIm);
        workState = findViewById(R.id.workStatIm);

        h = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECIEVE_MESSAGE:
                        statusText.setText("msg");// если приняли сообщение в Handler
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);
                        sb.append(strIncom);                                                // формируем строку
                        int endOfLineIndex = sb.indexOf("\r\n");                            // определяем символы конца строки
                        if (endOfLineIndex > 0) {                                            // если встречаем конец строки,
                            String sbprint = sb.substring(0, endOfLineIndex);               // то извлекаем строку
                            sb.delete(0, sb.length());                                      // и очищаем sb
                            statusText.setText("Ответ от датчика: " + sbprint);             // обновляем TextView
                            String[] step_data = sbprint.split(" ");
                            writeFile(step_data[0]);
                            writeFile(";");
                            writeFile(step_data[1]);
                            writeFile(";");
                            writeFile(step_data[2]);
                            writeFile(";\n");
                            btnOff.setEnabled(true);
                            btnOn.setEnabled(true);
                        }
                        //Log.d(TAG, "...Строка:"+ sb.toString() +  "Байт:" + msg.arg1 + "...");
                        break;
                }
            };
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();       // получаем локальный Bluetooth адаптер

        if (btAdapter.isEnabled()) {
            SharedPreferences prefs_btdev = getSharedPreferences("btdev", 0);
            String btdevaddr=prefs_btdev.getString("btdevaddr","?");

            if (btdevaddr != "?")
            {
                BluetoothDevice device = btAdapter.getRemoteDevice(btdevaddr);

                UUID SERIAL_UUID = UUID.fromString("0000f00d-1212-afde-1523-785fef13d123"); // bluetooth serial port service
                //UUID SERIAL_UUID = device.getUuids()[0].getUuid(); //if you don't know the UUID of the bluetooth device service, you can get it like this from android cache

                try {
                    btSocket = device.createRfcommSocketToServiceRecord(SERIAL_UUID);
                } catch (Exception e) {Log.e("","Error creating socket");}

                try {
                    btSocket.connect();
                    Log.e("","Connected");
                } catch (IOException e) {
                    Log.e("",e.getMessage());
                    try {
                        Log.e("","trying fallback...");

                        btSocket =(BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device,1);
                        btSocket.connect();

                        Log.e("","Connected");
                    }
                    catch (Exception e2) {
                        Log.e("", "Couldn't establish Bluetooth connection!");
                    }
                }
            }
            else
            {
                Log.e("","BT device not selected");
            }
        }

        checkBTState();

        btnOn.setOnClickListener(new OnClickListener() {        // определяем обработчик при нажатии на кнопку
            public void onClick(View v) {
                //btnOn.setEnabled(false);
                workState.setImageResource(R.drawable.ic_action_work_on);
                mConnectedThread.run();
                // TODO start writing in file
            }
        });

        btnOff.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                //btnOff.setEnabled(false);
                workState.setImageResource(R.drawable.ic_action_work_off);
                mConnectedThread.cancel();
                // TODO stop writing in file
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "...onResume - попытка соединения...");

        // Set up a pointer to the remote node using it's address.
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        // Two things are needed to make a connection:
        //   A MAC address, which we got above.
        //   A Service ID or UUID.  In this case we are using the
        //     UUID for SPP.
        try {
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
        }

        // Discovery is resource intensive.  Make sure it isn't going on
        // when you attempt to connect and pass your message.
        btAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Соединяемся...");
        try {
            btSocket.connect();
            Log.d(TAG, "...Соединение установлено и готово к передачи данных...");
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
            }
        }

        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Создание Socket...");

        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(TAG, "...In onPause()...");

        try     {
            btSocket.close();
        } catch (IOException e2) {
            errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
        }
    }

    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if(btAdapter==null) {
            btState.setImageResource(R.drawable.ic_action_off);
            errorExit("Fatal Error", "Bluetooth не поддерживается");

        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth включен...");
                btState.setImageResource(R.drawable.ic_action_on);
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(btAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    private void errorExit(String title, String message){
        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
        finish();
    }

    void writeFile(String content) {
        try {
            // отрываем поток для записи
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                    openFileOutput(FILENAME, MODE_PRIVATE)));
            // пишем данные
            bw.write(content);
            // закрываем поток
            bw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            statusText.setText("run");
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);        // Получаем кол-во байт и само собщение в байтовый массив "buffer"
                    statusText.setText("h");
                    h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();     // Отправляем в очередь сообщений Handler
                } catch (IOException e) {
                    Log.d(TAG, "exc");
                    statusText.setText(e.getMessage());
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String message) {
            Log.d(TAG, "...Данные для отправки: " + message + "...");
            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                Log.d(TAG, "...Ошибка отправки данных: " + e.getMessage() + "...");
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}