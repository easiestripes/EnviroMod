/*
 * Libraries Used:
 * Pure Data for Android (https://github.com/libpd/pd-for-android)
 * Java Wrapper for SoundCloud API by Birdcage (https://github.com/birdcage/soundcloud-web-api-android)
 *
 * *** IMPORTANT NOTE FOR MILESTONE 2 ***
 * Prof. Sherriff told us this was fine, just to make a note of it in our code.
 * We are planning to use SoundCloud as our 3rd party web service but recently
 * they changed their policies on accepting app registration so now the process
 * a lot longer and none of their api is usable without authentication so therefore
 * we can't do any of that yet while we wait on approval. But we do have the java
 * wrapper library fully implemented and working. Also we have modified it to work
 * for uploading tracks to SoundCloud. Therefore all our SoundCloud related code
 * should work, or be very close to working, we just can't test it yet to be sure.
 */

package cloyster.final_project_cloyster;

import android.content.Context;
import android.graphics.Path;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


import org.puredata.android.io.AudioParameters;
import org.puredata.android.service.PdService;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;
import org.puredata.*;

import java.nio.ShortBuffer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import static java.util.logging.Logger.global;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Frequency Mod Synth";

    Button btnPlaySound;
    ToggleButton btnToggleSound;
    SeekBar seekbarCarrierFrequency;
    SeekBar seekbarVolume;
    SeekBar seekbarModulatorFrequency;
    SeekBar seekbarModulatorDepth;
    ToggleButton btnRecord;
    Button btnPlayRecording;
    private ToggleButton record;
    private long recStart;
    private String recFile = null;
    private static final String TRANSPORT = "#transport";
    private RecordDataBase db;
    private long recordingId;
    AudioTrack audioTrack;
    ShortBuffer mSamples; // the samples to play
    int mNumSamples; // number of samples to play
    boolean mShouldContinue;

    public static final String RECORDING_PATH = "recording_path";
    private File folder;
    private File recDir = null;
    private String refFile = null;
    int fileNum=1;
    EditText editText;
    String title;


    /**
     * The PdService is provided by the pd-for-android library.
     */
    private PdService pdService = null;

    /**
     * The volume value as integer from 0 to 100 percent
     */
    int volume = 0;

    /**
     * Initialises the pure data service for playing audio and receiving control commands.
     */
    private final ServiceConnection pdConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            pdService = ((PdService.PdBinder)service).getService();
            initPd();

            try {
                int sampleRate = AudioParameters.suggestSampleRate();
                pdService.initAudio( sampleRate, 0, 2, 8 );
                pdService.startAudio();
            } catch (IOException e) {
                toast(e.toString());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            pdService.stopAudio();
        }
    };

    /**
     * Initialises the pure data audio interface and loads the patch file packaged within the app.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void initPd() {
        File patchFile = null;
        try {
            PdBase.setReceiver(new PdUiDispatcher());
            PdBase.subscribe("android");
            File dir = getFilesDir();
            IoUtils.extractZipResource( getResources().openRawResource( R.raw.fm ), dir, true );
            patchFile = new File( dir, "fm.pd" );
            PdBase.openPatch( patchFile.getAbsolutePath() );
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            finish();
        } finally {
            if (patchFile != null) {
                patchFile.delete();
            }
        }
    }

    private void post(final String msg) {
        Log.i(TAG, msg);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        setContentView(R.layout.activity_main);

        String recDirPath = intent.getStringExtra(RECORDING_PATH);
        recDir = new File(recDirPath != null ? recDirPath : getResources().getString(R.string.recording_folder));
        if (recDir.isFile() || (!recDir.exists() && !recDir.mkdirs())) recDir = null;

        AudioParameters.init(this);
        bindService(new Intent(this, PdService.class), pdConnection, BIND_AUTO_CREATE);
        initGui();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unbindService(pdConnection);
        } catch (IllegalArgumentException e) {
            pdService = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        getMenuInflater().inflate(R.menu.menu_main, menu );
        return true;
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
        int id = item.getItemId();

        if ( id == R.id.action_link ) {
            Intent browserIntent = new Intent( Intent.ACTION_VIEW, Uri.parse( "http://www.journal.deviantdev.com/example-libpd-android-studio/" ) );
            startActivity( browserIntent );
            return true;
        }

        if ( id == R.id.action_exit ) {
            moveTaskToBack( true );
            return true;
        }

        return super.onOptionsItemSelected( item );
    }

    /**
     * Initialises the user interface elements and necessary handlers responsibly for the interaction with the
     * pre-loaded pure data patch. The code is really pure data patch specific.
     */
    private void initGui() {
        // touch to play button
        this.btnPlaySound = (Button) findViewById( R.id.buttonPlaySound );
        this.btnPlaySound.setOnTouchListener( new View.OnTouchListener() {
            @Override
            public boolean onTouch( View v, MotionEvent event ) {
                if ( event.getAction() == MotionEvent.ACTION_DOWN ) {
                    PdBase.sendFloat( "osc_volume", volume / 100f ); // send volume (0 to 1)
                } else if ( event.getAction() == MotionEvent.ACTION_UP ) {
                    PdBase.sendFloat( "osc_volume", 0 ); // quiet down
                }
                return false;
            }
        } );

        editText = (EditText)findViewById(R.id.editText);
        /*editText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    title = editText.getText().toString().trim();
                    return true;
                }
                return false;
            }
        });
        */

        // touch to play back recording
        this.btnPlayRecording = (Button) findViewById( R.id.buttonPlayRecording );
        this.btnPlayRecording.setOnTouchListener( new View.OnTouchListener() {
            @Override
            public boolean onTouch( View v, MotionEvent event ) {

                    //PdBase.sendFloat("osc_volume", 0);
                    String dir = getFilesDir().getAbsolutePath();
                    PdBase.sendSymbol("readFile", title);
                    PdBase.sendBang("readBang");
                    PdBase.sendBang("readStart");
                    //fileNum++;
                    return false;
            }
        } );

        // toggle play button
        this.btnToggleSound = (ToggleButton) findViewById( R.id.buttonToggleSound );
        this.btnToggleSound.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ) {

                if ( btnPlaySound.isEnabled() ) {
                    btnPlaySound.setEnabled( false );
                    PdBase.sendFloat( "osc_volume", volume / 100f ); // enable volume while locked
                } else {
                    btnPlaySound.setEnabled( true );
                    PdBase.sendFloat( "osc_volume", 0 ); // quiet down for after unlock
                }
            }
        } );

        // Toggle record button
        this.btnRecord = (ToggleButton) findViewById( R.id.buttonToggleRecord);
        this.btnRecord.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ) {
                if (isChecked ) {
                    title = "hello";
                    title = editText.getText().toString();
                    String dir = getFilesDir().getAbsolutePath();
                    PdBase.sendSymbol("writeFile", title); // open file
                    PdBase.sendBang("writeStart");
                    File d = new File(dir);
                    String[] files = d.list();
                    post(dir);
                    for (int i = 0; i < files.length; i++){
                        post(i + ": " + files[i]);
                    }

                }
                else {
                    PdBase.sendBang("writeStop"); // stop recording otherwise
            }
        }
        });

        // seekbar for volume
        this.seekbarVolume = (SeekBar) findViewById( R.id.seekbarVolume );
        this.seekbarVolume.setMax( 20 );
        this.seekbarVolume.incrementProgressBy( 1 );
        this.seekbarVolume.setProgress( 0 );
        this.seekbarVolume.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
                volume = progress;
                if ( btnToggleSound.isChecked() ) {
                    PdBase.sendFloat( "osc_volume", volume / 100f ); // send volume (0 to 1) if locked
                }
            }

            public void onStartTrackingTouch( SeekBar seekBar ) {}

            public void onStopTrackingTouch( SeekBar seekBar ) {}
        } );

        // seekbar for carrier frequency
        this.seekbarCarrierFrequency = (SeekBar) findViewById( R.id.seekbarCarrierFrequency );
        this.seekbarCarrierFrequency.setMax( 100 );
        this.seekbarCarrierFrequency.incrementProgressBy( 1 );
        this.seekbarCarrierFrequency.setProgress( 0 );
        this.seekbarCarrierFrequency.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
                if ( progress == 0 ) progress = 1;
                float a = progress / 100f;
                float frequency = (float) ( 2500 * Math.exp( 2.19722 * a ) - 2500 );
                PdBase.sendFloat( "osc_carrier_frequency", frequency );
            }

            public void onStartTrackingTouch( SeekBar seekBar ) {}

            public void onStopTrackingTouch( SeekBar seekBar ) {}
        } );

        // seekbar for modulator frequency
        this.seekbarModulatorFrequency = (SeekBar) findViewById( R.id.seekbarModulatorFrequency );
        this.seekbarModulatorFrequency.setMax( 20 );
        this.seekbarModulatorFrequency.incrementProgressBy( 1 );
        this.seekbarModulatorFrequency.setProgress( 0 );
        this.seekbarModulatorFrequency.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
                if ( progress == 0 ) progress = 1;
                float a = progress / 100f;
                float frequency = (float) ( 2500 * Math.exp( 2.19722 * a ) - 2500 );
                PdBase.sendFloat( "osc_modulator_frequency", frequency );
            }

            public void onStartTrackingTouch( SeekBar seekBar ) {}

            public void onStopTrackingTouch( SeekBar seekBar ) {}
        } );

        // seekbar for modulator depth
        this.seekbarModulatorDepth = (SeekBar) findViewById( R.id.seekbarModulatorDepth );
        this.seekbarModulatorDepth.setMax( 100 );
        this.seekbarModulatorDepth.incrementProgressBy( 1 );
        this.seekbarModulatorDepth.setProgress( 0 );
        this.seekbarModulatorDepth.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
                if ( progress == 0 ) progress = 1;
                float a = progress / 100f;
                float depth = (float) ( 2500 * Math.exp( 2.19722 * a ) - 2500 );
                PdBase.sendFloat( "osc_modulator_depth", depth );
            }

            public void onStartTrackingTouch( SeekBar seekBar ) {}

            public void onStopTrackingTouch( SeekBar seekBar ) {}
        } );


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if ( fab != null ) {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("message/rfc822");
                    i.putExtra(Intent.EXTRA_EMAIL  , new String[]{"contact@deviantdev.com"});
                    i.putExtra( Intent.EXTRA_SUBJECT, "Mail to the Author...");
                    i.putExtra(Intent.EXTRA_TEXT   , "Thanks for all the fish!");
                    try {
                        startActivity(Intent.createChooser(i, "Send mail..."));
                    } catch (android.content.ActivityNotFoundException ex) {
                        Toast.makeText(MainActivity.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }


    /**
     * Trigger a native Android toast message.
     * @param text
     */
    private void toast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(getApplicationContext(), "", Toast.LENGTH_SHORT);
                toast.setText(TAG + ": " + text);
                toast.show();
            }
        });
    }
}
