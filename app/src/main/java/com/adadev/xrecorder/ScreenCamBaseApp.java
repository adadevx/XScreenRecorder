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

package com.adadev.xrecorder;

import android.app.Application;
import android.content.pm.ApplicationInfo;

/**
 * Todo: Add class description here
 *
 * @author Vijai Chandra Prasad .R
 */
public class ScreenCamBaseApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        checkMagiskMode();
    }

    public boolean checkMagiskMode(){
        int mask = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        return (getApplicationInfo().flags & mask) != 0;
    }

    public void setupAnalytics() {
    }
}
