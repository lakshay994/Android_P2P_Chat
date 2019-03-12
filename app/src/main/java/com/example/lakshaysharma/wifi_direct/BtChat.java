package com.example.lakshaysharma.wifi_direct;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class BtChat extends AppCompatActivity {

    private ListView listView, readMsg;
    private TextView status;
    private EditText writeMsg;

    private boolean isServer;
    private boolean isClient;

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] bluetoothDevices;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTON_FAILED = 4;
    static final int MESSAGE_RECEIVED = 5;

    private int REQUEST_ENABLE_BLUETOOTH = 1;

    SendReceive sendReceive;

    private ArrayList<String> messages;
    ArrayAdapter<String> messageAdapter;

    private static final String APP_NAME = "G14";
    private static final UUID BT_UUID = UUID.fromString("8ce255c0-223a-11e0-ac64-0803450c9a66");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bt_chat);

        UIInit();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(!bluetoothAdapter.isEnabled()){

            Intent enableIntent = new Intent(bluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }
    }

    private void UIInit() {

        listView = findViewById(R.id.peerListView);
        readMsg = findViewById(R.id.readMsg);
        status = findViewById(R.id.connectionStatus);
        writeMsg = findViewById(R.id.writeMsg);

        messages = new ArrayList<>();
        messageAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, messages);
        readMsg.setAdapter(messageAdapter);

        listViewListener();

    }

    private void listViewListener() {

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                ClientClass clientClass = new ClientClass(bluetoothDevices[position]);
                clientClass.start();
                isClient = true;
                status.setText("Connecting");
            }
        });
    }

    public void listDevices(View view) {

        Set<BluetoothDevice> bt = bluetoothAdapter.getBondedDevices();
        String[] deviceNames = new String[bt.size()];
        bluetoothDevices = new BluetoothDevice[bt.size()];

        int index = 0;
        if (bt.size() > 0){
            for(BluetoothDevice device: bt){
                bluetoothDevices[index] = device;
                deviceNames[index] = device.getName();
                index++;
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
            listView.setAdapter(adapter);
        }
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what){
                case STATE_LISTENING:
                    status.setText("Listening....");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting....");
                    break;
                case STATE_CONNECTED:
                    status.setText("Connected");

                    break;
                case STATE_CONNECTON_FAILED:
                    status.setText("Connection Failed");
                    break;
                case MESSAGE_RECEIVED:
                    byte[] readBuffer = (byte[]) msg.obj;
                    String tempMsg = new String(readBuffer, 0, msg.arg1);
                    updateMessages(tempMsg);
                    break;
            }

            return true;
        }
    });

    public void listen(View view) {

        ServerClass serverClass = new ServerClass();
        serverClass.start();
        isServer = true;
    }

    public void sendMessage(View view) {

        String message = writeMsg.getText().toString().trim();
        if (isServer){
            sendReceive.write(("SERVER: " + message).getBytes());
            updateMessages("SERVER: " + message);
        }
        else if (isClient){
            sendReceive.write(("CLIENT: " + message).getBytes());
            updateMessages("CLIENT: " + message);
        }
        writeMsg.setText("");
    }

    public void updateMessages(String msg){

        messages.add(msg);
        messageAdapter.notifyDataSetChanged();

    }

    public class ServerClass extends Thread{

        private BluetoothServerSocket serverSocket;

        public ServerClass(){
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, BT_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {

            BluetoothSocket socket = null;

            while(socket == null){
                try {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTING;
                    handler.sendMessage(message);
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTON_FAILED;
                    handler.sendMessage(message);
                }

                if (socket!= null){
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTED;
                    handler.sendMessage(message);

                    sendReceive = new SendReceive(socket);
                    sendReceive.start();

                    break;
                }
            }


        }
    }

    public class ClientClass extends Thread{

        private BluetoothDevice device;
        private BluetoothSocket socket;

        public ClientClass(BluetoothDevice device){

            this.device = device;
            try {
                socket = device.createRfcommSocketToServiceRecord(BT_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {

            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                handler.sendMessage(message);

                sendReceive = new SendReceive(socket);
                sendReceive.start();

            } catch (IOException e) {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTON_FAILED;
                handler.sendMessage(message);
            }
        }
    }

    public class SendReceive extends Thread{

        private BluetoothSocket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public SendReceive(BluetoothSocket socket){

            this.socket = socket;

            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void run() {

            byte[] buffer = new byte[1024];
            int bytes;

            while (true){
                try {

                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes){

            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
