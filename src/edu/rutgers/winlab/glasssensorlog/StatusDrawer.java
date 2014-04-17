package edu.rutgers.winlab.glasssensorlog;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;

import com.google.android.glass.timeline.DirectRenderingCallback;

/**
 * 
 * @author vietnguyen
 *
 */
public class StatusDrawer implements DirectRenderingCallback {
	private static final String  TAG = StatusDrawer.class.getSimpleName();
	
	private final StatusView mStatusView;
	
	private SurfaceHolder mHolder;
	private boolean mRenderingPaused;
	
	private final StatusView.Listener mStatusListener = new StatusView.Listener() {

		@Override
		public void onChange() {
            if (mHolder != null) {
                draw(mStatusView);
            }
		}


	};
	
	public StatusDrawer(Context context){
		this(new StatusView(context));
	}
	
	public StatusDrawer(StatusView statusView){
		mStatusView = statusView;
		mStatusView.setListener(mStatusListener);
	}
	
	/**
     * Keeps the created {@link SurfaceHolder} and updates this class' rendering state.
     */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		//The creation of a new Surface implicityly resumes the rendering
		mRenderingPaused = false;
		mHolder = holder;
		updateRenderingState();
	}

    /**
     * Uses the provided {@code width} and {@code height} to measure and layout the inflated
     * {@link StatusView}
     */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
        // Measure and layout the view with the canvas dimensions.
        int measuredWidth = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int measuredHeight = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);

        mStatusView.measure(measuredWidth, measuredHeight);
        mStatusView.layout(0, 0, mStatusView.getMeasuredWidth(), mStatusView.getMeasuredHeight());
	}


    /**
     * Removes the {@link SurfaceHolder} used for drawing and stops rendering.
     */
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
        mHolder = null;
        updateRenderingState();
	}

    /**
     * Updates this class' rendering state according to the provided {@code paused} flag.
     */
	@Override
	public void renderingPaused(SurfaceHolder holder, boolean paused) {
        mRenderingPaused = paused;
        updateRenderingState();	
	}
	
    /**
     * Starts or stops rendering according to the {@link LiveCard}'s state.
     */
    private void updateRenderingState() {
        if (mHolder != null && !mRenderingPaused) {
            mStatusView.start();
        } else {
            mStatusView.stop();
        }
    }
	
    /**
     * Draws the view in the SurfaceHolder's canvas.
     */
    private void draw(View view) {
        Canvas canvas;
        try {
            canvas = mHolder.lockCanvas();
        } catch (Exception e) {
            Log.e(TAG, "Unable to lock canvas: " + e);
            return;
        }
        if (canvas != null) {
            view.draw(canvas);
            mHolder.unlockCanvasAndPost(canvas);
        }
    }

}
