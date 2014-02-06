/*******************************************************************************
 * Copyright 2014 Federico Iosue (federico.iosue@gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package it.feio.android.omninotes;

import it.feio.android.omninotes.db.DbHelper;
import it.feio.android.omninotes.models.PasswordValidator;
import it.feio.android.omninotes.utils.Constants;
import it.feio.android.omninotes.utils.Security;

import java.lang.reflect.Field;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.espian.showcaseview.ShowcaseView;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.GoogleAnalytics;
import com.google.analytics.tracking.android.Tracker;

@SuppressLint("Registered") 
public class BaseActivity extends ActionBarActivity {

	private final boolean TEST = false;
	
	protected DbHelper db;	
	protected Activity mActivity;
	protected Tracker tracker;
	
	protected SharedPreferences prefs;
	
	// Location variables
	protected LocationManager locationManager;
	protected LocationListener locationListener;
	protected Location currentLocation;
	protected double currentLatitude;
	protected double currentLongitude;
	protected double noteLatitude;
	protected double noteLongitude;

	protected String navigation;

	protected String date_time_format, time_format;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
				
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		/*
		 * Executing the application in test will activate ScrictMode to debug
		 * heavy i/o operations on main thread and data sending to GA will be
		 * disabled
		 */
		if (TEST) {
			StrictMode.enableDefaults();
			GoogleAnalytics.getInstance(this).setDryRun(true);
		}
		
		mActivity = this;

		// Preloads shared preferences for all derived classes
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		// The localized (12 or 24 hours) time format is initialized
		date_time_format = prefs.getBoolean("settings_hours_format", true) ? Constants.DATE_FORMAT_SHORT
				: Constants.DATE_FORMAT_SHORT_12;
		time_format = prefs.getBoolean("settings_hours_format", true) ? Constants.DATE_FORMAT_SHORT_TIME
				: Constants.DATE_FORMAT_SHORT_TIME_12;
		
		// Preparation of DbHelper
		db = new DbHelper(this);
		
		// Starts location manager
		setLocationManager();

		// Force menu overflow icon
		try {
	        ViewConfiguration config = ViewConfiguration.get(this);
	        Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
	        if(menuKeyField != null) {
	            menuKeyField.setAccessible(true);
	            menuKeyField.setBoolean(config, false);
	        }
	    } catch (Exception ex) {
	        // Ignore exceptions
	    }
		
		
		
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onStart() {
		super.onStart();
		// Google Analytics
		EasyTracker.getInstance(this).activityStart(this);
		tracker = GoogleAnalytics.getInstance(this).getTracker("UA-45502770-1");
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		// Navigation selected
		String navNotes = getResources().getStringArray(R.array.navigation_list_codes)[0];
		navigation = prefs.getString(Constants.PREF_NAVIGATION, navNotes);
	}

	@Override
	public void onStop() {
		super.onStop();
		// Google Analytics
		EasyTracker.getInstance(this).activityStop(this);
		if (locationManager != null)
			locationManager.removeUpdates(locationListener);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			Intent settingsIntent = new Intent(this, SettingsActivity.class);
			startActivity(settingsIntent);
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	

	private void setLocationManager() {
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

		locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				updateLocation(location);
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
			}
		};

		// A check is done to avoid crash when NETWORK_PROVIDER is not 
		// available (ex. on emulator with API >= 11)
		if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
			locationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER, 60000, 50, locationListener);
		} else {
			locationManager.requestLocationUpdates(
					LocationManager.PASSIVE_PROVIDER, 60000, 50, locationListener);
		}
	}
    
    void updateLocation(Location location){
        currentLocation = location;
        currentLatitude = currentLocation.getLatitude();
        currentLongitude = currentLocation.getLongitude();
    }
    
    

	protected boolean navigationArchived() {
		return "1".equals(prefs.getString(Constants.PREF_NAVIGATION, "0"));
	}

	protected void showToast(CharSequence text, int duration) {
		if (prefs.getBoolean("settings_enable_info", true)) {
			Toast.makeText(getApplicationContext(), text, duration).show();
		}
	}
	
	
	
	/**
	 * Method to validate security password to protect notes.
	 * It uses an interface callback.
	 * @param password
	 * @param mPasswordValidator
	 */
	protected void requestPassword(final PasswordValidator mPasswordValidator) {
		
		final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
				this);

		// Inflate layout
		LayoutInflater inflater = getLayoutInflater();
		final View v = inflater.inflate(R.layout.password_request_dialog_layout, null);
		alertDialogBuilder.setView(v);

		// Set dialog message and button
		alertDialogBuilder.setMessage(
				getString(R.string.insert_security_password))
				.setPositiveButton(R.string.confirm, null);
		
		AlertDialog dialog = alertDialogBuilder.create();
		
		// Set a listener for dialog button press
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {

		    @Override
		    public void onShow(final DialogInterface dialog) {

		        Button b = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
		        b.setOnClickListener(new View.OnClickListener() {

		            @Override
		            public void onClick(View view) {
		            	// When positive button is pressed password correctness is checked
		            	String oldPassword = prefs.getString(
								Constants.PREF_PASSWORD, "");
		            	TextView passwordTextView = (TextView)v.findViewById(R.id.password_request);
						String password = passwordTextView.getText().toString();
						// The check is done on password's hash stored in preferences
						boolean result = Security.md5(password).equals(oldPassword);

						// In case password is ok dialog is dismissed and result sent to callback
		                if (result) {
		                	dialog.dismiss();
							mPasswordValidator.onPasswordValidated(result);
						// If password is wrong the auth flow is not interrupted and simply a message is shown
		                } else {
		                	passwordTextView.setError(getString(R.string.wrong_password));
		                }
		            }
		        });
		    }
		});
		

		dialog.show();
	}
	
	
	
	
	protected void updateNavigation(String nav){
		prefs.edit().putString(Constants.PREF_NAVIGATION, nav).commit();
		navigation = nav;
	}
	
	
	
	/**
	 * Used for ShowCase library instructions
	 * @param istructionsName
	 * @param target
	 * @param type
	 */
	protected void showCase(String istructionsName, int target, int type) {
		if (!prefs.getBoolean(istructionsName, false)) {
			ShowcaseView.insertShowcaseViewWithType(type,
		            R.id.menu_add, this, istructionsName + "_title", istructionsName + "_detail", new ShowcaseView.ConfigOptions());
			prefs.edit().putBoolean(istructionsName, true).commit();
		}
	}
	


	

}
