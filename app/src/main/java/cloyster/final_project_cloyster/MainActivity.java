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

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ShortBuffer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import static java.util.logging.Logger.global;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, LocationListener, SensorEventListener {

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

    private SensorManager senSensorManager;
    private Sensor senAccelerometer;
    private LocationManager locationManager;
    private int MY_PERMISSION_ACCESS_FINE_LOCATION;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int PICK_FILE_REQUEST = 1;
    private static final String TAG = MainActivity.class.getSimpleName();
    private String selectedFilePath;
    private String SERVER_URL = "http://www.austinpetrie.com/enviromod/UploadToServer.php";
    TextView browseFiles, lat, lng;
    Button bUpload, check_permission, request_permission;
    TextView tvFileName;
    ProgressDialog dialog;
    Location location;
    double latitude;
    double longitude;
    private long lastUpdate = 0;
    private float last_x, last_y, last_z;
    private static final int SHAKE_THRESHOLD = 600;


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

        // GPS
        check_permission = (Button)findViewById(R.id.check_permission);
        request_permission = (Button)findViewById(R.id.request_permission);
        check_permission.setOnClickListener(this);
        request_permission.setOnClickListener(this);
        lat = (TextView) findViewById(R.id.lat);
        lng = (TextView) findViewById(R.id.lng);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1, this);

        if (checkPermission()) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();

                lat.setText("Latitude: " + latitude);
                lng.setText("Longitude: " + longitude);
            }
        }

        // Sensor
        senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        senSensorManager.registerListener(this, senAccelerometer , SensorManager.SENSOR_DELAY_NORMAL);

        // PD
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


    // Sensor

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor mySensor = event.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            long curTime = System.currentTimeMillis();

            if ((curTime - lastUpdate) > 100) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;

                float speed = Math.abs(x + y + z - last_x - last_y - last_z)/ diffTime * 10000;
                if (speed > SHAKE_THRESHOLD) {

                }
                last_x = x;
                last_y = y;
                last_z = z;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    protected void onPause() {
        super.onPause();
        senSensorManager.unregisterListener(this);
    }

    protected void onResume() {
        super.onResume();
        senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    // GPS

    @Override
    public void onLocationChanged(Location location) {
        lat.setText("" + location.getLatitude());
        lng.setText("" + location.getLongitude());

        String msg = "New Latitude: " + location.getLatitude()
                + "New Longitude: " + location.getLongitude();

        Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onProviderDisabled(String provider) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(intent);
        Toast.makeText(getBaseContext(), "Gps is turned off!! ",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderEnabled(String provider) {
        Toast.makeText(getBaseContext(), "Gps is turned on!! ",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
    }

    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_FINE_LOCATION);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    private void requestPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
            Toast.makeText(getBaseContext(),"GPS permission allows us to access location data. Please allow in App Settings for additional functionality.",Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getBaseContext(), "Permission Granted, Now you can access location data.",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getBaseContext(), "Permission Denied, You cannot access location data.",
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public void onClick(View v) {
        if(v == check_permission) {
            if (checkPermission()) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                /*latitude = location.getLatitude();
                longitude = location.getLongitude();

                Log.i(TAG,"Lat: " + latitude);
                Log.i(TAG,"Lng: " + longitude);*/
                if (location != null) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();

                    Log.i(TAG,"Lat: " + latitude);
                    Log.i(TAG,"Lng: " + longitude);


                    lat.setText("Latitude: " + latitude);
                    lng.setText("Longitude: " + longitude);
                }

                Toast.makeText(getBaseContext(), "Permission already granted.",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getBaseContext(), "Please request permission.",
                        Toast.LENGTH_SHORT).show();
            }
        }

        if(v == request_permission) {
            if (!checkPermission()) {
                requestPermission();
            } else {
                Toast.makeText(getBaseContext(), "Permission already granted.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // PD

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

                    // Upload file to web server

                    String dir = getFilesDir().getAbsolutePath();
                    File d = new File(dir);
                    String[] files = d.list();
                    //last = files[files.length-1];
                    selectedFilePath = dir + "/" + files[files.length-1];
                    Log.i(TAG,"selectedFilePath: " + dir + "/" + selectedFilePath);
                    if(selectedFilePath != null) {
                        dialog = ProgressDialog.show(MainActivity.this,"","Uploading File...",true);

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                //creating new thread to handle Http Operations
                                uploadFile(selectedFilePath);
                            }
                        }).start();
                    } else {
                        Toast.makeText(MainActivity.this,"Please choose a File First",Toast.LENGTH_SHORT).show();
                    }
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


    //android upload file to server
    public int uploadFile(final String selectedFilePath){

        int serverResponseCode = 0;

        HttpURLConnection connection;
        DataOutputStream dataOutputStream;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";


        int bytesRead,bytesAvailable,bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;
        File selectedFile = new File(selectedFilePath);


        String[] parts = selectedFilePath.split("/");
        final String fileName = parts[parts.length-1];

        if (!selectedFile.isFile()){
            dialog.dismiss();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //tvFileName.setText("Source File Doesn't Exist: " + selectedFilePath);
                }
            });
            return 0;
        }else{
            try{
                FileInputStream fileInputStream = new FileInputStream(selectedFile);
                URL url = new URL(SERVER_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);//Allow Inputs
                connection.setDoOutput(true);//Allow Outputs
                connection.setUseCaches(false);//Don't use a cached Copy
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Connection", "Keep-Alive");
                connection.setRequestProperty("ENCTYPE", "multipart/form-data");
                connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                connection.setRequestProperty("uploaded_file",selectedFilePath);

                //creating new dataoutputstream
                dataOutputStream = new DataOutputStream(connection.getOutputStream());

                //writing bytes to data outputstream
                dataOutputStream.writeBytes(twoHyphens + boundary + lineEnd);
                dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\""
                        + selectedFilePath + "\"" + lineEnd);

                dataOutputStream.writeBytes(lineEnd);

                //returns no. of bytes present in fileInputStream
                bytesAvailable = fileInputStream.available();
                //selecting the buffer size as minimum of available bytes or 1 MB
                bufferSize = Math.min(bytesAvailable,maxBufferSize);
                //setting the buffer as byte array of size of bufferSize
                buffer = new byte[bufferSize];

                //reads bytes from FileInputStream(from 0th index of buffer to buffersize)
                bytesRead = fileInputStream.read(buffer,0,bufferSize);

                //loop repeats till bytesRead = -1, i.e., no bytes are left to read
                while (bytesRead > 0){
                    //write the bytes read from inputstream
                    dataOutputStream.write(buffer,0,bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable,maxBufferSize);
                    bytesRead = fileInputStream.read(buffer,0,bufferSize);
                }

                dataOutputStream.writeBytes(lineEnd);
                dataOutputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                serverResponseCode = connection.getResponseCode();
                String serverResponseMessage = connection.getResponseMessage();

                Log.i(TAG, "Server Response is: " + serverResponseMessage + ": " + serverResponseCode);

                //response code of 200 indicates the server status OK
                if(serverResponseCode == 200){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //tvFileName.setText("File Upload completed.\n\n You can see the uploaded file here: \n\n" + "http://austinpetrie.com/enviromod/uploads/"+ fileName);
                        }
                    });
                }

                //closing the input and output streams
                fileInputStream.close();
                dataOutputStream.flush();
                dataOutputStream.close();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,"File Not Found",Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (MalformedURLException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "URL error!", Toast.LENGTH_SHORT).show();

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Cannot Read/Write File!", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
            return serverResponseCode;
        }
    }
}
