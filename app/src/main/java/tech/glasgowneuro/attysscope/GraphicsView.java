package tech.glasgowneuro.attysscope;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.view.SurfaceView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

/**
 * Created by paul on 23/12/16.
 */

public class GraphicsView extends SurfaceView implements SurfaceHolder.Callback {


    private String TAG = GraphicsView.class.getSimpleName();

    static private SurfaceHolder holder = null;
    static private Canvas canvas = null;
    static private int xpos = 50;
    static private int ypos = 50;
    static private int radius = 100;

    static private Paint paint = new Paint();
    static private Paint paintBlack = new Paint();
    static private Paint paintWhite = new Paint();
    static private Paint paintRed = new Paint();
    static private Paint paintHalfWhite = new Paint();
    static private Paint paintTrans = new Paint();
    static private Bitmap bmp;
    static private int bmpWidth=1, bmpHeight=1;


    public GraphicsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
            Log.d(TAG, "GraphicsView - Constructor #1");
    }



    public GraphicsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
            Log.d(TAG, "GraphicsView - Constructor #2");
    }

    public GraphicsView(Context context) {
        super(context);
        init();
            Log.d(TAG, "GraphicsView - Constructor #3");
    }

    private void init() {
        Log.v(TAG, "GraphicsView - INIT()");
        holder = getHolder();
        holder.setFormat(PixelFormat.TRANSLUCENT);
        paint.setColor(Color.argb(255, 0, 128, 128));
        paintBlack.setColor(Color.BLACK);
        paintWhite.setColor(Color.WHITE);
        paintRed.setColor(Color.RED);
        paintTrans.setColor(Color.TRANSPARENT);
        paintHalfWhite.setColor(Color.argb(128, 255, 255, 128));
        bmp = BitmapFactory.decodeResource(getResources(), R.drawable.heartbeat);
//        bmp = BitmapFactory.decodeResource(getResources(), R.drawable.heartbeat3);

    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//        initYpos(width);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    public void surfaceCreated(SurfaceHolder holder) {
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
    }


    public synchronized void drawHeartbeat(){
        Surface surface = holder.getSurface();
        if (surface.isValid()) {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, radius, paint);
            } else {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "GraphicsView: Canvas==null");
                }
            }
            holder.unlockCanvasAndPost(canvas);
            canvas = null;
        }
    }

    public synchronized void drawHeartbeat(int rad){
        Surface surface = holder.getSurface();

        if (surface.isValid()) {
            canvas = holder.lockCanvas();
            if (canvas != null) {
//                canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, radius, paintBlack);
                Paint paint = new Paint();
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                canvas.drawPaint(paint);

//                canvas.drawColor(Color.argb(0, 32, 32, 32));
//                canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, rad, paint);

//                canvas.drawBitmap(Bitmap.createScaledBitmap(bmp, bmpWidth, bmpHeight, false),
//                        (canvas.getWidth() / 2) - (bmpWidth / 2), (canvas.getHeight() / 2) - (bmpHeight / 2), paintBlack);

                bmpWidth = Math.max(1, rad * 10);
                bmpWidth = Math.min(bmpWidth, canvas.getWidth() );

                bmpHeight = Math.max(1, rad * 10);
                bmpHeight = Math.min(bmpHeight, canvas.getHeight() );

                canvas.drawBitmap(Bitmap.createScaledBitmap(bmp, bmpWidth, bmpHeight, false),
                        (canvas.getWidth() / 2) - (bmpWidth / 2), 2 * (canvas.getHeight() / 3) - (bmpHeight / 2), paintWhite);

                Log.d(TAG, "Radius = " + radius);
                Log.d(TAG, "Rad = " + rad);

            } else {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "GraphicsView: Canvas==null");
                }
            }
            holder.unlockCanvasAndPost(canvas);
            canvas = null;
            radius = rad;
        }

    }

}
