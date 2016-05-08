package pt.ulisboa.tecnico.cmov.ubibike;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

/**
 * Created by ivanf on 08/05/2016.
 */
public class LoggedActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_logged);
    }

    public void sendMessage(View view) {
        Intent intent = new Intent(this, SendMessageActivity.class);
        startActivity(intent);
    }

    public void sendPoints(View view) {
        Intent intent = new Intent(this, SendPointsActivity.class);
        startActivity(intent);
    }

}