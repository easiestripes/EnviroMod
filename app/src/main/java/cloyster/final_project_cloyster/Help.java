package cloyster.final_project_cloyster;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import java.io.InputStream;

public class Help extends AppCompatActivity {

    TextView helpFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        TextView helpFile = (TextView) findViewById(R.id.helpFile);
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
            helpFile.setText("Error: Can't show help.");
        }
    }

    public void back(View v) {
        finish();
    }
}
