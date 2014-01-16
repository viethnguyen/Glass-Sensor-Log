package edu.rutgers.winlab.glasssensorlog;

import android.app.Application;

public class SensorLogApplication extends Application {//This consists of common code that all other parts can access
	private boolean serviceRunning;
	private String currentFilepath;
	
	public boolean isServiceRunning(){
		return serviceRunning;
	}
	
	public void setServiceRunning(boolean serviceRunning){
		this.serviceRunning = serviceRunning;
	}
	
	public String getCurrentFilepath(){
		return currentFilepath;
	}
	
	public void setCurrentFilepath(String filepath){
		currentFilepath = filepath;
	}
}
