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

package com.orpheusdroid.screenrecorder.services;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;
import com.orpheusdroid.screenrecorder.gesture.ShakeEventManager;
import com.orpheusdroid.screenrecorder.ui.EditVideoActivity;
import com.orpheusdroid.screenrecorder.ui.MainActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

/**
 * Service to manage recording and notifications
 * <p>
 * A service class to manage recording (start/pause/stop), handle notifications, manage floating controls
 * and recording permissions
 * </p>
 *
 * @author Vijai Chandra Prasad .R
 */
//TODO: Update icons for notifcation
public class RecorderService extends Service implements ShakeEventManager.ShakeListener {
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static int WIDTH, HEIGHT, FPS, DENSITY_DPI;
    private static int BITRATE;
    private static String audioRecSource;
    private static String SAVEPATH;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private int screenOrientation;

    private boolean isRecording;
    private boolean useFloatingControls;
    private boolean showCameraOverlay;
    private boolean showTouches;
    private boolean isShakeGestureActive;
    private FloatingControlService floatingControlService;
    private boolean isBound = false;
    private NotificationManager mNotificationManager;
    Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
            Toast.makeText(RecorderService.this, R.string.screen_recording_stopped_toast, Toast.LENGTH_SHORT).show();
            showShareNotification();
        }
    };
    private ShakeEventManager mShakeDetector;
    private Intent data;
    private int result;
    //Service connection to manage the connection state between this service and the bounded service
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //Get the service instance
            FloatingControlService.ServiceBinder binder = (FloatingControlService.ServiceBinder) service;
            floatingControlService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            floatingControlService = null;
            isBound = false;
        }
    };

    private ServiceConnection floatingCameraConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //Get the service instance
            FloatingCameraViewService.ServiceBinder binder = (FloatingCameraViewService.ServiceBinder) service;
            FloatingCameraViewService floatingCameraViewService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            floatingControlService = null;
        }
    };
    private long startTime, elapsedTime = 0;
    private SharedPreferences prefs;
    private WindowManager window;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private MediaRecorder mMediaRecorder;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannels();


        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        //return super.onStartCommand(intent, flags, startId);
        //Find the action to perform from intent
        switch (intent.getAction()) {
            case Const.SCREEN_RECORDING_START:

                /* Wish MediaRecorder had a method isRecording() or similar. But, we are forced to
                 * manage the state ourself. Let's hope the request is honored.
                 * Request: https://code.google.com/p/android/issues/detail?id=800 */
                if (!isRecording) {
                    //Get values from Default SharedPreferences
                    //screenOrientation = intent.getIntExtra(Const.SCREEN_ORIENTATION, 0);
                    screenOrientation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                    data = intent.getParcelableExtra(Const.RECORDER_INTENT_DATA);
                    result = intent.getIntExtra(Const.RECORDER_INTENT_RESULT, Activity.RESULT_OK);

                    getValues();
                    // Check if an app has to be started before recording and start the app
                    if (prefs.getBoolean(getString(R.string.preference_enable_target_app_key), false))
                        startAppBeforeRecording(prefs.getString(getString(R.string.preference_app_chooser_key), "none"));

                    if (isShakeGestureActive) {
                        //SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
                        mShakeDetector = new ShakeEventManager(this);
                        mShakeDetector.init(this);

                        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                                R.mipmap.ic_launcher);

                        Intent destroyMediaRecorderIntent = new Intent(this, RecorderService.class);
                        destroyMediaRecorderIntent.setAction(Const.SCREEN_RECORDING_DESTORY_SHAKE_GESTURE);
                        PendingIntent pdestroyMediaRecorderIntent = PendingIntent.getService(this, 0, destroyMediaRecorderIntent, 0);

                        NotificationCompat.Builder shakeGestureWaitNotification =
                                new NotificationCompat.Builder(this, Const.RECORDING_NOTIFICATION_CHANNEL_ID)
                                        .setContentTitle("Waiting for device shake")
                                        .setContentText("Shake your device to start recording or press this notification to cancel")
                                        .setOngoing(true)
                                        .setSmallIcon(R.drawable.ic_notification)
                                        .setLargeIcon(
                                                Bitmap.createScaledBitmap(icon, 128, 128, false))
                                        .setContentIntent(pdestroyMediaRecorderIntent);

                        startNotificationForeGround(shakeGestureWaitNotification.build(), Const.SCREEN_RECORDER_SHARE_NOTIFICATION_ID);

                        Toast.makeText(this, R.string.screenrecording_waiting_for_gesture_toast,
                                Toast.LENGTH_LONG).show();
                    } else {
                        startRecording();
                    }

                } else {
                    Toast.makeText(this, R.string.screenrecording_already_active_toast, Toast.LENGTH_SHORT).show();
                }
                break;
            case Const.SCREEN_RECORDING_PAUSE:
                pauseScreenRecording();
                break;
            case Const.SCREEN_RECORDING_RESUME:
                resumeScreenRecording();
                break;
            case Const.SCREEN_RECORDING_STOP:
                stopRecording();
                break;
            case Const.SCREEN_RECORDING_DESTORY_SHAKE_GESTURE:
                mShakeDetector.stop();
                stopSelf();
                break;
        }
        return START_STICKY;
    }

    private void stopRecording() {
        //Unbind the floating control service if its bound (naturally unbound if floating controls is disabled)
        if (isBound) {
            unbindService(serviceConnection);
            Log.d(Const.TAG, "Unbinding connection service");
        }
        stopScreenSharing();

        //Send a broadcast receiver to the plugin app to disable show touches since the recording is stopped
        if (showTouches) {
            Intent TouchIntent = new Intent();
            TouchIntent.setAction("com.orpheusdroid.screenrecorder.DISABLETOUCH");
            TouchIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendBroadcast(TouchIntent);
        }

        //The service is started as foreground service and hence has to be stopped
        stopForeground(true);
    }

    // Start the selected app before recording if its enabled and an app is selected
    private void startAppBeforeRecording(String packagename) {
        if (packagename.equals("none"))
            return;

        Intent startAppIntent = getPackageManager().getLaunchIntentForPackage(packagename);
        startActivity(startAppIntent);
    }

    @TargetApi(24)
    private void pauseScreenRecording() {
        mMediaRecorder.pause();
        //calculate total elapsed time until pause
        elapsedTime += (System.currentTimeMillis() - startTime);

        //Set Resume action to Notification and update the current notification
        Intent recordResumeIntent = new Intent(this, RecorderService.class);
        recordResumeIntent.setAction(Const.SCREEN_RECORDING_RESUME);
        PendingIntent precordResumeIntent = PendingIntent.getService(this, 0, recordResumeIntent, 0);
        NotificationCompat.Action action = new NotificationCompat.Action(android.R.drawable.ic_media_play,
                getString(R.string.screen_recording_notification_action_resume), precordResumeIntent);
        updateNotification(createRecordingNotification(action).setUsesChronometer(false).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);
        Toast.makeText(this, R.string.screen_recording_paused_toast, Toast.LENGTH_SHORT).show();

        if (isBound)
            floatingControlService.setRecordingState(Const.RecordingState.PAUSED);

        //Send a broadcast receiver to the plugin app to disable show touches since the recording is paused
        if (showTouches) {
            Intent TouchIntent = new Intent();
            TouchIntent.setAction("com.orpheusdroid.screenrecorder.DISABLETOUCH");
            TouchIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendBroadcast(TouchIntent);
        }
    }

    @TargetApi(24)
    private void resumeScreenRecording() {
        mMediaRecorder.resume();

        //Reset startTime to current time again
        startTime = System.currentTimeMillis();

        //set Pause action to Notification and update current Notification
        Intent recordPauseIntent = new Intent(this, RecorderService.class);
        recordPauseIntent.setAction(Const.SCREEN_RECORDING_PAUSE);
        PendingIntent precordPauseIntent = PendingIntent.getService(this, 0, recordPauseIntent, 0);
        NotificationCompat.Action action = new NotificationCompat.Action(android.R.drawable.ic_media_pause,
                getString(R.string.screen_recording_notification_action_pause), precordPauseIntent);
        updateNotification(createRecordingNotification(action).setUsesChronometer(true)
                .setWhen((System.currentTimeMillis() - elapsedTime)).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);
        Toast.makeText(this, R.string.screen_recording_resumed_toast, Toast.LENGTH_SHORT).show();

        if (isBound)
            floatingControlService.setRecordingState(Const.RecordingState.RECORDING);


        //Send a broadcast receiver to the plugin app to enable show touches since the recording is resumed
        if (showTouches) {
            if (showTouches) {
                Intent TouchIntent = new Intent();
                TouchIntent.setAction("com.orpheusdroid.screenrecorder.SHOWTOUCH");
                TouchIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                sendBroadcast(TouchIntent);
            }
        }
    }

    private void startRecording() {
        //Initialize MediaRecorder class and initialize it with preferred configuration
        mMediaRecorder = new MediaRecorder();
        initRecorder();

        //Set Callback for MediaProjection
        mMediaProjectionCallback = new MediaProjectionCallback();
        MediaProjectionManager mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        //Initialize MediaProjection using data received from Intent
        mMediaProjection = mProjectionManager.getMediaProjection(result, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);

        /* Create a new virtual display with the actual default display
         * and pass it on to MediaRecorder to start recording */
        mVirtualDisplay = createVirtualDisplay();
        try {
            mMediaRecorder.start();

            //If floating controls is enabled, start the floating control service and bind it here
            if (useFloatingControls) {
                Intent floatinControlsIntent = new Intent(this, FloatingControlService.class);
                startService(floatinControlsIntent);
                bindService(floatinControlsIntent,
                        serviceConnection, BIND_AUTO_CREATE);
            }

            if (showCameraOverlay) {
                Intent floatingCameraIntent = new Intent(this, FloatingCameraViewService.class);
                startService(floatingCameraIntent);
                bindService(floatingCameraIntent,
                        floatingCameraConnection, BIND_AUTO_CREATE);
            }

            //Set the state of the recording
            if (isBound)
                floatingControlService.setRecordingState(Const.RecordingState.RECORDING);
            isRecording = true;

            //Send a broadcast receiver to the plugin app to enable show touches since the recording is started
            if (showTouches) {
                Intent TouchIntent = new Intent();
                TouchIntent.setAction("com.orpheusdroid.screenrecorder.SHOWTOUCH");
                TouchIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                sendBroadcast(TouchIntent);
            }
            Toast.makeText(this, R.string.screen_recording_started_toast, Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException e) {
            Log.e(Const.TAG, "Mediarecorder reached Illegal state exception. Did you start the recording twice?");
            Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
            isRecording = false;
            mMediaProjection.stop();
            stopSelf();
        }

        /* Add Pause action to Notification to pause screen recording if the user's android version
         * is >= Nougat(API 24) since pause() isnt available previous to API24 else build
         * Notification with only default stop() action */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //startTime is to calculate elapsed recording time to update notification during pause/resume
            startTime = System.currentTimeMillis();
            Intent recordPauseIntent = new Intent(this, RecorderService.class);
            recordPauseIntent.setAction(Const.SCREEN_RECORDING_PAUSE);
            PendingIntent precordPauseIntent = PendingIntent.getService(this, 0, recordPauseIntent, 0);
            NotificationCompat.Action action = new NotificationCompat.Action(android.R.drawable.ic_media_pause,
                    getString(R.string.screen_recording_notification_action_pause), precordPauseIntent);

            //Start Notification as foreground
            startNotificationForeGround(createRecordingNotification(action).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);
        } else
            startNotificationForeGround(createRecordingNotification(null).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);
    }

    //Virtual display created by mirroring the actual physical display
    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("MainActivity",
                WIDTH, HEIGHT, DENSITY_DPI,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null
                /*Handler*/);
    }

    /* Initialize MediaRecorder with desired default values and values set by user. Everything is
     * pretty much self explanatory */
    private void initRecorder() {
        boolean mustRecAudio = false;
        try {
            switch (audioRecSource) {
                case "1":
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mustRecAudio = true;
                    break;
                case "2":
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
                    mMediaRecorder.setAudioEncodingBitRate(320 * 96000);
                    mMediaRecorder.setAudioSamplingRate(96000);
                    mustRecAudio = true;
                    break;
                case "3":
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.REMOTE_SUBMIX);
                    mMediaRecorder.setAudioEncodingBitRate(384000);
                    mMediaRecorder.setAudioSamplingRate(44100);
                    mustRecAudio = true;
                    break;
            }
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setOutputFile(SAVEPATH);
            mMediaRecorder.setVideoSize(WIDTH, HEIGHT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            if (mustRecAudio)
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setVideoEncodingBitRate(BITRATE);
            mMediaRecorder.setVideoFrameRate(FPS);
            int orientation = (360 - ORIENTATIONS.get(screenOrientation)) % 360;
            //mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Add notification channel for supporting Notification in Api 26 (Oreo)
    @TargetApi(26)
    private void createNotificationChannels() {
        List<NotificationChannel> notificationChannels = new ArrayList<>();
        NotificationChannel recordingNotificationChannel = new NotificationChannel(
                Const.RECORDING_NOTIFICATION_CHANNEL_ID,
                Const.RECORDING_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        recordingNotificationChannel.enableLights(true);
        recordingNotificationChannel.setLightColor(Color.RED);
        recordingNotificationChannel.setShowBadge(true);
        recordingNotificationChannel.enableVibration(true);
        recordingNotificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationChannels.add(recordingNotificationChannel);

        NotificationChannel shareNotificationChannel = new NotificationChannel(
                Const.SHARE_NOTIFICATION_CHANNEL_ID,
                Const.SHARE_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        shareNotificationChannel.enableLights(true);
        shareNotificationChannel.setLightColor(Color.RED);
        shareNotificationChannel.setShowBadge(true);
        shareNotificationChannel.enableVibration(true);
        shareNotificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationChannels.add(shareNotificationChannel);

        getManager().createNotificationChannels(notificationChannels);
    }

    /* Create Notification.Builder with action passed in case user's android version is greater than
     * API24 */
    private NotificationCompat.Builder createRecordingNotification(NotificationCompat.Action action) {
        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_launcher);

        Intent recordStopIntent = new Intent(this, RecorderService.class);
        recordStopIntent.setAction(Const.SCREEN_RECORDING_STOP);
        PendingIntent precordStopIntent = PendingIntent.getService(this, 0, recordStopIntent, 0);

        Intent UIIntent = new Intent(this, MainActivity.class);
        PendingIntent notificationContentIntent = PendingIntent.getActivity(this, 0, UIIntent, 0);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, Const.RECORDING_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.screen_recording_notification_title))
                .setTicker(getResources().getString(R.string.screen_recording_notification_title))
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(
                        Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setUsesChronometer(true)
                .setOngoing(true)
                .setContentIntent(notificationContentIntent)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .addAction(R.drawable.ic_notification_stop, getResources().getString(R.string.screen_recording_notification_action_stop),
                        precordStopIntent);
        if (action != null)
            notification.addAction(action);
        return notification;
    }

    private void showShareNotification() {
        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_launcher);

        Uri videoUri = FileProvider.getUriForFile(
                this, this.getApplicationContext().getPackageName() + ".provider",
                new File(SAVEPATH));

        Intent Shareintent = new Intent()
                .setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, videoUri)
                .setType("video/mp4");

        Intent editIntent = new Intent(this, EditVideoActivity.class);
        editIntent.putExtra(Const.VIDEO_EDIT_URI_KEY, SAVEPATH);
        PendingIntent editPendingIntent = PendingIntent.getActivity(this, 0, editIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent sharePendingIntent = PendingIntent.getActivity(this, 0, Intent.createChooser(
                Shareintent, getString(R.string.share_intent_title)), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setAction(Const.SCREEN_RECORDER_VIDEOS_LIST_FRAGMENT_INTENT), PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder shareNotification = new NotificationCompat.Builder(this, Const.SHARE_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.share_intent_notification_title))
                .setContentText(getString(R.string.share_intent_notification_content))
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_menu_share, getString(R.string.share_intent_notification_action_text)
                        , sharePendingIntent)
                .addAction(android.R.drawable.ic_menu_edit, getString(R.string.edit_intent_notification_action_text)
                        , editPendingIntent);
        updateNotification(shareNotification.build(), Const.SCREEN_RECORDER_SHARE_NOTIFICATION_ID);
    }

    //Start service as a foreground service. We dont want the service to be killed in case of low memory
    private void startNotificationForeGround(Notification notification, int ID) {
        startForeground(ID, notification);
    }

    //Update existing notification with its ID and new Notification data
    private void updateNotification(Notification notification, int ID) {
        getManager().notify(ID, notification);
    }

    private NotificationManager getManager() {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return mNotificationManager;
    }

    @Override
    public void onDestroy() {
        Log.d(Const.TAG, "Recorder service destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //Get user's choices for user choosable settings
    public void getValues() {
        String res = getResolution();
        setWidthHeight(res);
        FPS = Integer.parseInt(prefs.getString(getString(R.string.fps_key), "30"));
        BITRATE = Integer.parseInt(prefs.getString(getString(R.string.bitrate_key), "7130317"));
        audioRecSource = prefs.getString(getString(R.string.audiorec_key), "0");
        String saveLocation = prefs.getString(getString(R.string.savelocation_key),
                Environment.getExternalStorageDirectory() + File.separator + Const.APPDIR);
        File saveDir = new File(saveLocation);
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) && !saveDir.isDirectory()) {
            saveDir.mkdirs();
        }
        useFloatingControls = prefs.getBoolean(getString(R.string.preference_floating_control_key), false);
        showCameraOverlay = prefs.getBoolean(getString(R.string.preference_camera_overlay_key), false);
        showTouches = prefs.getBoolean(getString(R.string.preference_show_touch_key), false);
        String saveFileName = getFileSaveName();
        SAVEPATH = saveLocation + File.separator + saveFileName + ".mp4";
        isShakeGestureActive = prefs.getBoolean(getString(R.string.preference_shake_gesture_key), false);
    }

    /* The PreferenceScreen save values as string and we save the user selected video resolution as
     * WIDTH x HEIGHT. Lets split the string on 'x' and retrieve width and height */
    private void setWidthHeight(String res) {
        String[] widthHeight = res.split("x");
        String orientationPrefs = prefs.getString(getString(R.string.orientation_key), "auto");
        switch (orientationPrefs) {
            case "auto":
                if (screenOrientation == 0 || screenOrientation == 2) {
                    WIDTH = Integer.parseInt(widthHeight[0]);
                    HEIGHT = Integer.parseInt(widthHeight[1]);
                } else {
                    HEIGHT = Integer.parseInt(widthHeight[0]);
                    WIDTH = Integer.parseInt(widthHeight[1]);
                }
                break;
            case "portrait":
                WIDTH = Integer.parseInt(widthHeight[0]);
                HEIGHT = Integer.parseInt(widthHeight[1]);
                break;
            case "landscape":
                HEIGHT = Integer.parseInt(widthHeight[0]);
                WIDTH = Integer.parseInt(widthHeight[1]);
                break;
        }
        Log.d(Const.TAG, "Width: " + WIDTH + ",Height:" + HEIGHT);
    }

    //Get the device resolution in pixels
    private String getResolution() {
        DisplayMetrics metrics = new DisplayMetrics();
        window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        window.getDefaultDisplay().getRealMetrics(metrics);
        DENSITY_DPI = metrics.densityDpi;
        int width = metrics.widthPixels;
        width = Integer.parseInt(prefs.getString(getString(R.string.res_key), Integer.toString(width)));
        float aspectRatio = getAspectRatio(metrics);
        String res = width + "x" + (int) (width * getAspectRatio(metrics));
        Log.d(Const.TAG, "resolution service: " + "[Width: "
                + width + ", Height: " + width * aspectRatio + ", aspect ratio: " + aspectRatio + "]");
        return res;
    }

    private float getAspectRatio(DisplayMetrics metrics) {
        float screen_width = metrics.widthPixels;
        float screen_height = metrics.heightPixels;
        float aspectRatio;
        if (screen_width > screen_height) {
            aspectRatio = screen_width / screen_height;
        } else {
            aspectRatio = screen_height / screen_width;
        }
        return aspectRatio;
    }

    //Return filename of the video to be saved formatted as chosen by the user
    private String getFileSaveName() {
        String filename = prefs.getString(getString(R.string.filename_key), "yyyyMMdd_hhmmss");
        String prefix = prefs.getString(getString(R.string.fileprefix_key), "recording");
        Date today = Calendar.getInstance().getTime();
        SimpleDateFormat formatter = new SimpleDateFormat(filename);
        return prefix + "_" + formatter.format(today);
    }

    //Stop and destroy all the objects used for screen recording
    private void destroyMediaProjection() {
        try {
            mMediaRecorder.stop();
            indexFile();
            Log.i(Const.TAG, "MediaProjection Stopped");
        } catch (RuntimeException e) {
            Log.e(Const.TAG, "Fatal exception! Destroying media projection failed." + "\n" + e.getMessage());
            if (new File(SAVEPATH).delete())
                Log.d(Const.TAG, "Corrupted file delete successful");
            Toast.makeText(this, getString(R.string.fatal_exception_message), Toast.LENGTH_SHORT).show();
        } finally {
            mMediaRecorder.reset();
            mVirtualDisplay.release();
            mMediaRecorder.release();
            if (mMediaProjection != null) {
                mMediaProjection.unregisterCallback(mMediaProjectionCallback);
                mMediaProjection.stop();
                mMediaProjection = null;
            }
        }
        isRecording = false;
    }

    /* Its weird that android does not index the files immediately once its created and that causes
     * trouble for user in finding the video in gallery. Let's explicitly announce the file creation
     * to android and index it */
    private void indexFile() {
        //Create a new ArrayList and add the newly created video file path to it
        ArrayList<String> toBeScanned = new ArrayList<>();
        toBeScanned.add(SAVEPATH);
        String[] toBeScannedStr = new String[toBeScanned.size()];
        toBeScannedStr = toBeScanned.toArray(toBeScannedStr);

        //Request MediaScannerConnection to scan the new file and index it
        MediaScannerConnection.scanFile(this, toBeScannedStr, null, new MediaScannerConnection.OnScanCompletedListener() {

            @Override
            public void onScanCompleted(String path, Uri uri) {
                Log.i(Const.TAG, "SCAN COMPLETED: " + path);
                //Show toast on main thread
                Message message = mHandler.obtainMessage();
                message.sendToTarget();
                stopSelf();
            }
        });
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            Log.d(Const.TAG, "Virtual display is null. Screen sharing already stopped");
            return;
        }
        destroyMediaProjection();
    }

    @Override
    public void onShake() {
        if (!isRecording) {
            Vibrator vibrate = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

            getManager().cancel(Const.SCREEN_RECORDER_WAITING_FOR_SHAKE_NOTIFICATION_ID);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                vibrate.vibrate(500);
            else
                VibrationEffect.createOneShot(500, 255);

            startRecording();
        } else {
            Intent recordStopIntent = new Intent(this, RecorderService.class);
            recordStopIntent.setAction(Const.SCREEN_RECORDING_STOP);
            startService(recordStopIntent);
            mShakeDetector.stop();
        }
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.v(Const.TAG, "Recording Stopped");
            stopScreenSharing();
        }
    }
}