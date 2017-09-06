package com.aykuttasil.callrecorder;

import android.Manifest;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    private static String TAG = MainActivity.class.getSimpleName();

    CallRecord callRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //callRecord = CallRecord.init(this);

        callRecord = new CallRecord.Builder(this)
                .setRecordFileName("CallRecord")
                .setRecordDirName("CallRecorder")
                .setShowSeed(true)
                .build();

        callRecord.changeReceiver(new MyCallRecordReceiver(callRecord));
        callRecord.enableSaveFile();

        MainActivityPermissionsDispatcher.getStorageWithCheck(this);


       /* callRecord = new CallRecord.Builder(this)
                .setRecordFileName("Record_" + new SimpleDateFormat("ddMMyyyyHHmmss", Locale.US).format(new Date()))
                .setRecordDirName("CallRecord")
                .setRecordDirPath(Environment.getExternalStorageDirectory().getPath())
                .setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                .setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setShowSeed(true)
                .build();*/



    }

    @NeedsPermission(value = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.PROCESS_OUTGOING_CALLS})
    void getStorage() {
        // ...
        //callRecord.startCallRecordService();
        //CallRecord.initService(this);

    }

    public void StartCallRecordClick(View view) {
        Log.i("CallRecord", "StartCallRecordClick");
        callRecord.startCallReceiver();

        //callRecord.enableSaveFile();
        //callRecord.changeRecordDirName("NewDirName");
    }

    public void StopCallRecordClick(View view) {
        Log.i("CallRecord", "StopCallRecordClick");
        callRecord.stopCallReceiver();

        //callRecord.disableSaveFile();
        //callRecord.changeRecordFileName("NewFileName");
    }
}
