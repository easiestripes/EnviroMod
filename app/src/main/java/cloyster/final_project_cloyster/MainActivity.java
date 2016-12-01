package cloyster.final_project_cloyster;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void startNewRecording(View v) {
        Intent intent = new Intent(MainActivity.this, NewRecording.class);
        startActivity(intent);
    }

    public void startSavedRecordings(View v) {
        Intent intent = new Intent(MainActivity.this, SavedRecordings.class);
        startActivity(intent);
    }

    public void startHelp(View v) {
        Intent intent = new Intent(MainActivity.this, Help.class);
        startActivity(intent);
    }
}