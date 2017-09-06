package com.aykuttasil.callrecorder.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.aykuttasil.callrecorder.BuildConfig;
import com.aykuttasil.callrecorder.R;
import com.aykuttasil.callrecorder.utils.SongsManager;
import com.aykuttasil.callrecorder.utils.Utilities;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by tarun on 11/03/17.
 */

public class MusicPlayerDialog extends DialogFragment implements MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener{
    private TextView titleView;
    private ImageButton btnPlay;
    private ImageButton btnForward;
    private ImageButton btnBackward;
    private SeekBar songProgressBar;
    MusicPlayerDialogListener _listener;
    private TextView songCurrentDurationLabel;
    private TextView songTotalDurationLabel;
    private MediaPlayer mp;
    // Handler to update UI timer, progress bar etc,.
    private Handler mHandler = new Handler();;
    private SongsManager songManager;
    private Utilities utils;
    private int seekForwardTime = 5000; // 5000 milliseconds
    private int seekBackwardTime = 5000; // 5000 milliseconds
    private int currentSongIndex = 0;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    private ArrayList<HashMap<String, String>> songsList = new ArrayList<HashMap<String, String>>();
    private String mFilePath, mFileName;
    private int mDrawable;
    private ImageView imageView;
    private int mPosition;
    private Context mContext;
    private String mFileUri;

    public interface MusicPlayerDialogListener {
        void onMusicEditClick(int mPosition);
    }

    public MusicPlayerDialog() {
    }

    @Override public void onAttach(Context context) {
        super.onAttach(context);
        //_listener = (MusicPlayerDialogListener) context;
        mContext = context;
    }

    public static MusicPlayerDialog newInstance(String title, String path, String c, int type) {
        MusicPlayerDialog frag = new MusicPlayerDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("path", path);
        args.putString("fileUri", c);
        args.putInt("icon", type);
        args.putInt("position", 0);
        frag.setArguments(args);
        return frag;
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFileName = getArguments().getString("title");
        mFilePath = getArguments().getString("path");
        mFileUri = getArguments().getString("fileUri");
        mDrawable = getArguments().getInt("icon");
        mPosition = getArguments().getInt("position");
        if(BuildConfig.DEBUG)
            Log.e("tarun", "Options:" + mFilePath + " -> " + mFileName);

    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String title = getArguments().getString("Music Player");
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setTitle(title);
        View inflater = LayoutInflater.from(getActivity()).inflate(R.layout.fragment_dialog_player, null);
        titleView = (TextView) inflater.findViewById(R.id.textTitleName);
        imageView = (ImageView) inflater.findViewById(R.id.imageView);

        btnPlay = (ImageButton) inflater.findViewById(R.id.btnPlay);
        btnForward = (ImageButton) inflater.findViewById(R.id.btnForward);
        btnBackward = (ImageButton) inflater.findViewById(R.id.btnBackward);
        songProgressBar = (SeekBar) inflater.findViewById(R.id.songProgressBar);
        songCurrentDurationLabel = (TextView) inflater.findViewById(R.id.textView2);
        songTotalDurationLabel = (TextView) inflater.findViewById(R.id.textView3);

        alertDialogBuilder.setView(inflater);


        if(mDrawable == 0)
            imageView.setImageResource(android.R.drawable.sym_call_incoming);
        else
            imageView.setImageResource(android.R.drawable.sym_call_incoming);

        alertDialogBuilder.setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });


        return alertDialogBuilder.create();
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }

    @Override public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Mediaplayer
        mp = new MediaPlayer();
        //songManager = new SongsManager();
        utils = new Utilities();

        // Listeners
        songProgressBar.setOnSeekBarChangeListener(this); // Important
        mp.setOnCompletionListener(this); // Important

        // Getting all songs list
        //songsList = songManager.getPlayList();

        // By default play first song
        playSong(0);

        /**
         * Play button click event
         * plays a song and changes button to pause image
         * pauses a song and changes button to play image
         * */
        btnPlay.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // check for already playing
                if(mp.isPlaying()){
                    if(mp!=null){
                        mp.pause();
                        // Changing button image to play button
                        btnPlay.setImageResource(R.drawable.btn_play);
                    }
                }else{
                    // Resume song
                    if(mp!=null){
                        mp.start();
                        // Changing button image to pause button
                        btnPlay.setImageResource(R.drawable.btn_pause);
                    }
                }

            }
        });

        /**
         * Forward button click event
         * Forwards song specified seconds
         * */
        btnForward.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // get current song position
                int currentPosition = mp.getCurrentPosition();
                // check if seekForward time is lesser than song duration
                if(currentPosition + seekForwardTime <= mp.getDuration()){
                    // forward song
                    mp.seekTo(currentPosition + seekForwardTime);
                }else{
                    // forward to end position
                    mp.seekTo(mp.getDuration());
                }
            }
        });

        /**
         * Backward button click event
         * Backward song to specified seconds
         * */
        btnBackward.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // get current song position
                int currentPosition = mp.getCurrentPosition();
                // check if seekBackward time is greater than 0 sec
                if(currentPosition - seekBackwardTime >= 0){
                    // forward song
                    mp.seekTo(currentPosition - seekBackwardTime);
                }else{
                    // backward to starting position
                    mp.seekTo(0);
                }

            }
        });


    }


    /**
     * Function to play a song
     * @param songIndex - index of song
     * */
    public void  playSong(int songIndex){
        // Play song
        try {
            //mSoundFile = SoundFile.create(mFile.getAbsolutePath(), listener);

            mp.reset();
            mp.setDataSource(mFilePath);
            mp.prepare();
            mp.start();
            // Displaying Song title
            String songTitle = mFileName;
            titleView.setText(songTitle);

            // Changing Button Image to pause image
            btnPlay.setImageResource(R.drawable.btn_pause);

            // set Progress bar values
            songProgressBar.setProgress(0);
            songProgressBar.setMax(100);

            // Updating progress bar
            updateProgressBar();
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update timer on seekbar
     * */
    public void updateProgressBar() {
        mHandler.postDelayed(mUpdateTimeTask, 100);
    }

    /**
     * Background Runnable thread
     * */
    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            long totalDuration = mp.getDuration();
            long currentDuration = mp.getCurrentPosition();

            // Displaying Total Duration time
            songTotalDurationLabel.setText(""+utils.milliSecondsToTimer(totalDuration));
            // Displaying time completed playing
            songCurrentDurationLabel.setText(""+utils.milliSecondsToTimer(currentDuration));

            // Updating progress bar
            int progress = (int)(utils.getProgressPercentage(currentDuration, totalDuration));
            //Log.d("Progress", ""+progress);
            songProgressBar.setProgress(progress);

            // Running this thread after 100 milliseconds
            mHandler.postDelayed(this, 100);
        }
    };

    /**
     *
     * */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {

    }

    /**
     * When user starts moving the progress handler
     * */
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // remove message Handler from updating progress bar
        mHandler.removeCallbacks(mUpdateTimeTask);
    }

    /**
     * When user stops moving the progress hanlder
     * */
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mHandler.removeCallbacks(mUpdateTimeTask);
        int totalDuration = mp.getDuration();
        int currentPosition = utils.progressToTimer(seekBar.getProgress(), totalDuration);

        // forward or backward to certain seconds
        mp.seekTo(currentPosition);

        // update timer progress again
        updateProgressBar();
    }

    @Override
    public void onCompletion(MediaPlayer arg0) {

        // check for repeat is ON or OFF
        if(isRepeat){
            // repeat is on play same song again
            playSong(currentSongIndex);
        } else if(isShuffle){
            // shuffle is on - play a random song
            Random rand = new Random();
            currentSongIndex = rand.nextInt((songsList.size() - 1) - 0 + 1) + 0;
            playSong(currentSongIndex);
        } else{
            // no repeat or shuffle ON - play next song
            if(currentSongIndex < (songsList.size() - 1)){
                playSong(currentSongIndex + 1);
                currentSongIndex = currentSongIndex + 1;
            }else{
                // play first song
                playSong(0);
                currentSongIndex = 0;
            }
        }
    }

    @Override
    public void onDestroy(){
        if(mHandler!=null)
        mHandler.removeCallbacks(mUpdateTimeTask);
        super.onDestroy();
        if(mp!=null)
        mp.release();
    }
}
