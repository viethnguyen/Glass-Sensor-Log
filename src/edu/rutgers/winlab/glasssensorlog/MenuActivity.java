package edu.rutgers.winlab.glasssensorlog;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class MenuActivity extends Activity {
	private static final String TAG = MenuActivity.class.getSimpleName();

	SensorLogApplication sensorLog;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sensorLog = (SensorLogApplication) getApplication();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		//Handle item selection
		switch(item.getItemId()){
		case R.id.exit_service:
			if(sensorLog.isServiceRunning()){
				stopService(new Intent(this, SensorLogService.class));
			}
			finish();
			return true;
			
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onOptionsMenuClosed(Menu menu) {
		//Nothing else to do, closing the Activity
		//finish();
	}

	@Override
	protected void onResume() {
		super.onResume();
		openOptionsMenu();
	}
	
}
