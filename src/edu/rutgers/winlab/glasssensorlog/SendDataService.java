package edu.rutgers.winlab.glasssensorlog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.TimelineManager;

public class SendDataService extends Service {
    private static final String TAG = "SensorLogService";
    private static final String LIVE_CARD_TAG = "sensorlog";

    private static final boolean D = true;
    
    private TimelineManager mTimelineManager;
    private LiveCard mLiveCard;
    
    private SensorLogApplication sensorLog;
    private OrientationManager mOrientationManager;
    
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    
    // Key names received from the BluetoothManager Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Map<Integer, String> sensorTypes ;
    private Map<Integer, Sensor> sensors;
    
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothManager mBluetoothManager = null;
    
    private void publishCard(Context context){
    	if(mLiveCard == null){
    		mLiveCard = mTimelineManager.createLiveCard(LIVE_CARD_TAG);
    		mLiveCard.setViews(new RemoteViews(context.getPackageName(), R.layout.card_text));
    		Intent intent = new Intent(this, MenuActivity.class);
    		mLiveCard.setAction(PendingIntent.getActivity(context, 0, intent, 0));
    		mLiveCard.publish(LiveCard.PublishMode.REVEAL);
    		Log.d(TAG, "Done publishing LiveCard");
    		
    	}else{
    		//jump to the Live card when API is available
    		return;
    	}
    }
    
    private void unpublishCard(Context context){
    	if(mLiveCard != null){
    		Log.d(TAG, "Unpublishing LiveCard");
    		mLiveCard.unpublish();
    		mLiveCard = null;
    	}
    }
    
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
		mTimelineManager = TimelineManager.from(this);
		
		sensorLog = (SensorLogApplication) this.getApplication();

		publishCard(this);
		setupSensorLog();
		startRecording();
		
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        
        setupComm();
        
		//Setup Timer to update the live card
		if(isTimerUsed){ //Update user using RemoteViews 
			Timer timer = new Timer();
			timer.scheduleAtFixedRate(new UpdateLiveCardTask(), 1000, 5000);
			counter = 0;
		}
	}
	
	
	
	//----------------------------
	// 		TIMER
	//----------------------------
	
	private boolean isTimerUsed = false;
	private int counter; 
	
	class UpdateLiveCardTask extends TimerTask{
		public void run(){
			counter = (counter + 1) % 3;
			switch(counter){
			case 0:
				Log.d(TAG, "0");
				mLiveCard.setViews(new RemoteViews(SendDataService.this.getPackageName(), R.layout.card_text0));
				break;
			case 1:	
				mLiveCard.setViews(new RemoteViews(SendDataService.this.getPackageName(), R.layout.card_text1));
				Log.d(TAG, "1");
				break;
			case 2:	
				mLiveCard.setViews(new RemoteViews(SendDataService.this.getPackageName(), R.layout.card_text2));
				Log.d(TAG, "2");
				break;
			default:
				mLiveCard.setViews(new RemoteViews(SendDataService.this.getPackageName(), R.layout.card_text0));
				break;
			}
			mLiveCard.unpublish();
			mLiveCard.publish(LiveCard.PublishMode.REVEAL);
    	}
	}
	
	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		unpublishCard(this);
		super.onDestroy();
		this.sensorLog.setServiceRunning(false);
        // Stop the Bluetooth chat services
        if (mBluetoothManager != null) mBluetoothManager.stop();
		stopRecording();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		this.sensorLog.setServiceRunning(true);
		publishCard(this);
        
		if (mBluetoothManager != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mBluetoothManager.getState() == BluetoothManager.STATE_NONE) {
              // Start the Bluetooth chat services
            	mBluetoothManager.start();
            }
        }
		return START_STICKY;
	}
	
    private void setupComm() {
        Log.d(TAG, "setupComm()");

        // Initialize the BluetoothChatService to perform bluetooth connections
        mBluetoothManager = new BluetoothManager(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
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
        	//new WriteToFile().execute(file, "HEADING", orientationManager.getHeading());
       
        	new WriteToFileAndBluetooth().execute(file, "HEADING", orientationManager.getHeading());
            
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
			//new WriteToFile().execute(file,sensorTypes.get(event.sensor.getType()), event.values);
			new WriteToFileAndBluetooth().execute(file,sensorTypes.get(event.sensor.getType()), event.values);
			
		}
	};
	
	private LocationListener locationListener = new LocationListener() {

		@Override
		public void onLocationChanged(Location location) {
			//new WriteToFile().execute(file, "GPS", location);
			new WriteToFileAndBluetooth().execute(file, "GPS", location);
			
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
	

	
    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mBluetoothManager.getState() != mBluetoothManager.STATE_CONNECTED) {
            //Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mBluetoothManager.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }
    }
    
    private class WriteToFileAndBluetooth extends AsyncTask {
    	
    	@Override
    	protected Object doInBackground(Object... params) {

    		BufferedWriter file = (BufferedWriter) params[0];

    		if(params[1].equals("GPS")){
    			Location location = (Location) params[2];
    			write(file, "GPS", new String[] {Double.toString(location.getLatitude()), Double.toString(location.getLongitude()), Float.toString(location.getBearing()), Float.toString(location.getSpeed()), Long.toString(location.getTime())});
    		}
    		else if (params[1].equals("ENTER") || params[1].equals("EXIT")){
    			write(file, (String) params[1], (String[]) null);
    		}
    		else if (params[1].equals("HEADING")){
    			write(file, (String) "HEADING", new String[]{String.valueOf(params[2])});
    		}
    		else {
    			float[] values = (float[]) params[2];
    			String[] array = new String[values.length];
    			for (int i=0; i<values.length; i++) {
    				array[i] = Float.toString(values[i]);
    			}
    			write(file, (String)params[1], array );

    		}

    		return null;
    	}
    	
    	private void write(BufferedWriter file, String tag, String[] values) {
    		if (file == null) {
    			return;
    		}

    		String line = "";
    		if (values != null) {
    			for (String value : values) {
    				line += "," + value;
    			}
    		}
    		line = Long.toString(System.currentTimeMillis()) + "," + tag + line + "\n";

    		try {
    			//Log.d(TAG, line);
    			if(file !=null){
    				file.write(line);
    				sendMessage(line);
    			}
    			
    		} catch (IOException e) { 
    			e.printStackTrace();
    		}
    	}

    }
	// The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case BluetoothManager.MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothManager.STATE_CONNECTED:
                    break;
                case BluetoothManager.STATE_CONNECTING:
                    break;
                case BluetoothManager.STATE_LISTEN:
                case BluetoothManager.STATE_NONE:
                    break;
                }
                break;
            case BluetoothManager.MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                //mConversationArrayAdapter.add("Me:  " + writeMessage);
                break;
            case BluetoothManager.MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                Toast.makeText(getApplicationContext(), readMessage, Toast.LENGTH_SHORT).show();
                if(readMessage.contains("REQ_INFO")){
                	//QUICK PROTOTYPE - need to fix! 
                	Location location = null;
                	location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                	if(location==null){
                		location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                		if(location == null)
                			location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                	}
                	String message = "ACK_INFO," + 
                			String.valueOf(mOrientationManager.getHeading()) +
                			"," +
                			location.getLatitude() +
                			"," +
                			location.getLongitude() 
                			;
               ;
                	SendDataService.this.sendMessage(message);
                }
                break;
            case BluetoothManager.MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case BluetoothManager.MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
}
