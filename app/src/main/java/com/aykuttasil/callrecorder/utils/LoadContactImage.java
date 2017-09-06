package com.aykuttasil.callrecorder.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import com.aykuttasil.callrecorder.records.CallLogsListViewAdapter;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Created by tarun on 01/05/17.
 */

public class LoadContactImage extends AsyncTask<Object, Void, Bitmap> {

    private String Path;
    private CallLogsListViewAdapter.ViewHolder mHolder;
    private int mposition;
    Context ctx;

    public LoadContactImage(Context context,CallLogsListViewAdapter.ViewHolder holder,int position,String id) {
        this.mHolder= holder;
        this.Path = id;
        this.mposition=position;
        this.ctx= context;
    }

    @Override
    protected Bitmap doInBackground(Object... params) {
        Uri my_uri = Uri.parse(Path);
        ContentResolver cr = ctx.getContentResolver();
        InputStream in = null;
        try {
            in = cr.openInputStream(my_uri);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return BitmapFactory.decodeStream(in);
    }

    @Override
    protected void onPostExecute(Bitmap result) {

        super.onPostExecute(result);
        if (result != null && mposition == mHolder.position) {
            //imv.setImageBitmap(result);
            mHolder.getCallOperatorImage().setImageBitmap(result);
        }
    }
}