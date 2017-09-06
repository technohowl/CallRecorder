package com.aykuttasil.callrecorder.ui;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aykuttasil.callrecorder.R;
import com.aykuttasil.callrecorder.helper.MoreAppsData;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by tarun on 19/04/17.
 */

public class MoreAppsListViewAdapter extends ArrayAdapter<Object> {

    private final List<Object> mItems;
    private final Context _context;
    private final LayoutInflater inflate;

    public MoreAppsListViewAdapter(@NonNull Context context,
        @NonNull List<Object> objects) {
        super(context, 0, objects);
        _context = context;
        inflate = LayoutInflater.from(context);
        mItems = objects;



    }

    @Override public int getCount() {
        //Log.e("mnp", "mItems:" + mItems.size());

        return mItems.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        try{

                if (convertView == null) {
                    convertView = inflate.inflate(R.layout.call_log_row, parent, false);
                    viewHolder = new ViewHolder();
                    viewHolder.callNumber = (TextView) convertView.findViewById(R.id.callNumber);
                    viewHolder.callName = (TextView) convertView.findViewById(R.id.callName);
                    viewHolder.callDuration = (TextView) convertView.findViewById(R.id.callDuration);
                    viewHolder.callTime = (TextView) convertView.findViewById(R.id.callTime);
                    viewHolder.callState = (TextView) convertView.findViewById(R.id.callState);
                    viewHolder.callOperator = (TextView) convertView.findViewById(R.id.callOperator);
                    viewHolder.callType = (ImageView) convertView.findViewById(R.id.callTypeImage);
                    viewHolder.callOperatorImage = (ImageView) convertView.findViewById(R.id.operatorImage);
                    convertView.setTag(viewHolder);
                } else {
                    viewHolder = (ViewHolder) convertView.getTag();
                }

                //viewHolder.callNumber.setText(mb.getNumber());
            viewHolder.callNumber.setVisibility(View.VISIBLE);
            viewHolder.callDuration.setVisibility(View.GONE);
            viewHolder.callTime.setVisibility(View.GONE);
            viewHolder.callState.setVisibility(View.GONE);
            viewHolder.callType.setVisibility(View.GONE);

            MoreAppsData md = (MoreAppsData) mItems.get(position);

            viewHolder.callName.setText(md.getAppName());
            viewHolder.callOperator.setText(md.getAppDesc());
            viewHolder.callOperatorImage.setImageDrawable(_context.getResources().getDrawable(md.getAppIcon()));
            viewHolder.callNumber.setText("Sponsored");
            return convertView;


        }catch (Exception e ){
            e.printStackTrace();
        }

        return convertView;
    }



    public class ViewHolder {
        TextView callName;
        TextView callNumber;
        TextView callTime;
        TextView callDuration;
        TextView callState;
        TextView callOperator;
        ImageView callType;
        ImageView callOperatorImage;
    }
}
