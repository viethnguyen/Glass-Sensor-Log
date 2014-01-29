package edu.rutgers.winlab.glasssensorlog;

import java.io.BufferedWriter;
import java.io.IOException;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

public class WriteToFile extends AsyncTask {
	private static final String TAG = AsyncTask.class.getSimpleName();
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
			}
			
		} catch (IOException e) { 
			e.printStackTrace();
		}
	}

}
