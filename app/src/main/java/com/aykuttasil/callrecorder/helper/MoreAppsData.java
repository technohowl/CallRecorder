package com.aykuttasil.callrecorder.helper;

/**
 * Created by tarun on 23/04/17.
 */

public class MoreAppsData {
    String appName;
    int appIcon;
    String appUrl;
    String appDesc;

    public String getAppName() {
        return appName;
    }

    public int getAppIcon() {
        return appIcon;
    }

    public String getAppUrl() {
        return appUrl;
    }

    public String getAppDesc() {
        return appDesc;
    }

    public MoreAppsData(String appName, int appIcon, String appUrl, String appDesc) {
        this.appName = appName;
        this.appIcon = appIcon;
        this.appUrl = appUrl;
        this.appDesc = appDesc;
    }
}
