package solutions.overlayylmao.overlayylmao;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class OverlayService extends AccessibilityService {

    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_DATA = "data";
    static final String EXTRA_PRESET = "present";

    private static String TAG = OverlayService.class.getSimpleName();

    private WindowManager windowManager;
    private ImageView imageOverlay;
    private SurfaceView surfaceOverlay;
    private Timer mTimer;
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private DisplayMetrics mRealMetrics = new DisplayMetrics();
    private DisplayMetrics mFakeMetrics = new DisplayMetrics();
    private SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    private boolean useImage = false;
    private Preset preset;


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d("got event", event.toString());
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) {
            Log.d(TAG, "no source");
            return;
        }
        Log.d(TAG, "res: " + source.getViewIdResourceName());
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);

        preset = intent.getParcelableExtra(EXTRA_PRESET);
        useImage = preset.updateTime > 0;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(mRealMetrics);
        windowManager.getDefaultDisplay().getMetrics(mFakeMetrics);

        setupImageWatching();

        mTimer = new Timer();
        super.onCreate();

        if (useImage) {
            mTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    imageHandler.sendEmptyMessage(0);
                }
            }, 0, preset.updateTime < 25 ? 25 : preset.updateTime);
        } else {
            surfaceHandler.sendEmptyMessage(0);
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    surfaceHandler.sendEmptyMessage(0);
                }
            }, 1000);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (useImage && imageOverlay != null && imageOverlay.isAttachedToWindow()) windowManager.removeView(imageOverlay);
        if (!useImage && surfaceOverlay != null && surfaceOverlay.isAttachedToWindow()) windowManager.removeView(surfaceOverlay);
        if (mMediaProjection != null) mMediaProjection.stop();
        if (mVirtualDisplay != null) mVirtualDisplay.release();
        mTimer.cancel();
    }

    private void setupImageWatching() {
        if (mImageReader != null) mImageReader.close();
        if (mVirtualDisplay != null) mVirtualDisplay.release();
        DisplayMetrics metrics = preset.coverStatusBar ? mRealMetrics : mFakeMetrics;
        mImageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2);

        mVirtualDisplay = mMediaProjection.createVirtualDisplay("Overlayy lmao",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
    }

    private final Handler surfaceHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            boolean firstTime = false;
            if (surfaceOverlay == null) {
                firstTime = true;
                surfaceOverlay = new SurfaceView(OverlayService.this);
//                surfaceOverlay.setRotation(90);
            }

            DisplayMetrics metrics = preset.coverStatusBar ? mRealMetrics : mFakeMetrics;

            mVirtualDisplay = mMediaProjection.createVirtualDisplay("Overlayy lmao",
                    metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surfaceOverlay.getHolder().getSurface(), null, null);

            if (firstTime) windowManager.addView(surfaceOverlay, getParams());
        }
    };

    private final Handler imageHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            Log.d("OVERLAYY", "imagehandler");
            if(imageOverlay == null) {
                Log.d("service", "first time");
                imageOverlay = new ImageView(OverlayService.this);
            }

            if (imageOverlay.isAttachedToWindow()) windowManager.removeView(imageOverlay);

            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    imageHandler2.sendEmptyMessage(0);
                }
            }, 25);

        }
    };

    private final Handler imageHandler2 = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d("OVERLAYY", "imagehandlerTWO");
            DisplayMetrics metrics = preset.coverStatusBar ? mRealMetrics : mFakeMetrics;
            Image image = mImageReader.acquireLatestImage();
            if (image != null) {
                int width = metrics.widthPixels;
                int height = image.getHeight();
                final Image.Plane[] planes = image.getPlanes();
                final ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;
                Bitmap bitmap = Bitmap.createBitmap(width+rowPadding/pixelStride, height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);
                Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                bitmap.recycle();
                imageOverlay.setImageBitmap(newBitmap);
                image.close();
                setupImageWatching();
            }

            WindowManager.LayoutParams params = getParams();

            if(!imageOverlay.isAttachedToWindow()) windowManager.addView(imageOverlay, params);
        }
    };

    private WindowManager.LayoutParams getParams() {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if (preset.coverStatusBar) flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (preset.coverNavBar) flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);

        params.gravity = preset.horizontalGravity | preset.verticalGravity;

        params.x = preset.xOffset;
        params.y = preset.yOffset;

        DisplayMetrics metrics = preset.coverStatusBar ? mRealMetrics : mFakeMetrics;

        params.width = (int) (metrics.widthPixels * (preset.width / 100f));
        params.height = (int) (metrics.heightPixels * (preset.height / 100f));

        return params;

    }
}
