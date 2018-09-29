/*
 * Copyright (c) 2016-2018. Vijai Chandra Prasad R.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses
 */

package com.orpheusdroid.screenrecorder.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;
import com.orpheusdroid.screenrecorder.folderpicker.FolderChooser;
import com.orpheusdroid.screenrecorder.folderpicker.OnDirectorySelectedListerner;
import com.orpheusdroid.screenrecorder.interfaces.PermissionResultListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import androidx.annotation.NonNull;

/**
 * <p>
 *     This fragment handles various settings of the recorder.
 * </p>
 *
 * @author Vijai Chandra Prasad .R
 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener
 * @see PermissionResultListener
 * @see OnDirectorySelectedListerner
 * @see MainActivity.AnalyticsSettingsListerner
 */
public class SettingsPreferenceFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener
        , PermissionResultListener, OnDirectorySelectedListerner, MainActivity.AnalyticsSettingsListerner {

    /**
     * SharedPreferences object to read the persisted settings
     */
    SharedPreferences prefs;

    /**
     * ListPreference to choose the recording resolution
     */
    private ListPreference res;

    /**
     * ListPreference to manage audio recording via mic setting
     */
    private ListPreference recaudio;

    /**
     * CheckBoxPreference to manage onscreen floating control setting
     */
    private CheckBoxPreference floatingControl;

    /**
     * CheckBoxPreference to manage crash reporting via countly setting
     */
    private CheckBoxPreference crashReporting;

    /**
     * CheckBoxPreference to manage full analytics via countly setting
     */
    private CheckBoxPreference usageStats;

    /**
     * FolderChooser object to choose the directory where the video has to be saved to.
     */
    private FolderChooser dirChooser;

    /**
     * SwitchPreference for camera to show overlay
     */
    private CheckBoxPreference cameraOverlay;

    /**
     * MainActivity object
     */
    private MainActivity activity;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

    }

    /**
     * Initialize various listeners and settings preferences.
     *
     * @param savedInstanceState default savedInstance bundle sent by Android runtime
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        //init permission listener callback
        setPermissionListener();

        setAnalyticsPermissionListerner();

        //Get Default save location from shared preference
        String defaultSaveLoc = (new File(Environment
                .getExternalStorageDirectory() + File.separator + Const.APPDIR)).getPath();

        //Get instances of all preferences
        prefs = getPreferenceScreen().getSharedPreferences();
        res = (ListPreference) findPreference(getString(R.string.res_key));
        ListPreference fps = (ListPreference) findPreference(getString(R.string.fps_key));
        ListPreference bitrate = (ListPreference) findPreference(getString(R.string.bitrate_key));
        recaudio = (ListPreference) findPreference(getString(R.string.audiorec_key));
        ListPreference filenameFormat = (ListPreference) findPreference(getString(R.string.filename_key));
        EditTextPreference filenamePrefix = (EditTextPreference) findPreference(getString(R.string.fileprefix_key));
        dirChooser = (FolderChooser) findPreference(getString(R.string.savelocation_key));
        floatingControl = (CheckBoxPreference) findPreference(getString(R.string.preference_floating_control_key));
        CheckBoxPreference touchPointer = (CheckBoxPreference) findPreference("touch_pointer");
        crashReporting = (CheckBoxPreference) findPreference(getString(R.string.preference_crash_reporting_key));
        usageStats = (CheckBoxPreference) findPreference(getString(R.string.preference_anonymous_statistics_key));
        //Set previously chosen directory as initial directory
        dirChooser.setCurrentDir(getValue(getString(R.string.savelocation_key), defaultSaveLoc));
        cameraOverlay = (CheckBoxPreference) findPreference(getString(R.string.preference_camera_overlay_key));

        ListPreference orientation = (ListPreference) findPreference(getString(R.string.orientation_key));
        orientation.setSummary(orientation.getEntry());

        ListPreference theme = (ListPreference) findPreference(getString(R.string.preference_theme_key));
        theme.setSummary(theme.getEntry());

        //Set the summary of preferences dynamically with user choice or default if no user choice is made
        updateResolution(res);
        updateScreenAspectRatio();
        fps.setSummary(getValue(getString(R.string.fps_key), "30"));
        float bps = bitsToMb(Integer.parseInt(getValue(getString(R.string.bitrate_key), "7130317")));
        bitrate.setSummary(bps + " Mbps");
        dirChooser.setSummary(getValue(getString(R.string.savelocation_key), defaultSaveLoc));
        filenameFormat.setSummary(getFileSaveFormat());
        filenamePrefix.setSummary(getValue(getString(R.string.fileprefix_key), "recording"));

        checkAudioRecPermission();

        //If floating controls is checked, check for system windows permission
        if (floatingControl.isChecked())
            requestSystemWindowsPermission(Const.FLOATING_CONTROLS_SYSTEM_WINDOWS_CODE);

        if (cameraOverlay.isChecked()) {
            requestCameraPermission();
            requestSystemWindowsPermission(Const.CAMERA_SYSTEM_WINDOWS_CODE);
        }

        if(touchPointer.isChecked()){
            if (!hasPluginInstalled())
                touchPointer.setChecked(false);
        }

        //set callback for directory change
        dirChooser.setOnDirectoryClickedListerner(this);
    }

    private void checkAudioRecPermission() {
        String value = recaudio.getValue();
        switch (value) {
            case "1":
                requestAudioPermission(Const.AUDIO_REQUEST_CODE);
                break;
            case "2":
                requestAudioPermission(Const.INTERNAL_AUDIO_REQUEST_CODE);
                break;
        }
        recaudio.setSummary(recaudio.getEntry());
    }

    /**
     * Updates the summary of resolution settings preference
     *
     * @param pref object of the resolution ListPreference
     */
    private void updateResolution(ListPreference pref) {
        String resolution = getValue(getString(R.string.res_key), getNativeRes());
        if (resolution.toLowerCase().contains("x")) {
            resolution = getNativeRes();
            pref.setValue(resolution);
        }
        pref.setSummary(resolution + "P");
    }

    /**
     * Method to get the device's native resolution
     *
     * @return device resolution
     */
    private String getNativeRes() {
        DisplayMetrics metrics = getRealDisplayMetrics();
        return String.valueOf(getScreenWidth(metrics));
    }

    /**
     * Updates the available resolution based on aspect ratio
     */
    private void updateScreenAspectRatio() {
        CharSequence[] entriesValues = getResolutionEntriesValues();
        res.setEntries(getResolutionEntries(entriesValues));
        //res.setEntries(entriesValues);
        res.setEntryValues(entriesValues);
    }

    /**
     * Get resolutions based on the device's aspect ratio
     *
     * @return entries for the resolution
     */
    private CharSequence[] getResolutionEntriesValues() {

        ArrayList<String> entrieValues = buildEntries(R.array.resolutionValues);

        String[] entriesArray = new String[entrieValues.size()];
        return entrieValues.toArray(entriesArray);
    }

    private CharSequence[] getResolutionEntries(CharSequence[] entriesValues) {
        ArrayList<String> entries = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.resolutionsArray)));
        ArrayList<String> newEntries = new ArrayList<>();
        for (CharSequence values : entriesValues) {
            Log.d(Const.TAG, "res entries:" + values.toString());
            for (String entry : entries) {
                if (entry.contains(values))
                    newEntries.add(entry);
            }
            Log.d(Const.TAG, "res entries: split " + values.toString().split("P")[0] + " val: ");
        }
        Log.d(Const.TAG, "res entries" + newEntries.toString());
        String[] entriesArray = new String[newEntries.size()];
        return newEntries.toArray(entriesArray);
    }

    /**
     * Build resolutions from the arrays.
     *
     * @param resID resource ID for the resolution array
     * @return ArrayList of available resolutions
     */
    private ArrayList<String> buildEntries(int resID) {
        DisplayMetrics metrics = getRealDisplayMetrics();
        int deviceWidth = getScreenWidth(metrics);
        ArrayList<String> entries = new ArrayList<>(Arrays.asList(getResources().getStringArray(resID)));
        Iterator<String> entriesIterator = entries.iterator();
        while (entriesIterator.hasNext()) {
            String width = entriesIterator.next();
            if (deviceWidth < Integer.parseInt(width)) {
                entriesIterator.remove();
            }
        }
        if (!entries.contains("" + deviceWidth))
            entries.add("" + deviceWidth);
        return entries;
    }


    /**
     * Returns object of DisplayMetrics
     *
     * @return DisplayMetrics
     */
    private DisplayMetrics getRealDisplayMetrics(){
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager window = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
        window.getDefaultDisplay().getRealMetrics(metrics);
        return metrics;
    }


    /**
     * Get width of screen in pixels
     *
     * @return screen width
     */
    private int getScreenWidth(DisplayMetrics metrics) {
        return metrics.widthPixels;
    }

    /**
     * Get height of screen in pixels
     *
     * @return Screen height
     */
    private int getScreenHeight(DisplayMetrics metrics) {
        return metrics.heightPixels;
    }


    /**
     * Get aspect ratio of the screen
     */
    @Deprecated
    private Const.ASPECT_RATIO getAspectRatio() {
        float screen_width = getScreenWidth(getRealDisplayMetrics());
        float screen_height = getScreenHeight(getRealDisplayMetrics());
        float aspectRatio;
        if (screen_width > screen_height) {
            aspectRatio = screen_width / screen_height;
        } else {
            aspectRatio = screen_height / screen_width;
        }
        return Const.ASPECT_RATIO.valueOf(aspectRatio);
    }

    /**
     * Set permission listener in the {@link MainActivity} to handle permission results
     */
    private void setPermissionListener() {
        if (getActivity() != null && getActivity() instanceof MainActivity) {
            activity = (MainActivity) getActivity();
            activity.setPermissionResultListener(this);
        }
    }

    /**
     * Set Analytics permission listener in {@link MainActivity} to listen to analytics permission changes
     */
    private void setAnalyticsPermissionListerner(){
        if (getActivity() != null && getActivity() instanceof MainActivity) {
            activity = (MainActivity) getActivity();
            activity.setAnalyticsSettingsListerner(this);
        }
    }

    /**
     * Get the persisted value for the preference from default sharedPreference
     *
     * @param key String represnting the sharedpreference key to fetch
     * @param defVal String Default value if the preference does not exist
     * @return String the persisted preference value or default if not found
     */
    private String getValue(String key, String defVal) {
        return prefs.getString(key, defVal);
    }

    /**
     * Method to convert bits per second to MB/s
     *
     * @param bps float bitsPerSecond
     * @return float
     */
    private float bitsToMb(float bps) {
        return bps / (1024 * 1024);
    }

    //Register for OnSharedPreferenceChangeListener when the fragment resumes
    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    //Unregister for OnSharedPreferenceChangeListener when the fragment pauses
    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    //When user changes preferences, update the summary accordingly
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        Preference pref = findPreference(s);
        if (pref == null) return;
        switch (pref.getTitleRes()) {
            case R.string.preference_resolution_title:
                updateResolution((ListPreference) pref);
                break;
            case R.string.preference_fps_title:
                String fps = String.valueOf(getValue(getString(R.string.fps_key), "30"));
                pref.setSummary(fps);
                break;
            case R.string.preference_bit_title:
                float bps = bitsToMb(Integer.parseInt(getValue(getString(R.string.bitrate_key), "7130317")));
                pref.setSummary(bps + " Mbps");
                if (bps > 12)
                    Toast.makeText(getActivity(), R.string.toast_message_bitrate_high_warning, Toast.LENGTH_SHORT).show();
                break;
            case R.string.preference_filename_format_title:
                pref.setSummary(getFileSaveFormat());
                break;
            case R.string.preference_audio_record_title:
                switch (recaudio.getValue()) {
                    case "1":
                        requestAudioPermission(Const.AUDIO_REQUEST_CODE);
                        break;
                    case "2":
                        if (!prefs.getBoolean(Const.PREFS_INTERNAL_AUDIO_DIALOG_KEY, false))
                            showInternalAudioWarning(false);
                        else
                            requestAudioPermission(Const.INTERNAL_AUDIO_REQUEST_CODE);
                        break;
                    case "3":
                        if (!prefs.getBoolean(Const.PREFS_INTERNAL_AUDIO_DIALOG_KEY, false))
                            showInternalAudioWarning(true);
                        else
                            requestAudioPermission(Const.INTERNAL_R_SUBMIX_AUDIO_REQUEST_CODE);
                        break;
                    default:
                        recaudio.setValue("0");
                        break;
                }
                pref.setSummary(((ListPreference) pref).getEntry());
                break;
            case R.string.preference_filename_prefix_title:
                EditTextPreference etp = (EditTextPreference) pref;
                etp.setSummary(etp.getText());
                ListPreference filename = (ListPreference) findPreference(getString(R.string.filename_key));
                filename.setSummary(getFileSaveFormat());
                break;
            case R.string.preference_floating_control_title:
                requestSystemWindowsPermission(Const.FLOATING_CONTROLS_SYSTEM_WINDOWS_CODE);
                break;
            case R.string.preference_show_touch_title:
                CheckBoxPreference showTouchCB = (CheckBoxPreference)pref;
                if (showTouchCB.isChecked() && !hasPluginInstalled()){
                    showTouchCB.setChecked(false);
                    showDownloadAlert();
                }
                break;
            case R.string.preference_crash_reporting_title:
                CheckBoxPreference crashReporting = (CheckBoxPreference)pref;
                CheckBoxPreference anonymousStats = (CheckBoxPreference) findPreference(getString(R.string.preference_anonymous_statistics_key));
                if(!crashReporting.isChecked())
                    anonymousStats.setChecked(false);
            case R.string.preference_anonymous_statistics_title:
                Toast.makeText(getActivity(), R.string.toast_message_countly_activity_restart, Toast.LENGTH_SHORT).show();
                activity.recreate();
                break;
            case R.string.preference_theme_title:
                activity.recreate();
                break;
            case R.string.preference_orientation_title:
                pref.setSummary(((ListPreference) pref).getEntry());
                break;
            case R.string.preference_camera_overlay_title:
                requestCameraPermission();
                break;
        }
    }

    /**
     * show an alert to download the plugin when the plugin is not found
     */
    private void showDownloadAlert() {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.alert_plugin_not_found_title)
                .setMessage(R.string.alert_plugin_not_found_message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        try {
                            getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.orpheusdroid.screencamplugin")));
                        } catch (android.content.ActivityNotFoundException e) { // if there is no Google Play on device
                            getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.orpheusdroid.screencamplugin")));
                        }
                    }
                })
                .setNeutralButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .create().show();
    }

    private void showInternalAudioWarning(boolean isR_submix) {
        int message;
        final int requestCode;
        if (isR_submix) {
            message = R.string.alert_dialog_r_submix_audio_warning_message;
            requestCode = Const.INTERNAL_R_SUBMIX_AUDIO_REQUEST_CODE;
        } else {
            message = R.string.alert_dialog_internal_audio_warning_message;
            requestCode = Const.INTERNAL_AUDIO_REQUEST_CODE;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.alert_dialog_internal_audio_warning_title)
                .setMessage(message)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        requestAudioPermission(requestCode);

                    }
                })
                .setNegativeButton(R.string.alert_dialog_internal_audio_warning_negative_btn_text, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        prefs.edit().putBoolean(Const.PREFS_INTERNAL_AUDIO_DIALOG_KEY, true)
                                .apply();
                        requestAudioPermission(Const.INTERNAL_AUDIO_REQUEST_CODE);
                    }
                })
                .create()
                .show();
    }
    /**
     * Check if "show touches" plugin is installed.
     *
     * @return boolean
     */
    private boolean hasPluginInstalled(){
        PackageManager pm = getActivity().getPackageManager();
        try {
            pm.getPackageInfo("com.orpheusdroid.screencamplugin",PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(Const.TAG, "Plugin not installed");
            return false;
        }
        return true;
    }

    /**
     * Method to concat file prefix with dateTime format
     */
    public String getFileSaveFormat() {
        String filename = prefs.getString(getString(R.string.filename_key), "yyyyMMdd_hhmmss");
        String prefix = prefs.getString(getString(R.string.fileprefix_key), "recording");
        return prefix + "_" + filename;
    }

    /**
     * Method to request android permission to record audio
     */
    public void requestAudioPermission(int requestCode) {
        if (activity != null) {
            activity.requestPermissionAudio(requestCode);
        }
    }

    /**
     * Method to request Camera permission
     */
    public void requestCameraPermission() {
        if (activity != null)
            activity.requestPermissionCamera();
    }

    /**
     * Method to request android system windows permission to show floating controls
     * <p>
     *     Shown only on devices above api 23 (Marshmallow)
     * </p>
     */
    private void requestSystemWindowsPermission(int code) {
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestSystemWindowsPermission(code);
        } else {
            Log.d(Const.TAG, "API is < 23");
        }
    }

    /**
     * Show snackbar with permission Intent when the user rejects write storage permission
     */
    private void showSnackbar() {
        Snackbar.make(getActivity().findViewById(R.id.fab), R.string.snackbar_storage_permission_message,
                Snackbar.LENGTH_INDEFINITE).setAction(R.string.snackbar_storage_permission_action_enable,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (activity != null){
                            activity.requestPermissionStorage();
                        }
                    }
                }).show();
    }

    /**
     * Show a dialog when the permission to storage is denied by the user during startup
     */
    private void showPermissionDeniedDialog(){
        new AlertDialog.Builder(activity)
                .setTitle(R.string.alert_permission_denied_title)
                .setMessage(R.string.alert_permission_denied_message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (activity != null){
                            activity.requestPermissionStorage();
                        }
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showSnackbar();
                    }
                })
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(false)
                .create().show();
    }

    //Permission result callback to process the result of Marshmallow style permission request
    @Override
    public void onPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case Const.EXTDIR_REQUEST_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_DENIED)) {
                    Log.d(Const.TAG, "Storage permission denied. Requesting again");
                    dirChooser.setEnabled(false);
                    showPermissionDeniedDialog();
                } else if((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)){
                    dirChooser.setEnabled(true);
                }
                return;
            case Const.AUDIO_REQUEST_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(Const.TAG, "Record audio permission granted.");
                    recaudio.setValue("1");
                } else {
                    Log.d(Const.TAG, "Record audio permission denied");
                    recaudio.setValue("0");
                }
                recaudio.setSummary(recaudio.getEntry());
                return;
            case Const.INTERNAL_AUDIO_REQUEST_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(Const.TAG, "Record audio permission granted.");
                    recaudio.setValue("2");
                } else {
                    Log.d(Const.TAG, "Record audio permission denied");
                    recaudio.setValue("0");
                }
                recaudio.setSummary(recaudio.getEntry());
                return;
            case Const.INTERNAL_R_SUBMIX_AUDIO_REQUEST_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(Const.TAG, "Record audio permission granted.");
                    recaudio.setValue("3");
                } else {
                    Log.d(Const.TAG, "Record audio permission denied");
                    recaudio.setValue("0");
                }
                recaudio.setSummary(recaudio.getEntry());
                return;
            case Const.FLOATING_CONTROLS_SYSTEM_WINDOWS_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(Const.TAG, "System Windows permission granted");
                    floatingControl.setChecked(true);
                } else {
                    Log.d(Const.TAG, "System Windows permission denied");
                    floatingControl.setChecked(false);
                }
                return;
            case Const.CAMERA_SYSTEM_WINDOWS_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(Const.TAG, "System Windows permission granted");
                    cameraOverlay.setChecked(true);
                } else {
                    Log.d(Const.TAG, "System Windows permission denied");
                    cameraOverlay.setChecked(false);
                }
                return;
            case Const.CAMERA_REQUEST_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(Const.TAG, "System Windows permission granted");
                    requestSystemWindowsPermission(Const.CAMERA_SYSTEM_WINDOWS_CODE);
                } else {
                    Log.d(Const.TAG, "System Windows permission denied");
                    cameraOverlay.setChecked(false);
                }
            default:
                Log.d(Const.TAG, "Unknown permission request with request code: " + requestCode);
        }
    }

    @Override
    public void onDirectorySelected() {
        Log.d(Const.TAG, "In settings fragment");
        if (getActivity() != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onDirectoryChanged();
        }
    }

    @Override
    public void updateAnalyticsSettings(Const.analytics analytics) {
        switch (analytics){
            case CRASHREPORTING:
                crashReporting.setChecked(true);
                break;
            case USAGESTATS:
                usageStats.setChecked(true);
                break;
        }
    }

    /**
     * Start analytics service with user chosen config
     */
    private void startAnalytics(){
        if (getActivity() != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setupAnalytics();
        }
    }
}
