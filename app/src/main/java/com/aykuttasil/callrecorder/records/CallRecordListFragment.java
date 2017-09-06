package com.aykuttasil.callrecorder.records;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import com.appbrain.AdListAdapter;
import com.appbrain.AppBrain;
import com.appbrain.mediation.AdmobAdapter;
import com.aykuttasil.callrecorder.BuildConfig;
import com.aykuttasil.callrecorder.CallRecord;
import com.aykuttasil.callrecorder.MyApp;
import com.aykuttasil.callrecorder.R;
import com.aykuttasil.callrecorder.helper.PrefsHelper;
import com.aykuttasil.callrecorder.ui.MusicPlayerDialog;
import io.objectbox.Box;
import io.objectbox.query.Query;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.aykuttasil.callrecorder.CallRecord.PREF_NUM_RECORDS;

/**
 * Created by tarun on 30/04/17.
 */

public class CallRecordListFragment extends ListFragment {
    private Context mContext;
    private List<Object> mCallsList = new ArrayList<>();
    private CallLogsListViewAdapter mAdapter;
    private Query<CallRecordModel> query;
    private Box<CallRecordModel> mscBox;
    private AdListAdapter adapter;

    @Override public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
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
        mAdapter = new CallLogsListViewAdapter(mContext, mCallsList);

        adapter = AppBrain.getAds().wrapListAdapter(mContext, mAdapter);

        ListView lv = getListView();
        lv.setAdapter(adapter);
        lv.setCacheColorHint(Color.TRANSPARENT);
        lv.setDivider(getResources().getDrawable(R.drawable.divider));
        lv.setDividerHeight(1);
        lv.setFastScrollEnabled(true);
        registerForContextMenu(lv);

        mscBox =
            ((MyApp) ((AppCompatActivity) mContext).getApplication()).getBoxStore()
                .boxFor(CallRecordModel.class);


        query = mscBox.query()
            .orderDesc(CallRecordModel_.recordedDate)
            .build();

        Message message = Message.obtain(handler);

        if(mCallsList.size() > 0){
            message.sendToTarget();
        }else {
            getCallDetails(message);
        }
    }


    private void getCallDetails(final Message message) {
        AsyncTask.execute(new Runnable() {
            @Override public void run() {

               /* File path = Environment.getExternalStorageDirectory();
                File directory = new File(path, "CallRecorder");

                File[] files = directory.listFiles();
                for(File f : files)
                    mCallsList.add(f);*/
                List<CallRecordModel> list = query.find();
                int numRecords = PrefsHelper.readPrefInt(mContext, PREF_NUM_RECORDS);
                if(numRecords <= list.size()){
                    query = mscBox.query()
                        .order(CallRecordModel_.recordedDate)
                        .build();
                    List<CallRecordModel> templist = query.find();
                    int i =0;
                    for(CallRecordModel m: templist){
                        if(!TextUtils.isEmpty(m.getRecordLocation())) {
                            File f = new File(m.getRecordLocation());
                            //f.delete();
                        }
                        mscBox.remove(m);
                        i++;
                        if(i == list.size() - numRecords)
                            break;
                    }
                    query = mscBox.query()
                        .orderDesc(CallRecordModel_.recordedDate)
                        .build();
                    list = query.find();
                }

                mCallsList.addAll(list);
                message.sendToTarget();
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.setHeaderTitle("Select The Action");
        menu.add(0, R.id.play_list_item, 0, "Play File");//groupId, itemId, order, title
        menu.add(0, R.id.delete_list_item, 0, "Delete");//groupId, itemId, order, title
        //menu.add(0, v.getId(), 0, "SMS");
    }

    @Override public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
            .getMenuInfo();
        int pos = adapter.getItemPosition(info.position);

        CallRecordModel cm = (CallRecordModel) getListView().getItemAtPosition(pos);
        switch (item.getItemId()){
            case R.id.delete_list_item:
                mscBox.remove(cm);
                mAdapter.removeData(pos);
                mCallsList.remove(pos);
                mAdapter.notifyDataSetChanged();
                break;
            case R.id.play_list_item:

                MusicPlayerDialog.newInstance(
                    cm.getCallerNumber(),
                    cm.getRecordLocation(),
                    cm.getRecordLocation(),
                    cm.getCallType())
                    .show(((AppCompatActivity)mContext).getSupportFragmentManager(), "MusicPlayerDialog");

                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        if(BuildConfig.DEBUG)
            Log.e("tarun", "item type: " +adapter.getItemViewType(position));
        if(adapter.getItemViewType(position) == 0)
        l.showContextMenuForChild(v);
    }
}
