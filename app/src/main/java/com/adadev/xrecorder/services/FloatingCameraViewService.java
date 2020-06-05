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

package com.adadev.xrecorder.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.google.android.cameraview.CameraView;
import com.adadev.xrecorder.Const;
import com.adadev.xrecorder.R;

/**
 * Todo: Add class description here
 *
 * @author Vijai Chandra Prasad .R
 */
public class FloatingCameraViewService extends Service implements View.OnClickListener {
    private static FloatingCameraViewService context;
    private WindowManager mWindowManager;
    private LinearLayout mFloatingView;
    private View mCurrentView;
    private LinearLayout hidenCameraView;
    private ImageButton resizeOverlay;
    private CameraView cameraView;
    private boolean isCameraViewHidden;
    private Values values;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private OverlayResize overlayResize = OverlayResize.MINWINDOW;
    private IBinder binder = new ServiceBinder();

    public FloatingCameraViewService() {
        context = this;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(Const.TAG, "Binding successful!");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(Const.TAG, "Unbinding and stopping service");
        stopSelf();
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LayoutInflater li = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mFloatingView = (LinearLayout) li.inflate(R.layout.layout_floating_camera_view, null);
        hidenCameraView = (LinearLayout) li.inflate(R.layout.layout_floating_camera_view_hide, null);

        cameraView = mFloatingView.findViewById(R.id.cameraView);
        ImageButton hideCameraBtn = mFloatingView.findViewById(R.id.hide_camera);
        ImageButton switchCameraBtn = mFloatingView.findViewById(R.id.switch_camera);
        resizeOverlay = mFloatingView.findViewById(R.id.overlayResize);

        hidenCameraView = hidenCameraView.findViewById(R.id.rootOverlayExpandBtn);
        values = new Values();

        hideCameraBtn.setOnClickListener(this);
        switchCameraBtn.setOnClickListener(this);
        resizeOverlay.setOnClickListener(this);

        mCurrentView = mFloatingView;

        int xPos = getXPos();
        int yPos = getYPos();
        int layoutType;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        else
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        //Add the view to the window.
        params = new WindowManager.LayoutParams(
                values.smallCameraX,
                values.smallCameraY,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        //Specify the view position
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = xPos;
        params.y = yPos;

        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mCurrentView, params);

        cameraView.start();
        setupDragListener();

        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        changeCameraOrientation();
    }

    private void changeCameraOrientation() {
        values.buildValues();
        int x = overlayResize == OverlayResize.MAXWINDOW ? values.bigCameraX : values.smallCameraX;
        int y = overlayResize == OverlayResize.MAXWINDOW ? values.bigCameraY : values.smallCameraY;
        if (!isCameraViewHidden) {
            params.height = y;
            params.width = x;
            mWindowManager.updateViewLayout(mCurrentView, params);
        }
    }

    private void setupDragListener() {
        mCurrentView.setOnTouchListener(new View.OnTouchListener() {
            boolean isMoving = false;
            private WindowManager.LayoutParams paramsF = params;
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isMoving = false;
                        initialX = paramsF.x;
                        initialY = paramsF.y;
                        //get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isMoving && mCurrentView.equals(hidenCameraView)) {
                            showCameraView();
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        //Calculate the X and Y coordinates of the view.
                        int xDiff = (int) (event.getRawX() - initialTouchX);
                        int yDiff = (int) (event.getRawY() - initialTouchY);
                        paramsF.x = initialX + xDiff;
                        paramsF.y = initialY + yDiff;
                        /* Set an offset of 10 pixels to determine controls moving. Else, normal touches
                         * could react as moving the control window around */
                        if (Math.abs(xDiff) > 10 || Math.abs(yDiff) > 10)
                            isMoving = true;
                        mWindowManager.updateViewLayout(mCurrentView, paramsF);
                        persistCoordinates(initialX + xDiff, initialY + yDiff);
                        return true;
                }
                return false;
            }
        });
    }

    private int getXPos() {
        String pos = getDefaultPrefs().getString(Const.PREFS_CAMERA_OVERLAY_POS, "0X100");
        return Integer.parseInt(pos.split("X")[0]);
    }

    private int getYPos() {
        String pos = getDefaultPrefs().getString(Const.PREFS_CAMERA_OVERLAY_POS, "0X100");
        return Integer.parseInt(pos.split("X")[1]);
    }

    private void persistCoordinates(int x, int y) {
        getDefaultPrefs().edit()
                .putString(Const.PREFS_CAMERA_OVERLAY_POS, String.valueOf(x) + "X" + String.valueOf(y))
                .apply();
    }

    private SharedPreferences getDefaultPrefs() {
        if (prefs == null)
            prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingView != null) mWindowManager.removeView(mCurrentView);
        cameraView.stop();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.switch_camera:
                if (cameraView.getFacing() == CameraView.FACING_BACK) {
                    cameraView.setFacing(CameraView.FACING_FRONT);
                    cameraView.setAutoFocus(true);
                } else {
                    cameraView.setFacing(CameraView.FACING_BACK);
                    cameraView.setAutoFocus(true);
                }
                break;
            case R.id.hide_camera:
                Log.d(Const.TAG, "hide camera");
                if (mCurrentView.equals(mFloatingView)) {
                    mWindowManager.removeViewImmediate(mCurrentView);
                    mCurrentView = hidenCameraView;
                    params.width = values.cameraHideX;
                    params.height = values.cameraHideY;
                    mWindowManager.addView(mCurrentView, params);
                }
                setupDragListener();
                break;
            case R.id.overlayResize:
                updateCameraView();
                break;
        }
    }

    private void showCameraView() {
        if (mCurrentView.equals(hidenCameraView)) {
            mWindowManager.removeViewImmediate(mCurrentView);
            mCurrentView = mFloatingView;
            if (overlayResize == OverlayResize.MINWINDOW)
                overlayResize = OverlayResize.MAXWINDOW;
            else
                overlayResize = OverlayResize.MINWINDOW;
            mWindowManager.addView(mCurrentView, params);
            isCameraViewHidden = false;
            updateCameraView();
            setupDragListener();
        }
    }

    private void updateCameraView() {
        if (overlayResize == OverlayResize.MINWINDOW) {
            params.width = values.bigCameraX;
            params.height = values.bigCameraY;
            overlayResize = OverlayResize.MAXWINDOW;
            resizeOverlay.setImageResource(R.drawable.ic_bigscreen_exit);
        } else {
            params.width = values.smallCameraX;
            params.height = values.smallCameraY;
            overlayResize = OverlayResize.MINWINDOW;
            resizeOverlay.setImageResource(R.drawable.ic_bigscreen);
        }
        mWindowManager.updateViewLayout(mCurrentView, params);
    }

    private enum OverlayResize {
        MAXWINDOW, MINWINDOW
    }

    private class Values {
        int smallCameraX;
        int smallCameraY;
        int bigCameraX;
        int bigCameraY;
        int cameraHideX;
        int cameraHideY;

        public Values() {
            buildValues();
            cameraHideX = dpToPx(60);
            cameraHideY = dpToPx(60);
        }

        private int dpToPx(int dp) {
            DisplayMetrics displayMetrics = FloatingCameraViewService.this.getResources().getDisplayMetrics();
            return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        }

        void buildValues() {
            int orientation = context.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                smallCameraX = dpToPx(160);
                smallCameraY = dpToPx(120);
                bigCameraX = dpToPx(200);
                bigCameraY = dpToPx(150);
            } else {
                smallCameraX = dpToPx(120);
                smallCameraY = dpToPx(160);
                bigCameraX = dpToPx(150);
                bigCameraY = dpToPx(200);
            }
        }
    }

    public class ServiceBinder extends Binder {
        FloatingCameraViewService getService() {
            return FloatingCameraViewService.this;
        }
    }
}