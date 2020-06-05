package com.adadev.xrecorder.utils;

import android.content.Context;

import com.adadev.xrecorder.adapter.FAQModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

public class FAQBuilder {
    private static FAQBuilder faqBuilder;
    private Context context;

    private FAQBuilder() {
    }

    public static FAQBuilder getInstance(Context context) {
        if (faqBuilder == null) {
            faqBuilder = new FAQBuilder();
            faqBuilder.context = context;
        }
        return faqBuilder;
    }

    public String loadJSONFromAsset() {
        String json = null;
        try {
            InputStream is = context.getAssets().open("faq.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    public ArrayList<FAQModel> buildFAQ() {
        ArrayList<FAQModel> FAQs = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(loadJSONFromAsset());

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject faq = jsonArray.getJSONObject(i);
                FAQs.add(new FAQModel(faq.getString("question"), faq.getString("answer"), faq.getInt("weight")));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Collections.sort(FAQs, Collections.reverseOrder());
        return FAQs;
    }
}
