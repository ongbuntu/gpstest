/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gpstest;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.android.gpstest.view.ViewPagerMapBevelScroll;
import com.github.espiandev.showcaseview.ShowcaseView;

public class GpsTestActivity extends SherlockFragmentActivity
        implements LocationListener, GpsStatus.Listener, ActionBar.TabListener {
    private static final String TAG = "GpsTestActivity";
    
    private LocationManager mService;
    private LocationProvider mProvider;
    private GpsStatus mStatus;
    private ArrayList<GpsTestListener> mGpsTestListeners = new ArrayList<GpsTestListener>();
    boolean mStarted;
    private Location mLastLocation;
    String mTtff;
    
    private long minTime; // Min Time between location updates, in milliseconds
    private static final int SECONDS_TO_MILLISECONDS = 1000;
    private float minDistance; // Min Distance between location updates

    private static GpsTestActivity sInstance;
    
    /**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	SectionsPagerAdapter mSectionsPagerAdapter;
	    
	ViewPagerMapBevelScroll mViewPager;
	
	ShowcaseView sv;
    ShowcaseView.ConfigOptions mOptions = new ShowcaseView.ConfigOptions();
	
    interface GpsTestListener extends LocationListener {
        public void gpsStart();
        public void gpsStop();
        public void onGpsStatusChanged(int event, GpsStatus status);
    }

    static GpsTestActivity getInstance() {
        return sInstance;
    }

    void addSubActivity(GpsTestListener activity) {
        mGpsTestListeners.add(activity);
    }

    private void gpsStart() {
        if (!mStarted) {
            mService.requestLocationUpdates(mProvider.getName(), minTime, minDistance, this);
            mStarted = true;
            
            // Show Toast only if the user has set minTime or minDistance to something other than default values
            if (minTime != (long) (Double.valueOf(getString(R.string.pref_gps_min_time_default_sec)) * SECONDS_TO_MILLISECONDS) ||
            		minDistance != Float.valueOf(getString(R.string.pref_gps_min_distance_default_meters))) {
            	Toast.makeText(this, String.format(getString(R.string.gps_set_location_listener),
            		String.valueOf((double) minTime / SECONDS_TO_MILLISECONDS),String.valueOf(minDistance)), Toast.LENGTH_SHORT).show();
            }
            
            // Show the indeterminate progress bar on the action bar until first GPS status is shown
         	setSupportProgressBarIndeterminateVisibility(Boolean.TRUE);
         	
         	// Reset the options menu to trigger updates to action bar menu items
          	invalidateOptionsMenu();
        }
        for (GpsTestListener activity : mGpsTestListeners) {
            activity.gpsStart();
        }
    }

    private void gpsStop() {
        if (mStarted) {
            mService.removeUpdates(this);
            mStarted = false;
            // Stop progress bar
         	setSupportProgressBarIndeterminateVisibility(Boolean.FALSE);
         	
         	// Reset the options menu to trigger updates to action bar menu items
          	invalidateOptionsMenu();
        }
        for (GpsTestListener activity : mGpsTestListeners) {
            activity.gpsStop();
        }
    }

    private boolean sendExtraCommand(String command) {
        return mService.sendExtraCommand(LocationManager.GPS_PROVIDER, command, null);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	setTheme(com.actionbarsherlock.R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);
        sInstance = this;
        
        // Set the default values from the XML file if this is the first
     	// execution of the app
     	PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        mService = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mProvider = mService.getProvider(LocationManager.GPS_PROVIDER);
        if (mProvider == null) {
            // FIXME - fail gracefully here
            Log.e(TAG, "Unable to get GPS_PROVIDER");
        }
        mService.addGpsStatusListener(this);
        
        // Request use of spinner for showing indeterminate progress, to show
     	// the user something is going on during long-running operations
     	requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
     	
     	setContentView(R.layout.activity_main);
     	     	
     	initActionBar(savedInstanceState);
     	
     	SharedPreferences settings = Application.getPrefs();
     	
     	double tempMinTime = Double.valueOf(settings.getString(getString(R.string.pref_key_gps_min_time), getString(R.string.pref_gps_min_time_default_sec)));
     	minTime = (long) (tempMinTime * SECONDS_TO_MILLISECONDS);
     	minDistance = Float.valueOf(settings.getString(getString(R.string.pref_key_gps_min_distance), getString(R.string.pref_gps_min_distance_default_meters)));
     	    	
    	if (settings.getBoolean(getString(R.string.pref_key_auto_start_gps), true)) {    		
    		gpsStart();
    	}
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	SharedPreferences settings = Application.getPrefs();
    	    	    	
    	if (mViewPager != null) {
    		if (settings.getBoolean(getString(R.string.pref_key_keep_screen_on), true)) {
    			mViewPager.setKeepScreenOn(true);
    		} else {
    			mViewPager.setKeepScreenOn(false);
    		}   		    		
    	}
    	
    	checkTimeAndDistance(settings);
    	
    	checkTutorial(settings);
    }
    
    private void checkTimeAndDistance(SharedPreferences settings){
    	double tempMinTimeDouble = Double.valueOf(settings.getString(getString(R.string.pref_key_gps_min_time), "1"));     	   	
    	long minTimeLong = (long) (tempMinTimeDouble * SECONDS_TO_MILLISECONDS);
     	
    	if (minTime != minTimeLong || 
    			minDistance != Float.valueOf(settings.getString(getString(R.string.pref_key_gps_min_distance), "0"))) {
    		// User changed preference values, get the new ones    		
    		minTime = minTimeLong;
    		minDistance = Float.valueOf(settings.getString(getString(R.string.pref_key_gps_min_distance), "0"));
    		// If the GPS is started, reset the location listener with the new values
    		if(mStarted){
	    		mService.requestLocationUpdates(mProvider.getName(), minTime, minDistance, this);
				Toast.makeText(this, String.format(getString(R.string.gps_set_location_listener),
						String.valueOf(tempMinTimeDouble),String.valueOf(minDistance)), Toast.LENGTH_SHORT).show();
    		}
     	}    	
    }
    
    private void checkTutorial(SharedPreferences settings){
    	if (!settings.getBoolean(getString(R.string.pref_key_showed_v2_tutorial), false)) {
    		// If GPS is started, stop to clear the screen (we will start it again at the end of this method)
    	    boolean lastStartState = mStarted;
    		if (mStarted) {
    	    	gpsStop();    	    	
    	    }
    		
    		// Show the user a tutorial on using the ActionBar button to start/stop GPS,
    		// either on first execution or when the user choose the option in the Preferences
        	mOptions.shotType = ShowcaseView.TYPE_ONE_SHOT;
        	mOptions.block = false;
        	mOptions.hideOnClickOutside = true;
        	mOptions.noButton = true;
        	sv = ShowcaseView.insertShowcaseViewWithType(ShowcaseView.ITEM_ACTION_ITEM, R.id.gps_start, this,
        			R.string.showcase_gps_on_off_title, R.string.showcase_gps_on_off_message, mOptions);
        	sv.show();
    		
    		SharedPreferences.Editor editor = Application.getPrefs().edit();
    	    editor.putBoolean(getString(R.string.pref_key_showed_v2_tutorial), true);
    	    editor.commit();
    	    
    	    if (lastStartState) {
    	    	Handler h = new Handler();
    	    	// Restart the GPS, if it was previously started, with a slight delay to allow the UI to clear
    	    	// and allow the tutorial to be clearly visible
    	    	h.postDelayed(new Runnable() {
    	            public void run() {
    	                gpsStart();
    	            }
    	        }, 500);
    	    }    	    
    	}
    }
    
    @Override
    protected void onDestroy() {
    	mService.removeGpsStatusListener(this);
    	mService.removeUpdates(this);        
    	super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
    	getSupportMenuInflater().inflate(R.menu.gps_menu, menu);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(com.actionbarsherlock.view.Menu menu) {
    	MenuItem item = menu.findItem(R.id.gps_start);
        if (item != null) {
            if (mStarted) {
                item.setTitle(R.string.gps_pause);
                item.setIcon(R.drawable.av_pause);
            } else {
                item.setTitle(R.string.gps_start);
                item.setIcon(R.drawable.av_play);
            }
        }

        item = menu.findItem(R.id.delete_aiding_data);
        if (item != null) {
            item.setEnabled(!mStarted);
        }

        item = menu.findItem(R.id.send_location);
        if (item != null) {
            item.setVisible(mLastLocation != null);
        }

        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	boolean success;
    	// Handle menu item selection
    	switch (item.getItemId()) {
	        case R.id.gps_start:
	            if (mStarted) {
	                gpsStop();
	            } else {
	                gpsStart();
	            }
         	
	            return true;	
	        case R.id.delete_aiding_data:
	        	success = sendExtraCommand(getString(R.string.delete_aiding_data_command));
	        	if(success){
	    			Toast.makeText(this,getString(R.string.delete_aiding_data_success),
	    					Toast.LENGTH_SHORT).show();
	    		}else{	    			
	    			Toast.makeText(this,getString(R.string.delete_aiding_data_failure),
	    					Toast.LENGTH_SHORT).show();
	    		}
	            return true;	
	        case R.id.send_location:
	            sendLocation();
	            return true;	
	        case R.id.force_time_injection:
	            success = sendExtraCommand(getString(R.string.force_time_injection_command));
	            if(success){
	    			Toast.makeText(this,getString(R.string.force_time_injection_success),
	    					Toast.LENGTH_SHORT).show();
	    		}else{	    			
	    			Toast.makeText(this,getString(R.string.force_time_injection_failure),
	    					Toast.LENGTH_SHORT).show();
	    		}
	            return true;	
	        case R.id.force_xtra_injection:
	            success = sendExtraCommand(getString(R.string.force_xtra_injection_command));
	            if(success){
	    			Toast.makeText(this,getString(R.string.force_xtra_injection_success),
	    					Toast.LENGTH_SHORT).show();
	    		}else{	    			
	    			Toast.makeText(this,getString(R.string.force_xtra_injection_failure),
	    					Toast.LENGTH_SHORT).show();
	    		}
	            return true;
	        case R.id.menu_settings:
				// Show settings menu
				startActivity(new Intent(this, Preferences.class));
	        default:
	        	return super.onOptionsItemSelected(item);
    	}
    }

    public void onLocationChanged(Location location) {
        mLastLocation = location;
        
      	// Reset the options menu to trigger updates to action bar menu items
      	invalidateOptionsMenu();

        for (GpsTestListener activity : mGpsTestListeners) {
            activity.onLocationChanged(location);
        }
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        for (GpsTestListener activity : mGpsTestListeners) {
            activity.onStatusChanged(provider, status, extras);
        }
    }

    public void onProviderEnabled(String provider) {
        for (GpsTestListener activity : mGpsTestListeners) {
            activity.onProviderEnabled(provider);
        }
    }

    public void onProviderDisabled(String provider) {
        for (GpsTestListener activity : mGpsTestListeners) {
            activity.onProviderDisabled(provider);
        }
    }

    public void onGpsStatusChanged(int event) {
        mStatus = mService.getGpsStatus(mStatus);
        
        switch (event) {
        	case GpsStatus.GPS_EVENT_STARTED:	            
	            break;	
	        case GpsStatus.GPS_EVENT_STOPPED:	            
	            break;	
	        case GpsStatus.GPS_EVENT_FIRST_FIX:
	        	int ttff = mStatus.getTimeToFirstFix();
	        	if (ttff == 0) {
	        		mTtff = "";
	        	} else {
	        		ttff = (ttff + 500) / 1000;
	        		mTtff = Integer.toString(ttff) + " sec";
	        	} 
	            break;	
	        case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
	        	// Stop progress bar after the first status information is obtained
	         	setSupportProgressBarIndeterminateVisibility(Boolean.FALSE);
	            break;
        }
        
        // If the user is viewing the tutorial, we don't want to clutter the status screen, so return
        if (sv != null && sv.isShown()) {
        	return;
        }
        
        for (GpsTestListener activity : mGpsTestListeners) {
            activity.onGpsStatusChanged(event, mStatus);
        }
    }

    private void sendLocation() {
        if (mLastLocation != null) {
            Intent intent = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts("mailto", "", null));
            String location = "http://maps.google.com/maps?geocode=&q=" +
                    Double.toString(mLastLocation.getLatitude()) + "," +
                    Double.toString(mLastLocation.getLongitude());
            intent.putExtra(Intent.EXTRA_TEXT, location);
            startActivity(intent);
        }
    }
    
	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {}
	
	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the primary sections of the app.
	 */
public class SectionsPagerAdapter extends FragmentStatePagerAdapter {

		public static final int NUMBER_OF_TABS = 3; // Used to set up TabListener

		// Constants for the different fragments that will be displayed in tabs, in numeric order
		public static final int GPS_STATUS_FRAGMENT = 0;
		public static final int GPS_MAP_FRAGMENT = 1;
		public static final int GPS_SKY_FRAGMENT = 2;
		
		// Maintain handle to Fragments to avoid recreating them if one already
		// exists
		Fragment gpsStatus, gpsMap, gpsSky;

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int i) {			
			switch (i) {
				case GPS_STATUS_FRAGMENT:					
					if (gpsStatus == null) {
						gpsStatus = new GpsStatusFragment();
					}					
					return gpsStatus;
				case GPS_MAP_FRAGMENT:					
					if (gpsMap == null) {
						gpsMap = new GpsMapFragment();
					}
					return gpsMap;
				case GPS_SKY_FRAGMENT:					
					if (gpsSky == null) {
						gpsSky = new GpsSkyFragment();
					}
					return gpsSky;
			}
			return null; // This should never happen
		}

		@Override
		public int getCount() {
			return NUMBER_OF_TABS;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
			case GPS_STATUS_FRAGMENT:
				return getString(R.string.gps_status_tab);			
			case GPS_MAP_FRAGMENT:
				return getString(R.string.gps_map_tab);			
			case GPS_SKY_FRAGMENT:
				return getString(R.string.gps_sky_tab);
			}
			return null; // This should never happen
		}
	}
	
	private void initActionBar(Bundle savedInstanceState) {
		// Set up the action bar.
     	final com.actionbarsherlock.app.ActionBar actionBar = getSupportActionBar();
     	actionBar
     			.setNavigationMode(com.actionbarsherlock.app.ActionBar.NAVIGATION_MODE_TABS);
     	actionBar.setTitle(getApplicationContext().getText(R.string.app_name));
		
		//  page adapter contains all the fragment registrations
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
				
     	// Set up the ViewPager with the sections adapter.
     	mViewPager = (ViewPagerMapBevelScroll) findViewById(R.id.pager);
     	mViewPager.setAdapter(mSectionsPagerAdapter);
     	mViewPager.setOffscreenPageLimit(2);
     	
   		// When swiping between different sections, select the corresponding
   		// tab. We can also use ActionBar.Tab#select() to do this if we have a
   		// reference to the Tab.
   		mViewPager
   				.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
   					@Override
   					public void onPageSelected(int position) {
   						actionBar.setSelectedNavigationItem(position);
   					}
   				});
   		// For each of the sections in the app, add a tab to the action bar.
   		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
   			// Create a tab with text corresponding to the page title defined by
   			// the adapter. Also specify this Activity object, which implements
   			// the TabListener interface, as the listener for when this tab is
   			// selected.
   			actionBar.addTab(actionBar.newTab()
   					.setText(mSectionsPagerAdapter.getPageTitle(i))
   					.setTabListener(this));
   		}
	}
}
