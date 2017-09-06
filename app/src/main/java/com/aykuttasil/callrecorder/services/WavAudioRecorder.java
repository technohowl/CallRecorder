package com.aykuttasil.callrecorder.services;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import org.jcodec.audio.Audio;
import org.jcodec.audio.AudioFilter;
import org.jcodec.audio.FilterGraph;
import org.jcodec.audio.SincLowPassFilter;
import org.jcodec.codecs.wav.WavInput;
import org.jcodec.codecs.wav.WavOutput;

/**
 * Created by tarun on 03/05/17.
 */

public class WavAudioRecorder {
    //private final static int[] sampleRates = {8000, 11025, 22050};
    private long lastUpdate;
    private short smoothed = 0;
    private long smoothing = 24;
    private double b = 6;
    private final static int[] sampleRates = {44100, 22050, 11025, 8000};

    public static WavAudioRecorder getInstanse() {
        WavAudioRecorder result = null;
        int i=0;
        /*do {
            result = new WavAudioRecorder(AudioSource.MIC,
                sampleRates[i],
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        } while((++i<sampleRates.length) & !(result.getState() == WavAudioRecorder.State.INITIALIZING));*/
        result = new WavAudioRecorder(AudioSource.VOICE_RECOGNITION,
            sampleRates[1],
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
        return result;
    }

    /**
     * INITIALIZING : recorder is initializing;
     * READY : recorder has been initialized, recorder not yet started
     * RECORDING : recording
     * ERROR : reconstruction needed
     * STOPPED: reset needed
     */
    public enum State {INITIALIZING, READY, RECORDING, ERROR, STOPPED};

    public static final boolean RECORDING_UNCOMPRESSED = true;
    public static final boolean RECORDING_COMPRESSED = false;

    // The interval in which the recorded samples are output to the file
    // Used only in uncompressed mode
    private static final int TIMER_INTERVAL = 20;

    // Recorder used for uncompressed recording
    private AudioRecord     audioRecorder = null;

    // Output file path
    private String          filePath = null;

    // Recorder state; see State
    private State          	state;

    // File writer (only in uncompressed mode)
    private RandomAccessFile randomAccessWriter;

    // Number of channels, sample rate, sample size(size in bits), buffer size, audio source, sample size(see AudioFormat)
    private short                    nChannels;
    private int                      sRate;
    private short                    mBitsPersample;
    private int                      mBufferSize;
    private int                      mAudioSource;
    private int                      aFormat;

    // Number of frames/samples written to file on each output(only in uncompressed mode)
    private int                      mPeriodInFrames;

    // Buffer for output(only in uncompressed mode)
    private byte[]                   buffer;

    // Number of bytes written to file after header(only in uncompressed mode)
    // after stop() is called, this size is written to the header/data chunk in the wave file
    private int                      payloadSize;

    /**
     *
     * Returns the state of the recorder in a WavAudioRecorder.State typed object.
     * Useful, as no exceptions are thrown.
     *
     * @return recorder state
     */
    public State getState() {
        return state;
    }

    public static boolean isAudible(short[] data) {
        double rms = getRootMeanSquared(data);
        return (rms > 198 && 5600 > rms);
    }

    public static double getRootMeanSquared(short[] data) {
        double ms = 0;
        for (int i = 0; i < data.length; i++) {
            ms += data[i] * data[i];
        }
        ms /= data.length;
        return Math.sqrt(ms);
    }

    short amplifyPCMSInt16(int value, int dbGain, boolean clampValue) {
    /*To increase the gain of a sample by X db, multiply the PCM value by
     * pow( 2.0, X/6.014 ). i.e. gain +6dB means doubling the value of the sample, -6dB means halving it.
     */
        int newValue = (int) ( Math.pow(2.0, ((double)dbGain)/6.014 )*value);
        //newValue = smoothedValue(newValue);

        if(clampValue){
            if(newValue>32767)
                newValue = 32767;
            else if(newValue < -32768 )
                newValue = -32768;
        }
        return (short) newValue;
    }

    private byte[] adjustVolume(byte[] audioSamples, int volume) {
        byte[] array = new byte[audioSamples.length];
        for (int i = 0; i < array.length; i+=2) {
            // convert byte pair to int
            short buf1 = audioSamples[i+1];
            short buf2 = audioSamples[i];

            buf1 = (short) ((buf1 & 0xff) << 8);
            buf2 = (short) (buf2 & 0xff);

            short res= (short) (buf1 | buf2);
            res = amplifyPCMSInt16(res, volume, true);

            // convert back
            array[i] = (byte) res;
            array[i+1] = (byte) (res >> 8);

        }
        return array;
    }


    private short a(int paramShort)
    {
        int s;
        {
            s = (int) (paramShort * b(6.0f));
            if (s > Short.MAX_VALUE) {
                s = Short.MAX_VALUE;
            }
            else if (s < Short.MIN_VALUE) {
                s = Short.MIN_VALUE;
            }
        }
        return (short)s;
    }

    private double b(double a)
    {
        return Math.pow(2.0D, a / 6.014D);
    }

    public ShortBuffer a(ShortBuffer paramShortBuffer)
    {
        for (int i = 0; i < paramShortBuffer.limit(); i++) {
            paramShortBuffer.put(i, a(paramShortBuffer.get(i)));
        }
        return paramShortBuffer;
    }

    private byte aByte(byte paramByte)
    {
        int b1;
        {
            b1 = (int)(paramByte * b(6));
            if (b1 > Byte.MAX_VALUE) {
                b1 = Byte.MAX_VALUE;
            }
            if (b1 < Byte.MIN_VALUE) {
                b1 = Byte.MIN_VALUE;
            }
        }
        return (byte)b1;
    }



    private double k() {
        double pow;
        if (this.b != 0) {
            pow = Math.pow(2.0, this.b / 6.014);
        }
        else {
            pow = 1.0;
        }
        return pow;
    }

    public static byte[] F(short[] arr) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(arr.length * 2);
        bb.asShortBuffer().put(arr);
        byte []byteArray = new byte[bb.limit()]; // this returns the "raw" array, it's shared and not copied!
        bb.get(byteArray);
        return byteArray;
    }
    private ShortBuffer		shBuffer;
    private ByteBuffer		bBuffer;
    private short			 bSamples;
    private double 			gain = 2; // Gain converted from dB to multiplier;
    private int				 cAmplitude = 0;

    private float mVolume = 1.5f ;
    private FileChannel fChannel;

    int recBufferByteSize = mBufferSize*2;

    byte[] recBuffer = new byte[recBufferByteSize];
    int frameByteSize = mBufferSize/2;
    int sampleBytes = frameByteSize;
    int recBufferBytePtr = 0;

    private AudioRecord.OnRecordPositionUpdateListener updateListenerNew = new AudioRecord.OnRecordPositionUpdateListener() {
        @Override
        public void onPeriodicNotification(AudioRecord recorder) {
            /*audioRecorder.read(bBuffer, bBuffer.capacity()); // Fill buffer
            if (getState() != State.RECORDING)
                return;
            try {
                if (bSamples == 16) {
                    shBuffer.rewind();
                    int bLength = shBuffer.capacity(); // Faster than accessing buffer.capacity each time
                    for (int i = 0; i < bLength; i++) { // 16bit sample size
                        short curSample = (short) (shBuffer.get(i) * gain);
                        if (curSample > cAmplitude) { // Check amplitude
                            cAmplitude = curSample;
                        }
                        if(mVolume != 1.0f) {
                            // Adjust output volume.
                            int fixedPointVolume = (int)(mVolume*4096.0f);
                            int value = (curSample*fixedPointVolume) >> 12;
                            if(value > 32767) {
                                value = 32767;
                            } else if(value < -32767) {
                                value = -32767;
                            }
                            curSample = (short)value;
                            *//*scaleSamples(outputBuffer, originalNumOutputSamples, numOutputSamples - originalNumOutputSamples,
                                    mVolume, nChannels);*//*
                        }
                        shBuffer.put(curSample);
                    }
                } else { // 8bit sample size
                    int bLength = bBuffer.capacity(); // Faster than accessing buffer.capacity each time
                    bBuffer.rewind();
                    for (int i = 0; i < bLength; i++) {
                        byte curSample = (byte) (bBuffer.get(i) * gain);
                        if (curSample > cAmplitude) { // Check amplitude
                            cAmplitude = curSample;
                        }
                        bBuffer.put(curSample);
                    }
                }
                bBuffer.rewind();
                fChannel.write(bBuffer); // Write buffer to file
                payloadSize += bBuffer.capacity();
            } catch (IOException e) {
                e.printStackTrace();
                stop();
            }*/
            int reallySampledBytes = audioRecorder.read( recBuffer, recBufferBytePtr, sampleBytes );

            int i = 0;
            while ( i < reallySampledBytes ) {
                float sample = (float)( recBuffer[recBufferBytePtr+i  ] & 0xFF
                    | recBuffer[recBufferBytePtr+i+1] << 8 );

                // THIS is the point were the work is done:
                // Increase level by about 6dB:
                sample *= 2;
                // Or increase level by 20dB:
                // sample *= 10;
                // Or if you prefer any dB value, then calculate the gain factor outside the loop
                // float gainFactor = (float)Math.pow( 10., dB / 20. );    // dB to gain factor
                // sample *= gainFactor;

                // Avoid 16-bit-integer overflow when writing back the manipulated data:
                if ( sample >= 32767f ) {
                    recBuffer[recBufferBytePtr+i  ] = (byte)0xFF;
                    recBuffer[recBufferBytePtr+i+1] =       0x7F;
                } else if ( sample <= -32768f ) {
                    recBuffer[recBufferBytePtr+i  ] =       0x00;
                    recBuffer[recBufferBytePtr+i+1] = (byte)0x80;
                } else {
                    int s = (int)( 0.5f + sample );  // Here, dithering would be more appropriate
                    recBuffer[recBufferBytePtr+i  ] = (byte)(s & 0xFF);
                    recBuffer[recBufferBytePtr+i+1] = (byte)(s >> 8 & 0xFF);
                }
                i += 2;
            }

            // Do other stuff like saving the part of buffer to a file
            // if ( reallySampledBytes > 0 ) { ... save recBuffer+recBufferBytePtr, length: reallySampledBytes

            // Then move the recording pointer to the next position in the recording buffer
            recBufferBytePtr += reallySampledBytes;

            // Wrap around at the end of the recording buffer, e.g. like so:
            if ( recBufferBytePtr >= recBufferByteSize ) {
                recBufferBytePtr = 0;
                sampleBytes = frameByteSize;
            } else {
                sampleBytes = recBufferByteSize - recBufferBytePtr;
                if ( sampleBytes > frameByteSize )
                    sampleBytes = frameByteSize;
            }
        }

        @Override
        public void onMarkerReached(AudioRecord recorder) {
            // NOT USED
        }
    };
    private AudioRecord.OnRecordPositionUpdateListener updateListener = new AudioRecord.OnRecordPositionUpdateListener() {
        //	periodic updates on the progress of the record head
        public void onPeriodicNotification(AudioRecord recorder) {
            if (State.STOPPED == state) {
                Log.d(WavAudioRecorder.this.getClass().getName(), "recorder stopped");
                return;
            }
            int numOfBytes = audioRecorder.read(buffer, 0, buffer.length); // read audio data to buffer
            //			Log.d(WavAudioRecorder.this.getClass().getName(), state + ":" + numOfBytes);
            try {
                buffer = adjustVolume(buffer, 2);
                randomAccessWriter.write(buffer); 		  // write audio data to file
                payloadSize += buffer.length;
            } catch (IOException e) {
                Log.e(WavAudioRecorder.class.getName(), "Error occured in updateListener, recording is aborted");
                e.printStackTrace();
            }
        }
        //	reached a notification marker set by setNotificationMarkerPosition(int)
        public void onMarkerReached(AudioRecord recorder) {
        }
    };

    private AudioRecord.OnRecordPositionUpdateListener updateListener1 = new AudioRecord.OnRecordPositionUpdateListener() {
        //	periodic updates on the progress of the record head
        public void onPeriodicNotification(AudioRecord recorder) {
            if (State.STOPPED == state) {
                Log.d(WavAudioRecorder.this.getClass().getName(), "recorder stopped");
                return;
            }
            int numOfBytes = audioRecorder.read(buffer, 0, buffer.length); // read audio data to buffer
            //			Log.d(WavAudioRecorder.this.getClass().getName(), state + ":" + numOfBytes);
            try {

                final int USHORT_MASK = (1 << 16) - 1;

                /*final ShortBuffer buf = ByteBuffer.wrap(buffer).order(
                    ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                for (int i = 0; i < buffer.length / 2; ++i) {
                    int value;
                    final short n = (short)(value = buf.get(i));
                    //if (j())
                    {
                        int n2;
                        if ((n2 = (int)(n * k())) > 32767) {
                            n2 = 32767;
                        }
                        if ((value = n2) < -32768) {
                            value = -32768;
                        }
                        buf.put(i, (short)value);
                    }


                }
*/
/*
                for (int j = 0; j < buffer.length; ++j) {
                    int n3;
                    final byte b = (byte)(n3 = buffer[j]);
                    //if (com.appstar.callrecordercore.t.this.j())
                    {
                        int n4;
                        if ((n4 = (int)(b * k())) > 127) {
                            n4 = 127;
                        }
                        if ((n3 = n4) < -128) {
                            n3 = -128;
                        }
                        buffer[j] = (byte)n3;
                    }

                }*/

                //byte[] newbuffer = F(buf.array());
                /*

                i = 0;
                while (i < buffer.length){
                    buffer[i] = aByte(buf.get(i));
                }
                */
                final ShortBuffer buf = ByteBuffer.wrap(buffer).order(
                    ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                final ByteBuffer newBuf = ByteBuffer.allocate(
                    buffer.length).order(ByteOrder.LITTLE_ENDIAN);

                int sample;
                while (buf.hasRemaining()) {
                    sample = (int) buf.get() ;
                    sample = a(sample);
                    //sample *= 0.1;
                    //sample = amplifyPCMSInt16(sample, 6, true);
                    //sample = smoothedValue(sample);
                    newBuf.putShort((short) (sample ));

                    //newBuf.putShort((short) (sample & USHORT_MASK));
                }

                buffer = newBuf.array();
                //buffer = smoothArray(buffer);
                //buffer =smoothArray(buffer);
                //buffer = adjustVolume(buffer, 12);
                //buffer = smoothArray(buffer);
                randomAccessWriter.write(buffer); 		  // write audio data to file
                payloadSize += buffer.length;
            } catch (IOException e) {
                Log.e(WavAudioRecorder.class.getName(), "Error occured in updateListener, recording is aborted");
                e.printStackTrace();
            }
        }
        //	reached a notification marker set by setNotificationMarkerPosition(int)
        public void onMarkerReached(AudioRecord recorder) {
        }
    };

    byte[] smoothArray( byte[]values){
        byte value = values[0]; // start with the first input
        for (int i=1, len=values.length; i<len; ++i){
            byte currentValue = values[i];
            value += (currentValue - value) / smoothing;
            values[i] = value;
        }
        return values;
    }

    short smoothedValue( int newValue ){
        long now =  System.currentTimeMillis();
        long elapsedTime = now - lastUpdate;
        smoothed += elapsedTime * ( newValue - smoothed ) / smoothing;
        lastUpdate = now;
        return smoothed;
    }
    /**
     *
     *
     * Default constructor
     *
     * Instantiates a new recorder
     * In case of errors, no exception is thrown, but the state is set to ERROR
     *
     */
    public WavAudioRecorder(int audioSource, int sampleRate, int channelConfig, int audioFormat) {
        try {
            if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                mBitsPersample = 16;
            } else {
                mBitsPersample = 8;
            }

            if (channelConfig == AudioFormat.CHANNEL_IN_MONO) {
                nChannels = 1;
            } else {
                nChannels = 2;
            }

            mAudioSource = audioSource;
            sRate   = sampleRate;
            aFormat = audioFormat;

            mPeriodInFrames = sampleRate * TIMER_INTERVAL / 1000;		//?
            mBufferSize = mPeriodInFrames * 2  * nChannels * mBitsPersample / 8;		//?
            if (mBufferSize < AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)) {
                // Check to make sure buffer size is not smaller than the smallest allowed one
                mBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                // Set frame period and timer interval accordingly
                mPeriodInFrames = mBufferSize / ( 2 * mBitsPersample * nChannels / 8 );
                Log.w(WavAudioRecorder.class.getName(), "Increasing buffer size to " + Integer.toString(mBufferSize));
            }

            audioRecorder = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, mBufferSize);

            if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new Exception("AudioRecord initialization failed");
            }
            audioRecorder.setRecordPositionUpdateListener(updateListener);
            audioRecorder.setPositionNotificationPeriod(mPeriodInFrames);
            filePath = null;
            state = State.INITIALIZING;
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.e(WavAudioRecorder.class.getName(), e.getMessage());
            } else {
                Log.e(WavAudioRecorder.class.getName(), "Unknown error occured while initializing recording");
            }
            state = State.ERROR;
        }
    }

    /**
     * Sets output file path, call directly after construction/reset.
     *
     * @param argPath file path
     *
     */
    public void setOutputFile(String argPath) {
        try {
            if (state == State.INITIALIZING) {
                filePath = argPath;
            }
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.e(WavAudioRecorder.class.getName(), e.getMessage());
            } else {
                Log.e(WavAudioRecorder.class.getName(), "Unknown error occured while setting output path");
            }
            state = State.ERROR;
        }
    }


    /**
     *
     * Prepares the recorder for recording, in case the recorder is not in the INITIALIZING state and the file path was not set
     * the recorder is set to the ERROR state, which makes a reconstruction necessary.
     * In case uncompressed recording is toggled, the header of the wave file is written.
     * In case of an exception, the state is changed to ERROR
     *
     */
    public void prepare() {
        try {
            if (state == State.INITIALIZING) {
                if ((audioRecorder.getState() == AudioRecord.STATE_INITIALIZED) & (filePath != null)) {
                    // write file header

                    randomAccessWriter = new RandomAccessFile(filePath, "rw");
                    fChannel = randomAccessWriter.getChannel();
                    randomAccessWriter.setLength(0); // Set file length to 0, to prevent unexpected behavior in case the file already existed
                    randomAccessWriter.writeBytes("RIFF");
                    randomAccessWriter.writeInt(0); // Final file size not known yet, write 0
                    randomAccessWriter.writeBytes("WAVE");
                    randomAccessWriter.writeBytes("fmt ");
                    randomAccessWriter.writeInt(Integer.reverseBytes(16)); // Sub-chunk size, 16 for PCM
                    randomAccessWriter.writeShort(Short.reverseBytes((short) 1)); // AudioFormat, 1 for PCM
                    randomAccessWriter.writeShort(Short.reverseBytes(nChannels));// Number of channels, 1 for mono, 2 for stereo
                    randomAccessWriter.writeInt(Integer.reverseBytes(sRate)); // Sample rate
                    randomAccessWriter.writeInt(Integer.reverseBytes(sRate*nChannels*mBitsPersample/8)); // Byte rate, SampleRate*NumberOfChannels*mBitsPersample/8
                    randomAccessWriter.writeShort(Short.reverseBytes((short)(nChannels*mBitsPersample/8))); // Block align, NumberOfChannels*mBitsPersample/8
                    randomAccessWriter.writeShort(Short.reverseBytes(mBitsPersample)); // Bits per sample
                    randomAccessWriter.writeBytes("data");
                    randomAccessWriter.writeInt(0); // Data chunk size not known yet, write 0
                    buffer = new byte[mPeriodInFrames*mBitsPersample/8*nChannels];
                    state = State.READY;


                } else {
                    Log.e(WavAudioRecorder.class.getName(), "prepare() method called on uninitialized recorder");
                    state = State.ERROR;
                }
            } else {
                Log.e(WavAudioRecorder.class.getName(), "prepare() method called on illegal state");
                release();
                state = State.ERROR;
            }
        } catch(Exception e) {
            if (e.getMessage() != null) {
                Log.e(WavAudioRecorder.class.getName(), e.getMessage());
            } else {
                Log.e(WavAudioRecorder.class.getName(), "Unknown error occured in prepare()");
            }
            state = State.ERROR;
        }
    }

    /**
     *
     *
     *  Releases the resources associated with this class, and removes the unnecessary files, when necessary
     *
     */
    public void release() {
        if (state == State.RECORDING) {
            stop();


        } else {
            if (state == State.READY){
                try {
                    randomAccessWriter.close(); // Remove prepared file



                } catch (IOException e) {
                    Log.e(WavAudioRecorder.class.getName(), "I/O exception occured while closing output file");
                }
                //new File(filePath).delete();
            }
        }

        if (audioRecorder != null) {
            audioRecorder.release();
        }
    }

    /**
     *
     *
     * Resets the recorder to the INITIALIZING state, as if it was just created.
     * In case the class was in RECORDING state, the recording is stopped.
     * In case of exceptions the class is set to the ERROR state.
     *
     */
    public void reset() {
        try {
            if (state != State.ERROR) {
                release();
                filePath = null; // Reset file path
                audioRecorder = new AudioRecord(mAudioSource, sRate, nChannels, aFormat, mBufferSize);
                if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    throw new Exception("AudioRecord initialization failed");
                }
                audioRecorder.setRecordPositionUpdateListener(updateListenerNew);
                audioRecorder.setPositionNotificationPeriod(mPeriodInFrames);
                state = State.INITIALIZING;
            }
        } catch (Exception e) {
            Log.e(WavAudioRecorder.class.getName(), e.getMessage());
            state = State.ERROR;
        }
    }

    /**
     *
     *
     * Starts the recording, and sets the state to RECORDING.
     * Call after prepare().
     *
     */
    public void start() {
        if (state == State.READY) {
            payloadSize = 0;

            AcousticEchoCanceler.create(audioRecorder.getAudioSessionId());
            NoiseSuppressor.create(audioRecorder.getAudioSessionId());
            AutomaticGainControl.create(audioRecorder.getAudioSessionId());

            smoothed = 0;
            lastUpdate = System.currentTimeMillis();
            audioRecorder.startRecording();
            audioRecorder.read(buffer, 0, buffer.length);	//[TODO: is this necessary]read the existing data in audio hardware, but don't do anything
            state = State.RECORDING;
        } else {
            Log.e(WavAudioRecorder.class.getName(), "start() called on illegal state");
            state = State.ERROR;
        }
    }

    /**
     *
     *
     *  Stops the recording, and sets the state to STOPPED.
     * In case of further usage, a reset is needed.
     * Also finalizes the wave file in case of uncompressed recording.
     *
     */
    public void stop() {
        if (state == State.RECORDING) {
            audioRecorder.stop();
            try {
                randomAccessWriter.seek(4); // Write size to RIFF header
                randomAccessWriter.writeInt(Integer.reverseBytes(36+payloadSize));

                randomAccessWriter.seek(40); // Write size to Subchunk2Size field
                randomAccessWriter.writeInt(Integer.reverseBytes(payloadSize));

                randomAccessWriter.close();


                WavInput.WavFile wavFile = null;
                try {
                    wavFile = new WavInput.WavFile(new File(filePath));
                    WavInput.Source source = new WavInput.Source(wavFile);
                    WavOutput.WavOutFile
                        wavOutFile = new WavOutput.WavOutFile(new File(filePath.replace(".wav", "-lf.wav")),
                        source.getFormat());
                    WavOutput.Sink sink = new WavOutput.Sink(wavOutFile);

                    int cutOff = 8000;
                    int size = 40;

                    //@formatter:off
                    AudioFilter filter = FilterGraph
                        .addLevel(new SincLowPassFilter(size, (double) cutOff / source.getFormat().getSampleRate()))
                        .create();
                    //@formatter:on
                    Audio.filterTransfer(source, filter, sink);

                    source.close();
                    sink.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } catch(IOException e) {
                Log.e(WavAudioRecorder.class.getName(), "I/O exception occured while closing output file");
                state = State.ERROR;
            }
            state = State.STOPPED;
        } else {
            Log.e(WavAudioRecorder.class.getName(), "stop() called on illegal state");
            state = State.ERROR;
        }
    }
}
