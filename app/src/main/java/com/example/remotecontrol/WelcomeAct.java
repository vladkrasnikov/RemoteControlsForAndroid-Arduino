package com.example.remotecontrol;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class WelcomeAct extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome_page);
    }

    public void onstartMove(View v) {
        Intent intent = new Intent(this,MainActivity.class);
        startActivity(intent);
    }
}
