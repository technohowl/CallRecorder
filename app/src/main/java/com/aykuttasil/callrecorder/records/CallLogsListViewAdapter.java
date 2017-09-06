package com.aykuttasil.callrecorder.records;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.aykuttasil.callrecorder.MyApp;

import com.aykuttasil.callrecorder.R;
import com.aykuttasil.callrecorder.utils.LoadContactImage;
import com.github.tamir7.contacts.Contact;
import com.github.tamir7.contacts.Contacts;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import de.hdodenhof.circleimageview.CircleImageView;
import io.objectbox.Box;
import io.objectbox.query.Query;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Created by tarun on 19/04/17.
 */

public class CallLogsListViewAdapter extends ArrayAdapter<Object> {

    private final List<Object> mItems;
    private final Context _context;
    private final LayoutInflater inflate;
    private final Box<CallRecordModel> mscBox;
    private final Query<CallRecordModel> query;
    private final PhoneNumberUtil mPhoneUtil;
    private final com.github.tamir7.contacts.Query cQuery;

    public CallLogsListViewAdapter(@NonNull Context context,
        @NonNull List<Object> objects) {
        super(context, 0, objects);
        _context = context;
        inflate = LayoutInflater.from(context);
        mItems = objects;
        mscBox = ((MyApp) ((AppCompatActivity)_context).getApplication()).getBoxStore().boxFor(CallRecordModel.class);
        cQuery = Contacts.getQuery();

        cQuery.include(Contact.Field.DisplayName, Contact.Field.Email, Contact.Field.PhotoUri);

        query = mscBox.query().equal(CallRecordModel_.recordedDate, 0).build();
        mPhoneUtil = PhoneNumberUtil.getInstance();
    }

    @Override public int getCount() {
        //Log.e("mnp", "mItems:" + mItems.size());

        return mItems.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder viewHolder;
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
                viewHolder.callOperatorImage = (CircleImageView) convertView.findViewById(R.id.operatorImage);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            CallRecordModel mb = (CallRecordModel) mItems.get(position);
            //viewHolder.callNumber.setText(mb.getNumber());
            String callDate = new DateFormat().format("dd-MMM k:m", mb.getRecordedDate()).toString();
            String timeDate = format((int) mb.getCallDuration() /1000 );
            viewHolder.callTime.setText(callDate);
            viewHolder.callDuration.setText("(" + timeDate + ")");
            viewHolder.position = position;

            viewHolder.callNumber.setText(mb.getCallerNumber());
            viewHolder.callName.setTextColor(_context.getResources().getColor(R.color.textColor));
            /*Phonenumber.PhoneNumber usNumberProto = mPhoneUtil.parse(mb.getCallerNumber(), "US");

            String number = mPhoneUtil.format(usNumberProto, PhoneNumberUtil.PhoneNumberFormat.E164);
*/

            if(!mb.isSearched) {
                com.github.tamir7.contacts.Query q = Contacts.getQuery();
                q.include(Contact.Field.DisplayName, Contact.Field.Email, Contact.Field.PhotoUri);
                q.whereContains(Contact.Field.PhoneNumber, mb.getCallerNumber());
                List<Contact> contacts = q.find();
                mb.isSearched = true;
                mb.contacts = contacts;
                //mContactsMap.put(position, contacts);
            }
            else{
                //mb.contacts = new ArrayList<>();
                //contacts = (List<Contact>) viewHolder.callOperatorImage.getTag();
            }

            viewHolder.contacts = mb.contacts;

            //Log.e("tarun", "posd:"+position+"=>"+mb.getCallerNumber()+"=>" + viewHolder.contacts.size());

            if(viewHolder.contacts.size() > 0) {
                viewHolder.callName.setText("" + viewHolder.contacts.get(0).getDisplayName());
                viewHolder.callOperatorImage.setImageURI(Uri.parse(viewHolder.contacts.get(0).getPhotoUri()));

                new LoadContactImage(getContext(), viewHolder, position, viewHolder.contacts.get(0).getPhotoUri()).execute();
            }
            else
            {
                viewHolder.callName.setText(""+mb.getCallerNumber());
                viewHolder.callOperatorImage.setImageURI(null);
                viewHolder.callOperatorImage.setImageResource(android.R.drawable.sym_contact_card);
            }


            switch (mb.getCallType()) {
                case 0:
                    viewHolder.callType.setImageDrawable(_context.getResources().getDrawable(android.R.drawable.sym_call_incoming));
                    break;
                default:
                    viewHolder.callType.setImageDrawable(_context.getResources().getDrawable(android.R.drawable.sym_call_outgoing));
                    break;

            }
            try {
                String filename=mb.getRecordLocation().substring(mb.getRecordLocation().lastIndexOf("/")+1);
                viewHolder.callState.setText(filename);
            } catch (Exception e) {
                viewHolder.callState.setText(R.string.not_found);
            }
            return convertView;


        }catch (Exception e ){
            e.printStackTrace();
        }

        return convertView;
    }

    private String format(int seconds) {
        final SimpleDateFormat dateFormat;
        String appendString="";
        if (seconds < 60) {
            dateFormat = new SimpleDateFormat("ss");
            appendString = " s";
        } else if (seconds < 60 * 60) {
            dateFormat = new SimpleDateFormat("m:ss");
        } else {
            dateFormat = new SimpleDateFormat("H:mm:ss");
        }
        final Calendar gmt = Calendar.getInstance(Locale.ENGLISH);
        gmt.set(1970, Calendar.JANUARY, 1, 0, 0, 0);
        gmt.add(Calendar.SECOND, seconds);
        return dateFormat.format(gmt.getTime()) + appendString;
    }

    public void removeData(int position) {
    }

    public class ViewHolder {
        TextView callName;
        TextView callNumber;
        TextView callTime;
        TextView callDuration;
        TextView callState;
        TextView callOperator;
        ImageView callType;
        List<Contact> contacts;
        CircleImageView callOperatorImage;
        public int position;

        public CircleImageView getCallOperatorImage() {
            return callOperatorImage;
        }
    }
}
