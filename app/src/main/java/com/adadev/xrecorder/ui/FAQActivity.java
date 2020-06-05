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

package com.adadev.xrecorder.ui;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;

import com.adadev.xrecorder.Const;
import com.adadev.xrecorder.R;
import com.adadev.xrecorder.adapter.FAQAdapter;
import com.adadev.xrecorder.utils.FAQBuilder;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static com.topjohnwu.superuser.internal.InternalUtils.getContext;

public class FAQActivity extends AppCompatActivity {
    private RecyclerView FAQView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        String theme = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(getString(R.string.preference_theme_key), Const.PREFS_LIGHT_THEME);
        switch (theme) {
            case Const.PREFS_WHITE_THEME:
                setTheme(R.style.AppTheme_White);
                break;
            case Const.PREFS_DARK_THEME:
                setTheme(R.style.AppTheme_Dark);
                break;
            case Const.PREFS_BLACK_THEME:
                setTheme(R.style.AppTheme_Black);
                break;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faq);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        FAQView = findViewById(R.id.faq_rv);
        RecyclerView.LayoutManager recyclerViewLayoutManager = new LinearLayoutManager(getContext());
        FAQView.setLayoutManager(recyclerViewLayoutManager);
        FAQAdapter recyclerViewAdapter = new FAQAdapter(FAQBuilder.getInstance(this).buildFAQ());

        // set custom adapter to recycler view
        FAQView.setAdapter(recyclerViewAdapter);

        // Set recycler view item click listener
        //recyclerViewAdapter.setOnClick(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                //finish this activity and return to parent activity
                this.finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
