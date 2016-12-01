package cloyster.final_project_cloyster;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


public class SavedRecordings extends AppCompatActivity {
    private Button backward, play, forward;
    private MediaPlayer mediaPlayer;

    private double startTime = 0;
    private double finalTime = 0;

    private Handler myHandler = new Handler();;
    private int forwardTime = 5000;
    private int backwardTime = 5000;
    private SeekBar seekbar;
    private TextView start_time, end_time, song_title;

    public ArrayAdapter recordList_adapter;
    private ArrayList<String> recordList_files;
    private String selectedFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_recordings);

        backward = (Button) findViewById(R.id.backward);
        play = (Button)findViewById(R.id.play);
        forward = (Button)findViewById(R.id.forward);

        start_time = (TextView)findViewById(R.id.start_time);
        end_time = (TextView)findViewById(R.id.end_time);
        song_title = (TextView)findViewById(R.id.song_title);

        seekbar = (SeekBar)findViewById(R.id.seekBar);
        seekbar.setClickable(false);

        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    play.setBackgroundResource(R.drawable.play);
                } else {
                    play();
                }
            }
        });

        forward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int temp = (int)startTime;

                if((temp+forwardTime)<=finalTime){
                    startTime = startTime + forwardTime;
                    mediaPlayer.seekTo((int) startTime);
                }else{
                    // can't jump forward 5 seconds
                }
            }
        });

        backward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int temp = (int)startTime;

                if((temp-backwardTime)>0){
                    startTime = startTime - backwardTime;
                    mediaPlayer.seekTo((int) startTime);
                }else{
                    // can't jump backward 5 seconds
                }
            }
        });

        // recording list
        String dir = getFilesDir().getAbsolutePath();
        File d = new File(dir);
        String[] allFiles = d.list();;
        recordList_files = new ArrayList<String>();
        String temp = "";
        for (int i = 0; i < allFiles.length; i++) {
            temp = allFiles[i];
            if (temp.contains(".wav")) {
                recordList_files.add(temp.substring(0, temp.length()-4));
            }
        }

        recordList_adapter = new ArrayAdapter<String>(this, R.layout.recording_list_layout, recordList_files);
        ListView recordingListView = (ListView) findViewById(R.id.recordingList);
        recordingListView.setAdapter(recordList_adapter);

        // Initialize MediaPlayer
        if(recordList_files.size() > 0) {
            mediaPlayer = new MediaPlayer();

            String title = recordList_files.get(0);
            selectedFilePath = dir + "/" + title + ".wav";

            try {
                mediaPlayer.setDataSource(selectedFilePath);
                mediaPlayer.prepare();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            mediaPlayer = null;
        }
    }

    public void back(View v) {
        finish();
    }

    public void loadSong(View v) {
        int pos = ((ListView) findViewById(R.id.recordingList)).getPositionForView(v);
        String title = recordList_files.get(pos);
        song_title.setText(title);

        String dir = getFilesDir().getAbsolutePath();
        selectedFilePath = dir + "/" + title + ".wav";

        if(mediaPlayer == null)
            mediaPlayer = new MediaPlayer();

        mediaPlayer.reset();

        try {
            mediaPlayer.setDataSource(selectedFilePath);
            mediaPlayer.prepare();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        play();
    }

    public void play() {
        mediaPlayer.start();

        play.setBackgroundResource(R.drawable.pause);

        finalTime = mediaPlayer.getDuration();
        startTime = mediaPlayer.getCurrentPosition();

        seekbar.setMax((int) finalTime);

        end_time.setText(String.format("%d min %d sec",
                TimeUnit.MILLISECONDS.toMinutes((long) finalTime),
                TimeUnit.MILLISECONDS.toSeconds((long) finalTime) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long)
                                finalTime)))
        );

        start_time.setText(String.format("%d min %d sec",
                TimeUnit.MILLISECONDS.toMinutes((long) startTime),
                TimeUnit.MILLISECONDS.toSeconds((long) startTime) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long)
                                startTime)))
        );

        seekbar.setProgress((int) startTime);
        myHandler.postDelayed(UpdateSongTime, 100);
    }

    private Runnable UpdateSongTime = new Runnable() {
        public void run() {
            startTime = mediaPlayer.getCurrentPosition();
            start_time.setText(String.format("%d min %d sec",
                    TimeUnit.MILLISECONDS.toMinutes((long) startTime),
                    TimeUnit.MILLISECONDS.toSeconds((long) startTime) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.
                                    toMinutes((long) startTime)))
            );
            seekbar.setProgress((int)startTime);
            myHandler.postDelayed(this, 100);

            if(TimeUnit.MILLISECONDS.toMinutes((long) startTime) == TimeUnit.MILLISECONDS.toMinutes((long) finalTime)) {
                if (TimeUnit.MILLISECONDS.toSeconds((long) startTime) >= TimeUnit.MILLISECONDS.toSeconds((long) finalTime)-1) {
                    play.setBackgroundResource(R.drawable.play);
                }
            }
        }
    };
}
