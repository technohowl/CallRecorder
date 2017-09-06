package com.aykuttasil.callrecorder;

import android.app.Application;
import com.aykuttasil.callrecorder.records.MyObjectBox;
import com.github.tamir7.contacts.Contacts;
import io.objectbox.BoxStore;

/**
 * Created by tarun on 08/04/17.
 */

public class MyApp extends Application {
    private BoxStore boxStore;
    @Override public void onCreate() {
        super.onCreate();
        boxStore = MyObjectBox.builder().androidContext(this).build();
        Contacts.initialize(this);

    }

    public BoxStore getBoxStore() {
        return boxStore;
    }
}
