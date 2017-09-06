package com.aykuttasil.callrecorder.receiver;

import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import com.aykuttasil.callrecorder.CallRecord;
import com.aykuttasil.callrecorder.MyApp;
import com.aykuttasil.callrecorder.helper.PrefsHelper;
import com.aykuttasil.callrecorder.records.CallRecordModel;
import com.aykuttasil.callrecorder.records.CallRecordModel_;
import com.aykuttasil.callrecorder.services.RecordService;
import io.objectbox.Box;
import io.objectbox.query.Query;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import static com.aykuttasil.callrecorder.CallRecord.PREF_CALL_TYPE;

/**
 * Created by aykutasil on 19.10.2016.
 */
public class CallRecordReceiver extends PhoneCallReceiver {


    private static final String TAG = "CallRecord";

    public static final String ACTION_IN = "android.intent.action.PHONE_STATE";
    public static final String ACTION_OUT = "android.intent.action.NEW_OUTGOING_CALL";
    public static final String EXTRA_PHONE_NUMBER = "android.intent.extra.PHONE_NUMBER";

    protected CallRecord callRecord;
    private static MediaRecorder recorder;
    private File audiofile;
    private boolean isRecordStarted = false;

    public CallRecordReceiver(CallRecord callRecord) {
        this.callRecord = callRecord;
    }

    public CallRecordReceiver() {
        super();

    }

    @Override
    protected void onIncomingCallReceived(Context context, String number, Date start) {

    }

    @Override
    protected void onIncomingCallAnswered(Context context, String number, Date start) {
        Log.w("CallRecord", "onIncomingCallAnswered");

        String file_name = PrefsHelper.readPrefString(context, CallRecord.PREF_FILE_NAME);
        String dir_path = PrefsHelper.readPrefString(context, CallRecord.PREF_DIR_PATH);
        String dir_name = PrefsHelper.readPrefString(context, CallRecord.PREF_DIR_NAME);
        boolean show_seed = PrefsHelper.readPrefBool(context, CallRecord.PREF_SHOW_SEED);
        boolean show_phone_number = PrefsHelper.readPrefBool(context, CallRecord.PREF_SHOW_PHONE_NUMBER);
        int output_format = PrefsHelper.readPrefInt(context, CallRecord.PREF_OUTPUT_FORMAT);
        int audio_source = PrefsHelper.readPrefInt(context, CallRecord.PREF_AUDIO_SOURCE);
        int audio_encoder = PrefsHelper.readPrefInt(context, CallRecord.PREF_AUDIO_ENCODER);

        if(PrefsHelper.readPrefBool(context, CallRecord.PREF_IS_RECORD_ENABLED)){
            if(PrefsHelper.readPrefBool(context, CallRecord.PREF_IS_INCOMING_RECORD_ENABLED)){
                Box<CallRecordModel> mscBox =
                    ((MyApp) context.getApplicationContext()).getBoxStore()
                        .boxFor(CallRecordModel.class);

                CallRecordModel cm  = new CallRecordModel();
                cm.setRecordedDate(start);
                cm.setCallerNumber(number);
                cm.setCallType(0);
                //cm.setCallStatus("Recording");
                mscBox.put(cm);

                Intent callIntent = new Intent(context, RecordService.class);
                Bundle b = new Bundle();
                b.putLong(PREF_CALL_TYPE, cm.getId());
                callIntent.putExtras(b);
                context.startService(callIntent);
            }
        }


        // start service here to record

       /* this.callRecord = new CallRecord.Builder(context)
            .setRecordFileName("CallRecord")
            .setRecordDirName("CallRecorder")
            .setShowSeed(true)
            .build();
        startRecord(context, "incoming", number);*/

    }

    @Override
    protected void onIncomingCallEnded(Context context, String number, Date start, Date end) {
        stopRecord(number, start, context);
    }

    @Override
    protected void onOutgoingCallStarted(Context context, String number, Date start) {
        Log.w("CallRecord", "onOutgoingCallStarted");

        if(PrefsHelper.readPrefBool(context, CallRecord.PREF_IS_RECORD_ENABLED)){
            if(PrefsHelper.readPrefBool(context, CallRecord.PREF_IS_OUTGOING_RECORD_ENABLED)){
                Box<CallRecordModel> mscBox =
                    ((MyApp) context.getApplicationContext()).getBoxStore()
                        .boxFor(CallRecordModel.class);

                CallRecordModel cm  = new CallRecordModel();
                cm.setRecordedDate(start);
                cm.setCallerNumber(number);
                cm.setCallType(1);
                cm.setCallStatus("Recording");
                mscBox.put(cm);

                Intent callIntent = new Intent(context, RecordService.class);
                Bundle b = new Bundle();
                b.putLong(PREF_CALL_TYPE, cm.getId());
                callIntent.putExtras(b);
                context.startService(callIntent);
            }
        }


        /*this.callRecord = new CallRecord.Builder(context)
            .setRecordFileName("CallRecord")
            .setRecordDirName("CallRecorder")
            .setShowSeed(true)
            .setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB) // optional & default value
            .setOutputFormat(MediaRecorder.OutputFormat.AMR_NB) // optional & default value
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION) // optional & default value
            .build();
        startRecord(context, "outgoing", number);*/
    }

    @Override
    protected void onOutgoingCallEnded(Context context, String number, Date start, Date end) {
        stopRecord(number , start, context);
    }

    @Override
    protected void onMissedCall(Context context, String number, Date start) {

    }

    // Derived classes could override these to respond to specific events of interest
    protected void onRecordingStarted(Context context, CallRecord callRecord, File audioFile) {
    }

    protected void onRecordingFinished(Context context, CallRecord callRecord, File audioFile) {
    }

    private void startRecord(Context context, String seed, String phoneNumber) {

        try {

            boolean isSaveFile = PrefsHelper.readPrefBool(context, CallRecord.PREF_SAVE_FILE);
            Log.i(TAG, "isSaveFile: " + isSaveFile);

            // dosya kayıt edilsin mi?
            if (!isSaveFile) {
                return;
            }

            String file_name = PrefsHelper.readPrefString(context, CallRecord.PREF_FILE_NAME);
            String dir_path = PrefsHelper.readPrefString(context, CallRecord.PREF_DIR_PATH);
            String dir_name = PrefsHelper.readPrefString(context, CallRecord.PREF_DIR_NAME);
            boolean show_seed = PrefsHelper.readPrefBool(context, CallRecord.PREF_SHOW_SEED);
            boolean show_phone_number = PrefsHelper.readPrefBool(context, CallRecord.PREF_SHOW_PHONE_NUMBER);
            int output_format = PrefsHelper.readPrefInt(context, CallRecord.PREF_OUTPUT_FORMAT);
            int audio_source = PrefsHelper.readPrefInt(context, CallRecord.PREF_AUDIO_SOURCE);
            int audio_encoder = PrefsHelper.readPrefInt(context, CallRecord.PREF_AUDIO_ENCODER);

            File sampleDir = new File(dir_path + "/" + dir_name);

            if (!sampleDir.exists()) {
                sampleDir.mkdirs();
            }


            StringBuilder fileNameBuilder = new StringBuilder();
            fileNameBuilder.append(file_name);
            fileNameBuilder.append("_");

            if (show_seed) {
                fileNameBuilder.append(seed);
                fileNameBuilder.append("_");
            }

            if (show_phone_number) {
                fileNameBuilder.append(phoneNumber);
                fileNameBuilder.append("_");
            }


            file_name = fileNameBuilder.toString();

            String suffix = "";
            switch (output_format) {
                case MediaRecorder.OutputFormat.AMR_NB: {
                    suffix = ".amr";
                    break;
                }
                case MediaRecorder.OutputFormat.AMR_WB: {
                    suffix = ".amr";
                    break;
                }
                case MediaRecorder.OutputFormat.MPEG_4: {
                    suffix = ".mp4";
                    break;
                }
                case MediaRecorder.OutputFormat.THREE_GPP: {
                    suffix = ".3gp";
                    break;
                }
                default: {
                    suffix = ".amr";
                    break;
                }
            }

            audiofile = File.createTempFile(file_name, suffix, sampleDir);

            recorder = new MediaRecorder();
            recorder.setAudioSource(audio_source);
            recorder.setOutputFormat(output_format);
            recorder.setAudioEncoder(audio_encoder);
            recorder.setOutputFile(audiofile.getAbsolutePath());
            recorder.prepare();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            recorder.start();

            isRecordStarted = true;
            onRecordingStarted(context, callRecord, audiofile);

            Log.i(TAG, "record start");

        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
        }

        //save details in database
        // file location and phone number + details


    }

    private void stopRecord(String number, Date start, Context context) {
        /*if (recorder != null && isRecordStarted) {

            recorder.stop();
            recorder.reset();
            recorder.release();

            isRecordStarted = false;
            onRecordingFinished(context, callRecord, audiofile);

            Log.i(TAG, "record stop");
        }*/
        Box<CallRecordModel> mscBox =
            ((MyApp) context.getApplicationContext()).getBoxStore().boxFor(CallRecordModel.class);

        Query<CallRecordModel> query = mscBox.query()
            .equal(CallRecordModel_.callerNumber, number)
            .and()
            .equal(CallRecordModel_.recordedDate, start)
            .build();

        CallRecordModel cm = query.findFirst();
        if(cm!=null) {
            cm.setCallDuration(Calendar.getInstance().getTimeInMillis() - start.getTime());
            mscBox.put(cm);
        }
        Intent callIntent = new Intent(context, RecordService.class);
        context.stopService(callIntent);


    }

}
