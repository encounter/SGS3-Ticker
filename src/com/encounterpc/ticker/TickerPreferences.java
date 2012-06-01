package com.encounterpc.ticker;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class TickerPreferences extends PreferenceActivity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}