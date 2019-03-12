package com.example.lakshaysharma.wifi_direct;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class Welcom extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcom);
    }

    public void wifiActivity(View view) {

        startActivity(new Intent(this, MainActivity.class));
    }

    public void btActivity(View view) {

        startActivity(new Intent(this, BtChat.class));
    }
}
