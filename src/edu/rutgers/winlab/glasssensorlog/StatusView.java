package edu.rutgers.winlab.glasssensorlog;

import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

public class StatusView extends FrameLayout{


    /**
     * Interface to listen for changes in the countdown.
     */
    public interface Listener {
        /** Notified of a change in the view. */
        public void onChange();
    }
    
    /** About 24 FPS, visible for testing. */
    static final long DELAY_MILLIS = 1000;

    private final TextView mStatusView;
    

    private final Handler mHandler = new Handler();
    private final Runnable mUpdateTextRunnable = new Runnable() {

        @Override
        public void run() {
            if (mRunning) {
                updateText();
                postDelayed(mUpdateTextRunnable, DELAY_MILLIS);
            }
        }
    };

    private boolean mStarted;
    private boolean mForceStart;
    private boolean mVisible;
    private boolean mRunning;

    private long mBaseMillis;

    private Listener mChangeListener;

    public StatusView(Context context) {
        this(context, null, 0);
    }

    public StatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StatusView(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        LayoutInflater.from(context).inflate(R.layout.card_status, this);

        mStatusView = (TextView) findViewById(R.id.status_update);
        
        
        setBaseMillis(getElapsedRealtime());
    }

    /**
     * Sets the base value of the chronometer in milliseconds.
     */
    public void setBaseMillis(long baseMillis) {
        mBaseMillis = baseMillis;
        updateText();
    }

    /**
     * Gets the base value of the chronometer in milliseconds.
     */
    public long getBaseMillis() {
        return mBaseMillis;
    }

    /**
     * Sets a {@link Listener}.
     */
    public void setListener(Listener listener) {
        mChangeListener = listener;
    }

    /**
     * Returns the set {@link Listener}.
     */
    public Listener getListener() {
        return mChangeListener;
    }

    /**
     * Starts the chronometer.
     */
    public void start() {
        if (!mRunning) {
            postDelayed(mUpdateTextRunnable, DELAY_MILLIS);
        }
        mRunning = true;
    }

    /**
     * Stops the chronometer.
     */
    public void stop() {
        if (mRunning) {
            removeCallbacks(mUpdateTextRunnable);
        }
        mRunning = false;
    }

    @Override
    public boolean postDelayed(Runnable action, long delayMillis) {
        return mHandler.postDelayed(action, delayMillis);
    }

    @Override
    public boolean removeCallbacks(Runnable action) {
        mHandler.removeCallbacks(action);
        return true;
    }

    /**
     * Returns {@link SystemClock.elapsedRealtime}, overridable for testing.
     */
    protected long getElapsedRealtime() {
        return SystemClock.elapsedRealtime();
    }

    static int step = 0;
    /**
     * Updates the value of the chronometer, visible for testing.
     */
    void updateText() {
        
        step = (step + 1) % 3;
        String[] changingText = {"Logging.", "Logging..", "Logging..."};
        
        mStatusView.setText(changingText[step]);
        
        if (mChangeListener != null) {
            mChangeListener.onChange();
        }
    }

}
