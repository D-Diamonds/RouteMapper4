package com.example.routemapper4;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button startButton;

    @Override
    public void onClick(View v) {
        if (v.getId() == startButton.getId()) {
            Intent mapActivity = new Intent(this, MapsActivity.class);
            startActivity(mapActivity);
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.startButton = findViewById(R.id.startButton);
        this.startButton.setOnClickListener(this);
    }
}
