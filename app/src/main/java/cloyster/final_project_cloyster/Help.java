package cloyster.final_project_cloyster;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.InputStream;

public class Help extends AppCompatActivity {

    Button home;
    TextView helpFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        TextView helpFile = (TextView) findViewById(R.id.helpFile);
        setSupportActionBar(toolbar);

        try {
            Resources res = getResources();
            InputStream in_s = res.openRawResource(R.raw.help);

            byte[] b = new byte[in_s.available()];
            in_s.read(b);
            String s = new String(b);
            helpFile.setText((Html.fromHtml(s)));
            //helpFile.setText(new String(b));
        } catch (Exception e) {
            // e.printStackTrace();
            helpFile.setText("Error: can't show help.");
        }

        home = (Button) findViewById (R.id.help_to_home);
        home.setOnTouchListener( new View.OnTouchListener() {
            @Override
            public boolean onTouch( View v, MotionEvent event ) {
                finish();
                return false;
            }
        } );
    }



}
