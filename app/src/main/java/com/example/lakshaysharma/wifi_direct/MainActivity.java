package com.example.lakshaysharma.wifi_direct;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Button btnOnOff, btnDiscover, btnSend;
    private ListView listView, read_msg_box;
    TextView connectionStatus;
    private EditText messageToSend;

    WifiManager wifiManager;
    WifiP2pManager wifiP2pManager;
    WifiP2pManager.Channel channel;

    BroadcastReceiver broadcastReceiver;
    IntentFilter intentFilter;

    List<WifiP2pDevice> peers = new ArrayList<>();
    String[] deviceName;
    WifiP2pDevice[] wifiDevices;

    ArrayList<String> messages;
    ArrayAdapter<String> messageAdapter;

    private final int Message_Read = 1;

    ServerClass serverClass;
    ClientClass clientClass;
    SendRecieve sendRecieve;

    private boolean isServer = false;
    private boolean isClient = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT > 15)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        wifiManager = (WifiManager) getApplicationContext().getSystemService(this.WIFI_SERVICE);
        wifiP2pManager = (WifiP2pManager) getSystemService(this.WIFI_P2P_SERVICE);
        channel =wifiP2pManager.initialize(this, getMainLooper(), null);
        broadcastReceiver = new WifiBroadcastReceiver(wifiP2pManager, channel, this);
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        UIInit();
        listViewListener();
    }

    private void listViewListener() {

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                final WifiP2pDevice device = wifiDevices[position];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;

                wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Connected To " + device.deviceName, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailure(int reason) {
                        Toast.makeText(MainActivity.this, "Connection Failure With " + device.deviceName, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void UIInit() {

        btnOnOff = findViewById(R.id.onOff);
        btnSend = findViewById(R.id.sendButton);
        btnDiscover = findViewById(R.id.discover);
        listView = findViewById(R.id.peerListView);
        read_msg_box = findViewById(R.id.readMsg);
        connectionStatus = findViewById(R.id.connectionStatus);
        messageToSend = findViewById(R.id.writeMsg);

        messages = new ArrayList<>();
        messageAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, messages);
        read_msg_box.setAdapter(messageAdapter);

        toggleWiFi();
    }

    public void toggleWiFi() {

        btnOnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (wifiManager.isWifiEnabled()){
                    wifiManager.setWifiEnabled(false);
                    btnOnOff.setText(R.string.wifiOn);
                }
                else {
                    wifiManager.setWifiEnabled(true);
                    btnOnOff.setText(R.string.wifiOff);
                }
            }
        });
    }

    public void discoverPeers(View view) {
        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                connectionStatus.setText("Discovering....");
            }

            @Override
            public void onFailure(int reason) {
                connectionStatus.setText("Discovering....");
            }
        });
    }

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peersList) {
            if(!peersList.getDeviceList().equals(peers)){
                peers.clear();
                peers.addAll(peersList.getDeviceList());

                deviceName = new String[peersList.getDeviceList().size()];
                wifiDevices = new WifiP2pDevice[peersList.getDeviceList().size()];

                int index = 0;

                for(WifiP2pDevice device: peersList.getDeviceList()){
                    deviceName[index] = device.deviceName;
                    wifiDevices[index] = device;
                    index++;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, deviceName);

                listView.setAdapter(adapter);
            }

            if(peers.size() == 0){
                Toast.makeText(getApplicationContext(), "No Devices Found", Toast.LENGTH_LONG).show();
            }
        }
    };

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {

            if (info.groupFormed && info.isGroupOwner){
                connectionStatus.setText("Host");
                isServer = true;
                serverClass = new ServerClass();
                serverClass.start();
            }
            else if (info.groupFormed){
                connectionStatus.setText("Client");
                isClient = true;
                clientClass = new ClientClass(info.groupOwnerAddress);
                clientClass.start();
            }
        }
    };

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what){

                case Message_Read:
                    byte[] readBuffer = (byte[]) msg.obj;
                    String tempMsg = new String(readBuffer, 0, msg.arg1);
                    updateMessageList(tempMsg);
                    break;
            }
            return true;
        }
    });

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
    }

    public void sendMessage(View view) {

        String message = messageToSend.getText().toString().trim();
        if (isServer){
            sendRecieve.write(("SERVER: " + message).getBytes());
            updateMessageList("SERVER: " + message);
        }
        else if (isClient){
            sendRecieve.write(("CLIENT: " + message).getBytes());
            updateMessageList("CLIENT: " + message);
        }


        messageToSend.setText("");
    }

    public void updateMessageList(String msg){

        messages.add(msg);
        messageAdapter.notifyDataSetChanged();
    }

    public class ServerClass extends Thread{

        private ServerSocket serverSocket;
        private Socket socket;

        private final int PORT = 3000;

        @Override
        public void run() {

            try {

                serverSocket = new ServerSocket(PORT);
                socket = serverSocket.accept();

                sendRecieve = new SendRecieve(socket);
                sendRecieve.start();

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public class SendRecieve extends Thread{

        private Socket socket;
        private InputStream dataInputStream;
        private OutputStream dataOutputStream;

        public SendRecieve(Socket socket){

            this.socket = socket;
            try {
                dataInputStream = socket.getInputStream();
                dataOutputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                e.printStackTrace();
            }
        }

        @Override
        public void run() {

            while(socket != null){

                byte[] buffer = new byte[1024];
                int bytes;

                while (socket != null){
                    try {
                        bytes = dataInputStream.read(buffer);
                        if (bytes > 0){
                            handler.obtainMessage(Message_Read, bytes, -1, buffer).sendToTarget();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public void write(byte[] bytes){

            try {
                dataOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ClientClass extends Thread{

        Socket socket;
        String hostAdd;

        public ClientClass(InetAddress hostAddress){

            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run() {

            try {

                socket.connect(new InetSocketAddress(hostAdd, 3000), 500);

                sendRecieve = new SendRecieve(socket);
                sendRecieve.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
