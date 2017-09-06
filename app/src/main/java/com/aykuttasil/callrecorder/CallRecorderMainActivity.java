package com.aykuttasil.callrecorder;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import com.appbrain.AppBrain;
import com.aykuttasil.callrecorder.helper.PrefsHelper;
import com.aykuttasil.callrecorder.records.CallRecordListFragment;
import com.aykuttasil.callrecorder.ui.MoreAppsFragment;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nononsenseapps.filepicker.Utils;
import java.io.File;
import java.util.List;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

import static com.aykuttasil.callrecorder.CallRecord.PREF_AUDIO_SOURCE;
import static com.aykuttasil.callrecorder.CallRecord.PREF_DIR_PATH;
import static com.aykuttasil.callrecorder.CallRecord.PREF_IS_INCOMING_RECORD_ENABLED;
import static com.aykuttasil.callrecorder.CallRecord.PREF_IS_OUTGOING_RECORD_ENABLED;
import static com.aykuttasil.callrecorder.CallRecord.PREF_IS_RECORD_ENABLED;
import static com.aykuttasil.callrecorder.CallRecord.PREF_NUM_RECORDS;
import static com.nononsenseapps.filepicker.Utils.getFileForUri;

@RuntimePermissions
public class CallRecorderMainActivity extends AppCompatActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    private CallRecord callRecord;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_recorder_main);
        AppBrain.addTestDevice("861f900217e382c6");
        AppBrain.init(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null)
                    .show();
            }
        });
        fab.setVisibility(View.GONE);
        CallRecorderMainActivityPermissionsDispatcher.getStorageWithCheck(this);
    }


    @NeedsPermission(value = {
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.PROCESS_OUTGOING_CALLS})
    void getStorage() {
        // ...
        //callRecord.startCallRecordService();
        //CallRecord.initService(this);


       /* callRecord = new CallRecord.Builder(this)
            .setRecordFileName("CallRecord")
            .setRecordDirName("CallRecorder")
            .setShowSeed(true)
            .build();

        callRecord.changeReceiver(new MyCallRecordReceiver(callRecord));
        callRecord.enableSaveFile();*/
    }
    @NeedsPermission(value = {
        Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void getStoragePermission() {}
    @NeedsPermission(value = {
        Manifest.permission.RECORD_AUDIO})
    void getRecordingPermission() {}
    @NeedsPermission(value = {
        Manifest.permission.READ_PHONE_STATE})
    void getPhoneStatePermission() {}
    @NeedsPermission(value = {
        Manifest.permission.PROCESS_OUTGOING_CALLS})
    void getOutgoingCallPermission() {}
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_call_recorder_main, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_call_recorder_main, container, false);
            TextView textView = (TextView) rootView.findViewById(R.id.section_label);
            textView.setText(getString(R.string.section_format, getArguments().getInt(ARG_SECTION_NUMBER)));
            return rootView;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            if(position == 0)
                return new CallRecordListFragment();
            if(position == 1)
                return new PreferenceFragment();
            return MoreAppsFragment.newInstance(position + 1);
        }

        @Override public int getCount() {
            // Show 3 total pages.
            return 3;
        }

        @Override public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Call Records";
                case 1:
                    return "Settings";
                case 2:
                    return "Apps";
            }
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB) public static class PreferenceFragment extends
        Fragment implements CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemSelectedListener, View.OnClickListener {

        private static final int REQUEST_DIRECTORY = 1234;
        private Switch callrecord_switch;
        private Switch in_callrecord_switch;
        private Switch out_callrecord_switch;
        private Spinner sourceSpinner;
        private Context mContext;
        private TextView destinationPath;
        private int permissionCheck;
        private Spinner recordsNumber;

        @Nullable @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_settings, container, false);

            callrecord_switch = (Switch) v.findViewById(R.id.callrecord_switch);
            in_callrecord_switch = (Switch) v.findViewById(R.id.in_callrecord_switch);
            out_callrecord_switch = (Switch) v.findViewById(R.id.out_callrecord_switch);
            sourceSpinner = (Spinner) v.findViewById(R.id.sourceSpinner);
            recordsNumber = (Spinner) v.findViewById(R.id.recordsNumber);
            destinationPath = (TextView) v.findViewById(R.id.destinationPath);
            TextView selectDestinationFolder =
                (TextView) v.findViewById(R.id.selectDestinationFolder);
            selectDestinationFolder.setOnClickListener(this);
            return v;
        }

        @Override public void onActivityCreated(@Nullable Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            String path = PrefsHelper.readPrefString(mContext, PREF_DIR_PATH);
            if(path == null){
                PrefsHelper.writePrefString(mContext, PREF_DIR_PATH,
                    Environment.getExternalStorageDirectory().toString() + "/aCallRecorder");
            }
            destinationPath.setText(PrefsHelper.readPrefString(mContext, PREF_DIR_PATH));
            if(PrefsHelper.readPrefBool(mContext, PREF_IS_INCOMING_RECORD_ENABLED))
                in_callrecord_switch.setChecked(true);
            else
                in_callrecord_switch.setChecked(false);

            if(PrefsHelper.readPrefBool(mContext, PREF_IS_OUTGOING_RECORD_ENABLED))
                out_callrecord_switch.setChecked(true);
            else
                out_callrecord_switch.setChecked(false);

            if(PrefsHelper.readPrefBool(mContext, PREF_IS_RECORD_ENABLED))
                callrecord_switch.setChecked(true);
            else {
                callrecord_switch.setChecked(false);
                in_callrecord_switch.setChecked(false);
                out_callrecord_switch.setChecked(false);
            }

            int selection = PrefsHelper.readPrefInt(mContext,PREF_AUDIO_SOURCE);
            int recordsSelection = PrefsHelper.readPrefInt(mContext,PREF_NUM_RECORDS);
            Log.e("tarun", "sourceSpinner found:" + selection);

            if(recordsSelection == 0)
            {
                recordsSelection = 100;
                PrefsHelper.writePrefInt(mContext, PREF_NUM_RECORDS, recordsSelection);
            }
            sourceSpinner.setSelection(selection);

            int i =0;
            String [] sourceList = mContext.getResources().getStringArray(R.array.records_array);
            for(String s: sourceList){
                //Log.e("tarun", "recordsSelection searching:" + recordsSelection + "=>" + s);
                if(recordsSelection == Integer.parseInt(s)){
                    Log.e("tarun", "recordsSelection found:" + recordsSelection + "=>" + i);
                    break;
                }
                i++;
            }
            // if selection is not yet done then..
            if(i == sourceList.length)
                i = 0;
            //Log.e("tarun", "recordsSelection saved:" + recordsSelection + "=>" + i);

            recordsNumber.setSelection(i);

            callrecord_switch.setOnCheckedChangeListener(this);
            in_callrecord_switch.setOnCheckedChangeListener(this);
            out_callrecord_switch.setOnCheckedChangeListener(this);

            sourceSpinner.setOnItemSelectedListener(this);
            recordsNumber.setOnItemSelectedListener(this);
        }

        @Override public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            switch (compoundButton.getId()){
                case R.id.callrecord_switch:
                    permissionCheck = ContextCompat.checkSelfPermission(mContext,
                        Manifest.permission.RECORD_AUDIO);
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                        if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                            CallRecorderMainActivityPermissionsDispatcher.getStorageWithCheck((CallRecorderMainActivity) mContext);
                            return;
                        }else
                            enableRecording(b);
                    }
                    else enableRecording(b);
                    break;
                case R.id.in_callrecord_switch:

                    PrefsHelper.writePrefBool(mContext, PREF_IS_INCOMING_RECORD_ENABLED,b);

                    break;
                case R.id.out_callrecord_switch:
                    permissionCheck = ContextCompat.checkSelfPermission(mContext,
                        Manifest.permission.PROCESS_OUTGOING_CALLS);
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                        if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                            CallRecorderMainActivityPermissionsDispatcher.getOutgoingCallPermissionWithCheck((CallRecorderMainActivity) mContext);
                            return;
                        }else
                            PrefsHelper.writePrefBool(mContext, PREF_IS_OUTGOING_RECORD_ENABLED,b);
                    }
                    PrefsHelper.writePrefBool(mContext, PREF_IS_OUTGOING_RECORD_ENABLED,b);

                    break;
            }
        }

        private void enableRecording(boolean b) {
            PrefsHelper.writePrefBool(mContext, PREF_IS_RECORD_ENABLED,b);
            PrefsHelper.writePrefBool(mContext, PREF_IS_INCOMING_RECORD_ENABLED,b);
            PrefsHelper.writePrefBool(mContext, PREF_IS_OUTGOING_RECORD_ENABLED, b);
            callrecord_switch.setOnCheckedChangeListener(null);
            in_callrecord_switch.setOnCheckedChangeListener(null);
            out_callrecord_switch.setOnCheckedChangeListener(null);
            callrecord_switch.setChecked(b);
            in_callrecord_switch.setChecked(b);
            out_callrecord_switch.setChecked(b);
            callrecord_switch.setOnCheckedChangeListener(this);
            in_callrecord_switch.setOnCheckedChangeListener(this);
            out_callrecord_switch.setOnCheckedChangeListener(this);
        }

        @Override public void onAttach(Context context) {
            super.onAttach(context);
            mContext = context;
        }

        @Override public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if(adapterView.getId() == R.id.sourceSpinner) {
                PrefsHelper.writePrefInt(mContext, PREF_AUDIO_SOURCE, i);
                Log.e("tarun", "sourceSpinner saved:" + i);
            }else{
                String []recordList = mContext.getResources().getStringArray(R.array.records_array);
                PrefsHelper.writePrefInt(mContext, PREF_NUM_RECORDS, Integer.parseInt(recordList[i]));
            }

        }

        @Override public void onNothingSelected(AdapterView<?> adapterView) {

        }

        @Override public void onClick(View view) {
            permissionCheck = ContextCompat.checkSelfPermission(mContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                    CallRecorderMainActivityPermissionsDispatcher.getStoragePermissionWithCheck((CallRecorderMainActivity) mContext);
                    return;
                }else
                    getDirectory();
            }else {
                getDirectory();
            }

        }

        private void getDirectory() {
            Intent i = new Intent(mContext, FilePickerActivity.class);
            // This works if you defined the intent filter
            // Intent i = new Intent(Intent.ACTION_GET_CONTENT);

            // Set these depending on your use case. These are the defaults.
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
            i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);

            // Configure initial directory by specifying a String.
            // You could specify a String like "/storage/emulated/0/", but that can
            // dangerous. Always use Android's API calls to get paths to the SD-card or
            // internal memory.
            i.putExtra(FilePickerActivity.EXTRA_START_PATH, PrefsHelper.readPrefString(mContext, PREF_DIR_PATH));

            startActivityForResult(i, REQUEST_DIRECTORY);
        }

        @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_DIRECTORY && resultCode == Activity.RESULT_OK) {
                // Use the provided utility method to parse the result
                List<Uri> files = Utils.getSelectedFilesFromResult(data);
                if(files.size() <=0 )
                    return;
                // A new utility method is provided to transform the URI to a File object
                File file = getFileForUri(files.get(0));
                // If you want a URI which matches the old return value, you can do
                //Uri fileUri = Uri.fromFile(file);
                handleDirectoryChoice(file.getPath());
            }
        }

        private void handleDirectoryChoice(String stringExtra) {
            PrefsHelper.writePrefString(mContext, PREF_DIR_PATH, stringExtra);
            destinationPath.setText(stringExtra);
            //String path = PrefsHelper.readPrefString(mContext, PREF_DIR_PATH);
            //Log.e("tarun", "path saved:" + path);
        }
    }
}
