/*
 * Libraries Used:
 * Pure Data for Android (https://github.com/libpd/pd-for-android)
 * Server Upload: http://www.coderefer.com/android-upload-file-to-server/
 * Location: https://www.learn2crack.com/2015/10/android-marshmallow-permissions.html
 * Accelerometer: https://code.tutsplus.com/tutorials/using-the-accelerometer-on-android--mobile-22125
 * Audio Controls: https://github.com/arunkumarsekar/audioControls
 * AM/FM Image: http://www-tc.pbs.org/wgbh/aso/tryit/radio/images/fmamcompare.gif
 * Gif library: https://github.com/koral--/android-gif-drawable
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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.Switch;

import org.puredata.android.io.AudioParameters;
import org.puredata.android.service.PdService;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener {

    Button btnPlaySound;
    ToggleButton btnToggleSound;
    ToggleButton btnRecord;
    Switch switch_am_fm;
    TextView ModulatorFrequency;
    TextView ModulatorDepth;
    TextView CarrierFrequency;
    Button help;

    float am_volume = 15/100f;
    float fm_volume = 15/100f;
    double carrierMin = 220.0;
    double carrierMax = 2000.0;
    double modulatorMin = 0.0;
    double modulatorMax = 2000.0;
    double amModMax = 40.0;
    float amDepthLimit = .5f;
    float fmDepthLimit = 50f;
    int lat = 0;
    int lon = 0;
    double subC = 0.0;
    double subM = 0.0;
    double dep = 0.0;


    public static final String RECORDING_PATH = "recording_path";
    private File recDir = null;
    EditText editText;
    String title;
    public ArrayAdapter recordList_adapter;
    ArrayList<String> recordList_files;

    private SensorManager senSensorManager;
    private Sensor senAccelerometer;
    private LocationManager locationManager;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = MainActivity.class.getSimpleName();
    private String selectedFilePath;
    private String SERVER_URL = "http://www.austinpetrie.com/enviromod/UploadToServer.php";

    ProgressDialog dialog;
    Location location;
    double latitude;
    double longitude;
    private long lastUpdate = 0;
    private float last_x, last_y, last_z;

    /**
     * The PdService is provided by the pd-for-android library.
     */
    private PdService pdService = null;

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

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1, this);

        if (checkPermission()) {
            location = getLoc();
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                lat = (int) latitude;
                lon = (int) longitude;
                subC = Math.abs(latitude - lat);
                subM = Math.abs(longitude - lon);
                dep = (subC + subM);
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

                last_x = x;
                last_y = y;
                last_z = z;

                float carFrequency = (float) (Math.abs(carrierMax*subC*last_x) + carrierMin);
                float fmModFrequency = (float) (Math.abs(modulatorMax*subM*last_y) + modulatorMin);
                float amModFrequency = (float) (Math.abs(amModMax*subM*last_y) + modulatorMin);

                float amDepth = (float) Math.abs(dep*amDepthLimit*last_z);
                float fmDepth = (float) Math.abs(dep*fmDepthLimit*last_z);

                if (switch_am_fm.isChecked()){
                    PdBase.sendFloat( "fm_carrier_frequency", carFrequency);
                    PdBase.sendFloat( "fm_modulator_frequency", fmModFrequency );
                    PdBase.sendFloat( "fm_modulator_depth", fmDepth );
                    CarrierFrequency.setText("" + carFrequency);
                    ModulatorFrequency.setText("" + fmModFrequency);
                    ModulatorDepth.setText("" + fmDepth);
                }
                else {
                    PdBase.sendFloat( "am_carrier_frequency", carFrequency);
                    PdBase.sendFloat( "am_modulator_frequency", amModFrequency );
                    PdBase.sendFloat( "am_modulator_depth", amDepth );
                    CarrierFrequency.setText("" + carFrequency);
                    ModulatorFrequency.setText("" + amModFrequency);
                    ModulatorDepth.setText("" + amDepth);
                }
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
    private Location getLoc() {
        locationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);
        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            if(checkPermission()) {
                Location l = locationManager.getLastKnownLocation(provider);

                if (l == null) {
                    continue;
                }
                if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                    // Found best last known location: %s", l);
                    bestLocation = l;
                }
            }
        }
        return bestLocation;
    }


    @Override
    public void onLocationChanged(Location location) {
        String msg = "New Latitude: " + location.getLatitude()
                + "New Longitude: " + location.getLongitude();

        latitude = location.getLatitude();
        longitude = location.getLongitude();
        lat = (int) latitude;
        lon = (int) longitude;
        subC = Math.abs(latitude - lat);
        subM = Math.abs(longitude - lon);
        dep = (subC + subM);

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


    public void openHelp(View v){
        Intent i = new Intent(MainActivity.this, Help.class);
        startActivity(i);
    }

    // PD

    /**
     * Initialises the user interface elements and necessary handlers responsibly for the interaction with the
     * pre-loaded pure data patch. The code is really pure data patch specific.
     */
    private void initGui() {

        this.ModulatorDepth = (TextView) findViewById(R.id.ModulatorDepth);
        this.ModulatorFrequency = (TextView) findViewById(R.id.ModulatorFrequency);
        this.CarrierFrequency = (TextView) findViewById(R.id.CarrierFrequency);

        PdBase.sendFloat("am_volume", am_volume);
        PdBase.sendFloat("fm_volume", fm_volume);

        this.switch_am_fm = (Switch) findViewById(R.id.switch_am_fm);
        this.switch_am_fm.setChecked(false);

        this.switch_am_fm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (btnToggleSound.isChecked()) {
                    if (isChecked) {
                        PdBase.sendFloat("am_volume", 0);
                        PdBase.sendFloat("fm_volume", fm_volume);
                    } else {
                        PdBase.sendFloat("fm_volume", 0);
                        PdBase.sendFloat("am_volume", am_volume);
                    }
                }
            }
        });

        // touch to play button
        this.btnPlaySound = (Button) findViewById( R.id.buttonPlaySound );
        this.btnPlaySound.setOnTouchListener( new View.OnTouchListener() {
            @Override
            public boolean onTouch( View v, MotionEvent event ) {
                if ( event.getAction() == MotionEvent.ACTION_DOWN ) {
                    if (switch_am_fm.isChecked()){
                        PdBase.sendFloat( "fm_volume", fm_volume ); // send volume (0 to 1)
                    }
                    else{
                        PdBase.sendFloat( "am_volume", am_volume  ); // send volume (0 to 1)
                    }

                } else if ( event.getAction() == MotionEvent.ACTION_UP ) {
                        PdBase.sendFloat( "fm_volume", 0 ); // quiet down
                        PdBase.sendFloat("am_volume", 0); // quiet down
                }
                return false;
            }
        } );

        editText = (EditText)findViewById(R.id.editText);
        editText.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                if(s.length() != 0){
                    btnRecord.setEnabled(true);
                }
                else{
                    btnRecord.setEnabled(false);
                }
            }
        });

        if (checkPermission()){
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();

                Log.i(TAG,"Lat: " + latitude);
                Log.i(TAG,"Lng: " + longitude);
            }
        }
        else{
            requestPermission();
        }

        String dir = getFilesDir().getAbsolutePath();
        File d = new File(dir);
        String[] allFiles = d.list();
        recordList_files = new ArrayList<String>();
        for (int i = 0; i < allFiles.length; i++){
            if (allFiles[i].contains(".wav")){
                recordList_files.add(allFiles[i]);
            }
        }

        recordList_adapter = new ArrayAdapter<String>(this, R.layout.recording_list_layout, recordList_files);

        // toggle play button
        this.btnToggleSound = (ToggleButton) findViewById( R.id.buttonToggleSound );
        this.btnToggleSound.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ) {
                if ( btnPlaySound.isEnabled() ) {
                    btnPlaySound.setEnabled( false );
                    if(switch_am_fm.isChecked()){
                        PdBase.sendFloat( "fm_volume", fm_volume ); // enable volume while locked
                    }
                    else{
                        PdBase.sendFloat("am_volume", am_volume );
                    }

                } else {
                    btnPlaySound.setEnabled( true );
                        PdBase.sendFloat( "fm_volume", 0 ); // quiet down for after unlock
                        PdBase.sendFloat("am_volume", 0);
                }
            }
        } );

        // Toggle record button
        this.btnRecord = (ToggleButton) findViewById( R.id.buttonToggleRecord);
        this.btnRecord.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ) {
                if (isChecked ) {
                        title = "test";
                        title = editText.getText().toString();
                        String dir = getFilesDir().getAbsolutePath();
                        PdBase.sendSymbol("writeFile", title); // open file
                        PdBase.sendBang("writeStart");
                        File d = new File(dir);
                        String[] files = d.list();
                        post(dir);
                        for (int i = 0; i < files.length; i++) {
                            post(i + ": " + files[i]);
                        }
                }
                else {
                    PdBase.sendBang("writeStop"); // stop recording otherwise

                    String dir = getFilesDir().getAbsolutePath();
                    File d = new File(dir);
                    String[] files = d.list();
                    recordList_files.add(files[files.length-1]);
                    recordList_adapter.notifyDataSetChanged();
                    editText.setText("");

                    // Upload file to web server

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
                        toast("Upload Complete!");
                    } else {
                        Toast.makeText(MainActivity.this,"Please choose a File First",Toast.LENGTH_SHORT).show();
                    }
            }
        }
        });
    }

    /**
     * Trigger a native Android toast message.
     * @param text
     */
    private void toast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(getApplicationContext(), "", Toast.LENGTH_LONG);
                toast.setText(text);
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
