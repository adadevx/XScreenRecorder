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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.orpheusdroid.screenrecorder.BuildConfig;
import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.DonateActivity;
import com.orpheusdroid.screenrecorder.R;
import com.orpheusdroid.screenrecorder.ScreenCamApp;
import com.orpheusdroid.screenrecorder.interfaces.PermissionResultListener;
import com.orpheusdroid.screenrecorder.services.RecorderService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.legacy.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import ly.count.android.sdk.Countly;
import ly.count.android.sdk.DeviceId;

/**
 * <p>
 * This class is the launcher activity of the app which handles
 * various permissions of the app. One of the main function of this activity is to manage the
 * lifecycle of two important fragments:
 * <br />
 * <ul>
 * <li><a href="SettingsPreferenceFragment">SettingsPreferenceFragment</a></li>
 * <li><a href="VideosListFragment">VideosListFragment</a></li>
 * </ul></p>
 * <br />
 * This class also manages of the most important function of the app: Creating a mediaprojection object
 * and starting the screen recording service.
 *
 * @author Vijai Chandra Prasad .R
 * @see SettingsPreferenceFragment
 * @see VideosListFragment
 * @see RecorderService
 */

public class MainActivity extends AppCompatActivity {

    /**
     * Permission listener interface to listen to permission results obtained from
     * <a href="SettingsPreferenceFragment">SettingsPreferenceFragment</a>
     *
     * @see PermissionResultListener
     */
    private PermissionResultListener mPermissionResultListener;

    /**
     * Interface to listen to settings changes pertaining to analytics.
     * This is used to enable/disable sdk depending on the user's settings in realtime (without waiting for app restart)
     *
     * @see MainActivity.AnalyticsSettingsListerner
     */
    private AnalyticsSettingsListerner analyticsSettingsListerner;

    /**
     * MediaProjection token to hold screen capture permission grant
     */
    private MediaProjection mMediaProjection;

    /**
     * Instance of {@link MediaProjectionManager} system service
     */
    private MediaProjectionManager mProjectionManager;

    /**
     * {@link FloatingActionButton} view which handles the record start/stop action
     */
    private FloatingActionButton fab;

    /**
     * {@link ViewPager} to handle swiping (left/right) of {@link SettingsPreferenceFragment}
     * and {@link VideosListFragment} fragments
     */
    private ViewPager viewPager;

    /**
     * Object of {@link SharedPreferences} to read the app's settings.
     *
     * @see SettingsPreferenceFragment
     */
    private SharedPreferences prefs;

    /**
     * Static method to create the app's default directory in the external storage
     */
    public static void createDir() {
        File appDir = new File(Environment.getExternalStorageDirectory() + File.separator + Const.APPDIR);
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) && !appDir.isDirectory()) {
            appDir.mkdirs();
        }
    }

    /**
     * This method handles themes, populates the UI views and click listeners.
     *
     * @param savedInstanceState default savedInstance bundle sent by Android runtime
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        String theme = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(getString(R.string.preference_theme_key), Const.PREFS_LIGHT_THEME);
        int popupOverlayTheme = 0;
        int toolBarColor = 0;
        switch (theme) {
            case Const.PREFS_WHITE_THEME:
                setTheme(R.style.AppTheme_White_NoActionBar);
                break;
            case Const.PREFS_DARK_THEME:
                setTheme(R.style.AppTheme_Dark_NoActionBar);
                popupOverlayTheme = R.style.AppTheme_PopupOverlay_Dark;
                toolBarColor = ContextCompat.getColor(this, R.color.colorPrimary_dark);
                break;
            case Const.PREFS_BLACK_THEME:
                setTheme(R.style.AppTheme_Black_NoActionBar);
                popupOverlayTheme = R.style.AppTheme_PopupOverlay_Black;
                toolBarColor = ContextCompat.getColor(this, R.color.colorPrimary_black);
                break;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(toolBarColor);

        if (popupOverlayTheme != 0)
            toolbar.setPopupTheme(popupOverlayTheme);

        setSupportActionBar(toolbar);

        viewPager = findViewById(R.id.viewpager);
        setupViewPager(viewPager);

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.setBackgroundColor(toolBarColor);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        //Arbitrary "Write to external storage" permission since this permission is most important for the app
        requestPermissionStorage();

        fab = findViewById(R.id.fab);

        //Acquiring media projection service to start screen mirroring
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        //Respond to app shortcut
        if (getIntent().getAction() != null) {
            if (getIntent().getAction().equals(getString(R.string.app_shortcut_action))) {
                startActivityForResult(mProjectionManager.createScreenCaptureIntent(), Const.SCREEN_RECORD_REQUEST_CODE);
                return;
            } else if (getIntent().getAction().equals(Const.SCREEN_RECORDER_VIDEOS_LIST_FRAGMENT_INTENT)) {
                viewPager.setCurrentItem(1);
            }
        }

        if (isServiceRunning(RecorderService.class)) {
            Log.d(Const.TAG, "service is running");
        }
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMediaProjection == null && !isServiceRunning(RecorderService.class)) {
                    //Request Screen recording permission
                    startActivityForResult(mProjectionManager.createScreenCaptureIntent(), Const.SCREEN_RECORD_REQUEST_CODE);
                } else if (isServiceRunning(RecorderService.class)) {
                    //stop recording if the service is already active and recording
                    Toast.makeText(MainActivity.this, "Screen already recording", Toast.LENGTH_SHORT).show();
                }
            }
        });
        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Toast.makeText(MainActivity.this, R.string.fab_record_hint, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        if (!BuildConfig.DEBUG)
            requestAnalyticsPermission();

        Countly.onCreate(this);
    }

    /**
     * Method to initialize the countly analytics sdk.<br />
     * The sdk is initialized either to only report app crashes or to report usage analytics based
     * on the settings chosen by the user which is saved to the shared preference
     */
    public void setupAnalytics() {
        if (!prefs.getBoolean(getString(R.string.preference_crash_reporting_key), false) &&
                !prefs.getBoolean(getString(R.string.preference_anonymous_statistics_key), false)) {
            Log.d(Const.TAG, "Analytics disabled by user");
            return;
        }
        Countly.sharedInstance().init(this, Const.ANALYTICS_URL, Const.ANALYTICS_API_KEY,
                null, DeviceId.Type.OPEN_UDID, 3, null, null, null, null);
        Countly.sharedInstance().setHttpPostForced(true);
        Countly.sharedInstance().enableParameterTamperingProtection(getPackageName());

        if (prefs.getBoolean(getString(R.string.preference_crash_reporting_key), false)) {
            Countly.sharedInstance().enableCrashReporting();
            Log.d(Const.TAG, "Enabling crash reporting");
        }
        if (prefs.getBoolean(getString(R.string.preference_anonymous_statistics_key), false)) {
            Countly.sharedInstance().setStarRatingDisableAskingForEachAppVersion(false);
            Countly.sharedInstance().setViewTracking(true);
            Countly.sharedInstance().setIfStarRatingShownAutomatically(true);
            Log.d(Const.TAG, "Enabling countly statistics");
        }
        Countly.sharedInstance().onStart(this);
    }

    /**
     * Method to add the fragments: {@link SettingsPreferenceFragment} and {@link VideosListFragment}
     * to the viewpager and add {@link ViewPager#addOnPageChangeListener(ViewPager.OnPageChangeListener)}
     * to hide {@link #fab} on {@link VideosListFragment}
     *
     * @param viewPager viewpager instance from the layout
     */
    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getFragmentManager());
        adapter.addFragment(new SettingsPreferenceFragment(), getString(R.string.tab_settings_title));
        adapter.addFragment(new VideosListFragment(), getString(R.string.tab_videos_title));
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0:
                        fab.show();
                        break;
                    case 1:
                        fab.hide();
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    /**
     * Method to check if the {@link RecorderService} is running
     *
     * @param serviceClass Collection containing the {@link RecorderService} class
     * @return boolean value representing if the {@link RecorderService} is running
     * @throws NullPointerException May throw NullPointerException
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    /**
     * onActivityResult method to handle the activity results for floating controls
     * and screen recording permission
     *
     * @param requestCode Unique request code for different startActivityForResult calls
     * @param resultCode  result code representing the user's choice
     * @param data        Extra intent data passed from calling intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        //Result for system windows permission required to show floating controls
        if (requestCode == Const.FLOATING_CONTROLS_SYSTEM_WINDOWS_CODE || requestCode == Const.CAMERA_SYSTEM_WINDOWS_CODE) {
            setSystemWindowsPermissionResult(requestCode);
            return;
        }

        //The user has denied permission for screen mirroring. Let's notify the user
        if (resultCode == RESULT_CANCELED && requestCode == Const.SCREEN_RECORD_REQUEST_CODE) {
            Toast.makeText(this,
                    getString(R.string.screen_recording_permission_denied), Toast.LENGTH_SHORT).show();
            //Return to home screen if the app was started from app shortcut
            if (getIntent().getAction().equals(getString(R.string.app_shortcut_action)))
                this.finish();
            return;

        }

        /*If code reaches this point, congratulations! The user has granted screen mirroring permission
         * Let us set the recorderservice intent with relevant data and start service*/
        Intent recorderService = new Intent(this, RecorderService.class);
        recorderService.setAction(Const.SCREEN_RECORDING_START);
        recorderService.putExtra(Const.RECORDER_INTENT_DATA, data);
        recorderService.putExtra(Const.RECORDER_INTENT_RESULT, resultCode);
        startService(recorderService);
    }


    /**
     * Method to remove and recreate the {@link VideosListFragment} when the save location changes
     */
    public void onDirectoryChanged() {
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        ((VideosListFragment) adapter.getItem(1)).removeVideosList();
        Log.d(Const.TAG, "reached main act");
    }


    /**
     * Method to request permission for writing to external storage
     *
     * @return boolean
     */
    public boolean requestPermissionStorage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.storage_permission_request_title))
                    .setMessage(getString(R.string.storage_permission_request_summary))
                    .setNeutralButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    Const.EXTDIR_REQUEST_CODE);
                        }
                    })
                    .setCancelable(false);

            alert.create().show();
            return false;
        }
        return true;
    }


    /**
     * Method to request system windows permission. The permission is granted implicitly on API's below 23
     */
    @TargetApi(23)
    public void requestSystemWindowsPermission(int code) {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, code);
        }
    }

    /**
     * Sets system overlay permission if permission granted.
     * The permission is always set to granted if the api is under 23
     */
    @TargetApi(23)
    private void setSystemWindowsPermissionResult(int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                mPermissionResultListener.onPermissionResult(requestCode,
                        new String[]{"System Windows Permission"},
                        new int[]{PackageManager.PERMISSION_GRANTED});
            } else {
                mPermissionResultListener.onPermissionResult(requestCode,
                        new String[]{"System Windows Permission"},
                        new int[]{PackageManager.PERMISSION_DENIED});
            }
        } else {
            mPermissionResultListener.onPermissionResult(requestCode,
                    new String[]{"System Windows Permission"},
                    new int[]{PackageManager.PERMISSION_GRANTED});
        }
    }

    public void requestPermissionCamera() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    Const.CAMERA_REQUEST_CODE);
        }
    }

    /**
     * Method to request audio permission
     */
    public void requestPermissionAudio(int requestCode) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    requestCode);
        }
    }

    /**
     * Overrided onRequestPermissionsResult from {@link PermissionResultListener}
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     * @see PermissionResultListener
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case Const.EXTDIR_REQUEST_CODE:
                if ((grantResults.length > 0) &&
                        (grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
                    Log.d(Const.TAG, "write storage Permission Denied");
                    /* Disable floating action Button in case write storage permission is denied.
                     * There is no use in recording screen when the video is unable to be saved */
                    fab.setEnabled(false);
                } else {
                    /* Since we have write storage permission now, lets create the app directory
                     * in external storage*/
                    Log.d(Const.TAG, "write storage Permission granted");
                    createDir();
                }
        }

        // Let's also pass the result data to SettingsPreferenceFragment using the callback interface
        if (mPermissionResultListener != null) {
            mPermissionResultListener.onPermissionResult(requestCode, permissions, grantResults);
        }
    }


    /**
     * Method to create a dialog to request for analytics permission. Allows users to choose between
     * <br />1. Crash reporting only
     * <br />2. All analytics
     * <br />3. Disable everything
     */
    private void requestAnalyticsPermission() {
        if (!prefs.getBoolean(Const.PREFS_REQUEST_ANALYTICS_PERMISSION, true))
            return;

        new AlertDialog.Builder(this)
                .setTitle("Allow anonymous analytics")
                .setMessage("Do you want to allow anonymous crash reporting and usage metrics now?" +
                        "\nRead the privacy policy to know more on what data are collected")
                .setPositiveButton("Enable analytics", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        analyticsSettingsListerner.updateAnalyticsSettings(Const.analytics.CRASHREPORTING);
                        analyticsSettingsListerner.updateAnalyticsSettings(Const.analytics.USAGESTATS);
                        prefs.edit()
                                .putBoolean(Const.PREFS_REQUEST_ANALYTICS_PERMISSION, false)
                                .apply();
                    }
                })
                .setNeutralButton("Enable Crash reporting only", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        analyticsSettingsListerner.updateAnalyticsSettings(Const.analytics.CRASHREPORTING);
                        prefs.edit()
                                .putBoolean(Const.PREFS_REQUEST_ANALYTICS_PERMISSION, false)
                                .apply();
                    }
                })
                .setNegativeButton("Disable everything", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        prefs.edit()
                                .putBoolean(Const.PREFS_REQUEST_ANALYTICS_PERMISSION, false)
                                .apply();
                    }
                })
                .setCancelable(false)
                .create().show();
    }

    /**
     * Method to set {@link PermissionResultListener}
     *
     * @param mPermissionResultListener {@link PermissionResultListener} object
     */
    public void setPermissionResultListener(PermissionResultListener mPermissionResultListener) {
        this.mPermissionResultListener = mPermissionResultListener;
    }

    /**
     * Method to set {@link AnalyticsSettingsListerner}
     *
     * @param analyticsSettingsListerner {@link AnalyticsSettingsListerner} object
     */
    public void setAnalyticsSettingsListerner(AnalyticsSettingsListerner analyticsSettingsListerner) {
        this.analyticsSettingsListerner = analyticsSettingsListerner;
    }

    @Override
    protected void onStart() {
        super.onStart();
        ((ScreenCamApp) getApplication()).setupAnalytics();
        Countly.sharedInstance().onStart(this);
    }

    @Override
    protected void onStop() {
        Countly.sharedInstance().onStop();
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.overflow_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            case R.id.privacy_policy:
                startActivity(new Intent(this, PrivacyPolicy.class));
                return true;
            case R.id.donate:
                startActivity(new Intent(this, DonateActivity.class));
                return true;
            case R.id.help:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/joinchat/C_ZSIUKiqUCI5NsPMAv0eA")));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, "No browser app installed!", Toast.LENGTH_SHORT).show();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public interface AnalyticsSettingsListerner {
        void updateAnalyticsSettings(Const.analytics analytics);
    }

    /**
     * ViewPagerAdapter class to handle fragment tabs
     */
    class ViewPagerAdapter extends FragmentStatePagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        /**
         * Get the fragment depending on the position
         *
         * @param position integer representing the tab position
         * @return Fragment
         */
        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        /**
         * Get the position of the fragment
         *
         * @param object Fragment object
         * @return Integer position of the tab
         */
        @Override
        public int getItemPosition(Object object) {
            return super.getItemPosition(object);
        }

        /**
         * Get total fragment count
         *
         * @return integer count of the tabs
         */
        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        /**
         * Add a fragment to the tab bar
         *
         * @param fragment Tab fragment
         * @param title    title of the fragment tab
         */
        void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        /**
         * Gets the title of the fragment
         *
         * @param position integer Fragment position
         * @return Fragment title
         */
        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }
}
