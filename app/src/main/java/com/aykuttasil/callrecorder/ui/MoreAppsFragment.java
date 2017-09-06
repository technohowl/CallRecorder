package com.aykuttasil.callrecorder.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.ListView;

import com.aykuttasil.callrecorder.R;
import com.aykuttasil.callrecorder.helper.MoreAppsData;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by tarun on 19/04/17.
 */

public class MoreAppsFragment extends ListFragment {

    private int mType = 0;
    private Context mContext;
    private List<Object> mCallsList = new ArrayList<>();
    private MoreAppsListViewAdapter mAdapter;

    public static Fragment newInstance(int type){
        Fragment f = new MoreAppsFragment();
        Bundle b = new Bundle();
        b.putInt("type", type);
        f.setArguments(b);
        return  f;
    }
    @Override public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mType = getArguments().getInt("type");

    }

    Handler handler = new Handler(){
        @Override public void handleMessage(Message msg) {
            super.handleMessage(msg);
            //mCallsList.clear();
            //mCallsList.addAll((ArrayList<LogObject>) msg.obj);
            mAdapter.notifyDataSetChanged();
            if (isResumed()) {
                setListShown(true);
            } else {
                setListShownNoAnimation(true);
            }
        }
    };

    @Override public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //listNativeAdsManager = new NativeAdsManager(mContext,"132225140652670_132225393985978",2);
        //listNativeAdsManager.setListener(this);
        //listNativeAdsManager.loadAds();
        mAdapter =
            new MoreAppsListViewAdapter(mContext, mCallsList);

        ListView lv = getListView();
        lv.setAdapter(mAdapter);
        lv.setCacheColorHint(Color.TRANSPARENT);
        lv.setDivider(getResources().getDrawable(R.drawable.divider));
        lv.setDividerHeight(1);
        lv.setFastScrollEnabled(true);
        Message message = Message.obtain(handler);
        if(mCallsList.size() > 0){
            message.sendToTarget();
        }else {

            getMoreApps(message);
        }
    }

    private void getMoreApps(Message message) {

        mCallsList.add(new MoreAppsData("Love Messages", R.drawable.ic_lovesms, "https://g7ent.app.goo.gl/BJlF", "Love Messages for any romantic occasion"));
        mCallsList.add(new MoreAppsData("Ringtone Maker", R.drawable.ic_ringtone, "https://g7ent.app.goo.gl/I2ST", "Create your own ringtone"));
        mCallsList.add(new MoreAppsData("Video 2 Mp3", R.drawable.ic_video, "https://g7ent.app.goo.gl/4D6z", "Convert any video to mp3 song"));
        mCallsList.add(new MoreAppsData("Love Wallpapers", R.drawable.ic_wallpaper, "https://g7ent.app.goo.gl/bdgq", "Set beautiful love wallpapers"));
        mCallsList.add(new MoreAppsData("SMS Collection", R.drawable.ic_aiosms, "https://g7ent.app.goo.gl/FTol", "All in one sms library"));
        message.sendToTarget();
    }

    @Override public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        MoreAppsData md = (MoreAppsData) l.getItemAtPosition(position);
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(md.getAppUrl())));
    }
}
