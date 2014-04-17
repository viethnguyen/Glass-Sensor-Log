package edu.rutgers.winlab.glasssensorlog;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;

public class SensorLogService extends Service {

    private static final String TAG = SensorLogService.class.getSimpleName();
    private static final String LIVE_CARD_TAG = "sensorlog";
    
    private LiveCard mLiveCard;
    
    private SensorLogApplication sensorLog;
    private OrientationManager mOrientationManager;
    
    private void publishCard(Context context){
    	if(mLiveCard == null){
    		mLiveCard = new LiveCard(this, LIVE_CARD_TAG);
    		mLiveCard.setViews(new RemoteViews(context.getPackageName(), R.layout.card_text));
    		Intent intent = new Intent(this, MenuActivity.class);
    		mLiveCard.setAction(PendingIntent.getActivity(context, 0, intent, 0));
    		mLiveCard.publish(LiveCard.PublishMode.REVEAL);
    		Log.d(TAG, "Done publishing LiveCard");
    		
    	}else{
    		mLiveCard.navigate();
    	}
    }
    
    private void unpublishCard(Context context){
    	if(mLiveCard != null){
    		Log.d(TAG, "Unpublishing LiveCard");
    		mLiveCard.unpublish();
    		mLiveCard = null;
    	}
    }
    
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Map<Integer, String> sensorTypes ;
    private Map<Integer, Sensor> sensors;
    
    private void setupSensorLog(){
    	//Get sensors to be captured
		sensorTypes = new HashMap<Integer, String>();
		sensors = new HashMap<Integer, Sensor>();
    	sensorTypes.put(Sensor.TYPE_ACCELEROMETER, "ACCEL");
    	sensorTypes.put(Sensor.TYPE_GYROSCOPE, "GYRO");
		sensorTypes.put(Sensor.TYPE_LINEAR_ACCELERATION, "LINEAR");
		sensorTypes.put(Sensor.TYPE_MAGNETIC_FIELD, "MAG");
		sensorTypes.put(Sensor.TYPE_GRAVITY, "GRAV");
		sensorTypes.put(Sensor.TYPE_ROTATION_VECTOR, "ROTATION");
		
		//set up managers: sensor, location, orientation
    	sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    	
    	for(Integer type : sensorTypes.keySet()){
    		sensors.put(type, sensorManager.getDefaultSensor(type));
    	}
    	locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
    	
    	mOrientationManager = new OrientationManager(sensorManager, locationManager);
    }
    
	@Override
	public void onCreate() {
		super.onCreate();
		
		sensorLog = (SensorLogApplication) this.getApplication();

		setupSensorLog();
		startRecording();
		
	}
	
	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		unpublishCard(this);
		super.onDestroy();
		this.sensorLog.setServiceRunning(false);
		stopRecording();
	}
	
	private StatusDrawer mCallback;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		this.sensorLog.setServiceRunning(true);
		if(mLiveCard == null){
			mLiveCard = new LiveCard(this, LIVE_CARD_TAG);
			
			//Keep track of the callback to remove it before unpublishing
			mCallback = new StatusDrawer(this);
			mLiveCard.setDirectRenderingEnabled(true).getSurfaceHolder().addCallback(mCallback);
			
			Intent menuIntent = new Intent(this, MenuActivity.class);
			menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));
			mLiveCard.attach(this);
			mLiveCard.publish(PublishMode.REVEAL);
		}
		else{
			mLiveCard.navigate();
		}
		
		return START_STICKY;
	}
	
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	public BufferedWriter file;
	
	private String currentFilename ="";
	
	private void startRecording(){
		//Prepare data storage
		File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		String currentFilename = "log_" + System.currentTimeMillis() + ".csv";
		File filename = new File(directory, currentFilename);
		sensorLog.setCurrentFilepath(filename.getAbsolutePath());
		try{
			file = new BufferedWriter(new FileWriter(filename));
		}catch(IOException e){
			e.printStackTrace();
		}
		
		//register sensor listeners
		for(Sensor sensor:sensors.values()){
			sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
		}
		
		//register orientation listener
        mOrientationManager.addOnChangedListener(mCompassListener);
        mOrientationManager.start();
		
		//register location listener
		List<String> providers = locationManager.getAllProviders();
		for(String provider: providers){
			Log.d(TAG, provider + " - isEnabled: " + String.valueOf(locationManager.isProviderEnabled(provider)));
			locationManager.requestLocationUpdates(provider, 0, 0, locationListener);
		}
		
	}
	
    private final OrientationManager.OnChangedListener mCompassListener =
            new OrientationManager.OnChangedListener() {

        @Override
        public void onOrientationChanged(OrientationManager orientationManager) {
        	new WriteToFile().execute(file, "HEADING", orientationManager.getHeading());
        }

        @Override
        public void onLocationChanged(OrientationManager orientationManager) {

        }

        @Override
        public void onAccuracyChanged(OrientationManager orientationManager) {

        }
    };
	
	private void stopRecording(){
		sensorManager.unregisterListener(sensorListener);
		locationManager.removeUpdates(locationListener);
		try{
			file.close();
			Log.d(TAG, "File closed");
			file = null;
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	private SensorEventListener sensorListener = new SensorEventListener(){
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy){
			
		}
		
		@Override
		public void onSensorChanged(SensorEvent event){
			//Write to file 
			new WriteToFile().execute(file,sensorTypes.get(event.sensor.getType()), event.values);
		}
	};
	
	private LocationListener locationListener = new LocationListener() {

		@Override
		public void onLocationChanged(Location location) {
			Log.d(TAG,"Location changed");
			new WriteToFile().execute(file, "GPS", location);
		}

		@Override
		public void onProviderDisabled(String provider) {
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}

	};
}
