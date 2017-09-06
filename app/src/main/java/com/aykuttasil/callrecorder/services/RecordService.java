package com.aykuttasil.callrecorder.services;

import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import com.aykuttasil.callrecorder.CallRecord;
import com.aykuttasil.callrecorder.MyApp;
import com.aykuttasil.callrecorder.R;
import com.aykuttasil.callrecorder.helper.PrefsHelper;
import com.aykuttasil.callrecorder.records.CallRecordModel;
import com.aykuttasil.callrecorder.utils.Preferences;
import io.objectbox.Box;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Exception;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.text.SimpleDateFormat;

import android.os.IBinder;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.widget.Toast;
import android.util.Log;

//import java.security.KeyPairGenerator;
//import java.security.KeyPair;
//import java.security.Key;

import java.io.InputStream;
import java.io.FileInputStream;
import java.util.Iterator;

import static com.aykuttasil.callrecorder.CallRecord.PREF_AUDIO_SOURCE;
import static com.aykuttasil.callrecorder.CallRecord.PREF_CALL_TYPE;
import static com.aykuttasil.callrecorder.CallRecord.PREF_DIR_PATH;

public class RecordService 
    extends Service
    implements MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener{
    private static final String TAG = "CallRecorder";

    public static final String DEFAULT_STORAGE_LOCATION = Environment.getExternalStorageDirectory().toString();
    private static final int RECORDING_NOTIFICATION_ID = 1;

    int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format

    private MediaRecorder recorder = null;
    private boolean isRecording = false;
    private File recording = null;;
    private int deviceCallVol;
    private String nativeSampleRate;
    private String nativeSampleBufSize;
    private boolean supportRecording;
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private RehearsalAudioRecorder audiorecorder = null;
    private int sampleRate;
    private int bufSize;
    private String mPath;
    /*
    private static void test() throws java.security.NoSuchAlgorithmException
    {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.genKeyPair();
        Key publicKey = kp.getPublic();
        Key privateKey = kp.getPrivate();
    }
    */

    private void queryNativeAudioParameters() {
        AudioManager myAudioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        nativeSampleRate  =  myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        nativeSampleBufSize =myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        int recBufSize = AudioRecord.getMinBufferSize(
            Integer.parseInt(nativeSampleRate),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
        supportRecording = true;
        if (recBufSize == AudioRecord.ERROR ||
            recBufSize == AudioRecord.ERROR_BAD_VALUE) {
            supportRecording = false;
        }
    }

    private File makeOutputFile(SharedPreferences prefs, String callerNumber)
    {
        String path = PrefsHelper.readPrefString(this, PREF_DIR_PATH);
        File dir = new File(path);

        // test dir for existence and writeability
        if (!dir.exists()) {
            try {
                dir.mkdirs();
            } catch (Exception e) {
                Log.e("CallRecorder", "RecordService::makeOutputFile unable to create directory " + dir + ": " + e);
                Toast t = Toast.makeText(getApplicationContext(), "CallRecorder was unable to create the directory " + dir + " to store recordings: " + e, Toast.LENGTH_LONG);
                t.show();
                return null;
            }
        } else {
            if (!dir.canWrite()) {
                Log.e(TAG, "RecordService::makeOutputFile does not have write permission for directory: " + dir);
                Toast t = Toast.makeText(getApplicationContext(), "CallRecorder does not have write permission for the directory directory " + dir + " to store recordings", Toast.LENGTH_LONG);
                t.show();
                return null;
            }
        }

        // test size

        // create filename based on call data
        //String prefix = "call";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String prefix = sdf.format(new Date());

        // add info to file name about what audio channel we were recording
        callerNumber = callerNumber.trim();
        callerNumber = callerNumber.replaceAll("\\D+","");
        prefix += "-" + callerNumber + "-";

        // create suffix based on format
        String suffix = "";
        int audioformat = Integer.parseInt(prefs.getString(Preferences.PREF_AUDIO_FORMAT, "6"));
        switch (audioformat) {
        case MediaRecorder.OutputFormat.AAC_ADTS:
            suffix = ".aac";
            break;
        case MediaRecorder.OutputFormat.MPEG_4:
            suffix = ".mpg";
            break;
        case MediaRecorder.OutputFormat.AMR_NB:
            suffix = ".amr";
            break;
        }
        suffix = ".wav";
        try {
            File f = File.createTempFile(prefix, suffix, dir);
            Log.e("tarun", "File created:" + f.getPath());
            return f;
        } catch (IOException e) {
            Log.e("CallRecorder", "RecordService::makeOutputFile unable to create temp file in " + dir + ": " + e);
            Toast t = Toast.makeText(getApplicationContext(), "CallRecorder was unable to create temp file in " + dir + ": " + e, Toast.LENGTH_LONG);
            t.show();
            return null;
        }
    }
    enum State { INITIALIZING, READY, STARTED, RECORDING };

    private final static int[] sampleRates = {44100, 22050, 11025, 8000};
    public void onCreate()
    {
        super.onCreate();

        recorder = new MediaRecorder();
        //queryNativeAudioParameters();
        //createSLEngine(Integer.parseInt(nativeSampleRate), Integer.parseInt(nativeSampleBufSize));
       /* createEngine();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            AudioManager myAudioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            String nativeParam = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            sampleRate = Integer.parseInt(nativeParam);
            nativeParam = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            bufSize = Integer.parseInt(nativeParam);
        }
        createBufferQueueAudioPlayer(sampleRate, bufSize);*/
        Log.i("CallRecorder", "onCreate created MediaRecorder object");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.i("CallRecorder", "RecordService::onStart calling through to onStartCommand");
        //onStartCommand(intent, 0, startId);
        //}

        //public int onStartCommand(Intent intent, int flags, int startId)
        //{
        Log.i("CallRecorder", "RecordService::onStartCommand called while isRecording:" + isRecording);

        if (isRecording){
            return START_NOT_STICKY;
        }

        Bundle b = intent.getExtras();
        long idOfObjectBox = b.getLong(PREF_CALL_TYPE, -1);
        Log.i("CallRecorder", "RecordService::onStartCommand idOfObjectBox:" + idOfObjectBox);

        if(idOfObjectBox!=-1) {
            Box<CallRecordModel> mscBox =
                ((MyApp) getApplication()).getBoxStore().boxFor(CallRecordModel.class);
            CallRecordModel objectBox = mscBox.get(idOfObjectBox);

            Context c = getApplicationContext();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);

            /*Boolean shouldRecord = prefs.getBoolean(Preferences.PREF_RECORD_CALLS, false);
            if (!shouldRecord) {
                Log.i("CallRecord",
                    "RecordService::onStartCommand with PREF_RECORD_CALLS false, not recording");
                //return START_STICKY;
                return;
            }*/

            //source_array_values

            int selection = PrefsHelper.readPrefInt(this, PREF_AUDIO_SOURCE);
            String [] list = getResources().getStringArray(R.array.source_array_values);
            int audiosource = Integer.parseInt(list[selection]);
           /* int audiosource = Integer.parseInt(prefs.getString(Preferences.PREF_AUDIO_SOURCE,
                String.valueOf(MediaRecorder.AudioSource.VOICE_CALL)));*/
            int audioformat = Integer.parseInt(prefs.getString(Preferences.PREF_AUDIO_FORMAT, "6"));

            recording = makeOutputFile(prefs, objectBox.getCallerNumber());
            objectBox.setRecordLocation(recording.getPath());
            mscBox.put(objectBox);
            if (recording == null) {
                recorder = null;
                return START_NOT_STICKY; //return 0;
            }

            Log.i("CallRecorder", "RecordService will config MediaRecorder with audiosource: "
                + audiosource
                + " audioformat: "
                + audioformat);
            try {
                // These calls will throw exceptions unless you set the
                // android.permission.RECORD_AUDIO permission for your app
                recorder.reset();
                recorder.setAudioSource(audiosource);
                Log.d("CallRecorder", "set audiosource " + audiosource);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                Log.d("CallRecorder", "set output " + audioformat);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                Log.d("CallRecorder", "set encoder default");
                recorder.setOutputFile(recording.getPath());
                Log.d("CallRecorder", "set file: " + recording);
                //recorder.setMaxDuration(msDuration); //1000); // 1 seconds
                //recorder.setMaxFileSize(bytesMax); //1024*1024); // 1KB

                recorder.setOnInfoListener(this);
                recorder.setOnErrorListener(this);

                try {
                    recorder.prepare();
                } catch (IOException e) {
                    // IF VOICE_CALL failed then check some other source
                    e.printStackTrace();
                    Log.e("CallRecorder",
                        "RecordService::onStart() IOException attempting recorder.prepare()\n");
                    Toast t = Toast.makeText(getApplicationContext(),
                        "CallRecorder was unable to start recording: " + e, Toast.LENGTH_LONG);
                    t.show();
                    recorder = null;
                    return START_NOT_STICKY; //return 0; //START_STICKY;
                }
                Log.d("CallRecorder", "recorder.prepare() returned");
                isRecording = true;

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    isRecording = false;
                    e.printStackTrace();
                }
                recorder.start();
                Log.i("CallRecorder", "recorder.start() returned");
                updateNotification(true);
            } catch (Exception e ) {
                //Toast t = Toast.makeText(getApplicationContext(),
                //    "CallRecorder was unable to start recording: " + e, Toast.LENGTH_LONG);
                //t.show();

                Log.e("CallRecorder", "RecordService::onStart caught unexpected exception", e);
                //recorder.reset();

                /*AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                deviceCallVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                //set volume to maximum
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0);

                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                Log.d("CallRecorder", "set audiosource " + audiosource);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
                Log.d("CallRecorder", "set output " + audioformat);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                Log.d("CallRecorder", "set encoder default:"+recording.getPath());
                recorder.setOutputFile(recording.getPath());
                recorder.setAudioEncodingBitRate(24);
                recorder.setAudioSamplingRate(44100);
                isRecording = true;
                try {
                    recorder.prepare();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                    isRecording = false;
                    e1.printStackTrace();
                }
                try {
                    recorder.start();
                } catch (Exception e1) {
                    isRecording = false;
                    e1.printStackTrace();
                }*/
                /*audiorecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);
                audiorecorder.startRecording();
                audiorecorder.setRecordPositionUpdateListener(this);*/

                /*AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                deviceCallVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                //set volume to maximum
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0);
                audioManager.setMode(AudioManager.MODE_IN_CALL);
                audioManager.setParameters("noise_suppression=on");*/

               /* audiorecorder = WavAudioRecorder.getInstanse();
                audiorecorder.setOutputFile(recording.getPath());
                audiorecorder.prepare();
                audiorecorder.start();*/

                int i=0;
                do
                {
                    if (audiorecorder != null)
                        audiorecorder.release();
                    audiorecorder = new RehearsalAudioRecorder(true, MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRates[i], AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                } while((++i<sampleRates.length) & !(audiorecorder.getState() == RehearsalAudioRecorder.State.INITIALIZING));

                audiorecorder.setOutputFile(recording.getPath());
                audiorecorder.setGain(16);
                audiorecorder.prepare();
                audiorecorder.start();
                isRecording = true;
            }
        }
        return START_STICKY;
    }

    public void onDestroy()
    {
        super.onDestroy();

        if (null != recorder) {
            Log.i("CallRecorder", "RecordService::onDestroy calling recorder.release()");
            isRecording = false;

            if(recorder!=null)
                recorder.release();
            if(audiorecorder!=null)
            {
                audiorecorder.stop();
                audiorecorder.reset();
                //shutdown();
                /*AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                deviceCallVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                //set volume to maximum
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, deviceCallVol, 0);*/
            }


            /*
            // encrypt the recording
            String keyfile = "/sdcard/keyring";
            try {
                //PGPPublicKey k = readPublicKey(new FileInputStream(keyfile));
                test();
            } catch (java.security.NoSuchAlgorithmException e) {
                Log.e("CallRecorder", "RecordService::onDestroy crypto test failed: ", e);
            }
            //encrypt(recording);
            */
        }

        updateNotification(false);
    }


    // methods to handle binding the service

    public IBinder onBind(Intent intent)
    {
        return null;
    }

    public boolean onUnbind(Intent intent)
    {
        return false;
    }

    public void onRebind(Intent intent)
    {
    }


    private void updateNotification(Boolean status)
    {
        /*Context c = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);

        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

        if (status) {
            int icon = R.drawable.rec;
            CharSequence tickerText = "Recording call from channel " + prefs.getString(Preferences.PREF_AUDIO_SOURCE, "1");
            long when = System.currentTimeMillis();
            
            Notification notification = new Notification(icon, tickerText, when);
            
            Context context = getApplicationContext();
            CharSequence contentTitle = "CallRecorder Status";
            CharSequence contentText = "Recording call from channel...";
            Intent notificationIntent = new Intent(this, RecordService.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            
            notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
            mNotificationManager.notify(RECORDING_NOTIFICATION_ID, notification);
        } else {
            mNotificationManager.cancel(RECORDING_NOTIFICATION_ID);
        }*/
    }

    // MediaRecorder.OnInfoListener
    public void onInfo(MediaRecorder mr, int what, int extra)
    {
        Log.i("CallRecorder", "RecordService got MediaRecorder onInfo callback with what: " + what + " extra: " + extra);
        isRecording = false;
    }

    // MediaRecorder.OnErrorListener
    public void onError(MediaRecorder mr, int what, int extra) 
    {
        Log.e("CallRecorder", "RecordService got MediaRecorder onError callback with what: " + what + " extra: " + extra);
        isRecording = false;
        mr.release();
    }
/*
    public static native void createEngine();
    public static native void createBufferQueueAudioPlayer(int sampleRate, int samplesPerBuf);
    public static native boolean createAssetAudioPlayer(AssetManager assetManager, String filename);
    // true == PLAYING, false == PAUSED
    public static native void setPlayingAssetAudioPlayer(boolean isPlaying);
    public static native boolean createUriAudioPlayer(String uri);
    public static native void setPlayingUriAudioPlayer(boolean isPlaying);
    public static native void setLoopingUriAudioPlayer(boolean isLooping);
    public static native void setChannelMuteUriAudioPlayer(int chan, boolean mute);
    public static native void setChannelSoloUriAudioPlayer(int chan, boolean solo);
    public static native int getNumChannelsUriAudioPlayer();
    public static native void setVolumeUriAudioPlayer(int millibel);
    public static native void setMuteUriAudioPlayer(boolean mute);
    public static native void enableStereoPositionUriAudioPlayer(boolean enable);
    public static native void setStereoPositionUriAudioPlayer(int permille);
    public static native boolean selectClip(int which, int count);
    public static native boolean enableReverb(boolean enabled);
    public static native boolean createAudioRecorder();
    public static native void startRecording(String uri);
    public static native void shutdown();

    *//** Load jni .so on initialization *//*
    static {
        System.loadLibrary("native-audio-jni");
    }

    *//*
     * jni function implementations...
     *//*

    @Override public void onMarkerReached(AudioRecord audioRecord) {

    }

    @Override public void onPeriodicNotification(AudioRecord audioRecord) {

    }*/
}
