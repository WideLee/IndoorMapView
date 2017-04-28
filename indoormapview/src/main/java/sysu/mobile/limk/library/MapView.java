package sysu.mobile.limk.library;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MapView extends View {
    // attributes
    private boolean mDebug;

    private int mMyLocationColor = 0xFFFF0000;
    private int mCircleEdgeColor = 0xFFFFFFFF;
    private int mMyLocationRangeColor = 0x1EFF0000;
    private float mMyLocationRadius = 64;
    private float mMyLocationRangeRadius = 64;

    private float mMinScale = 0.1f;
    private float mMaxScale = 4f;


    // local parameters
    private float mScale = 1;
    private long mFloorId;
    private float mInitRotation = 0;
    private float mInitScale = 1;
    private boolean mIsTrackPosition = false;
    private boolean mInitMapView = false;

    private Matrix mMapMatrix = new Matrix();
    private Matrix mDetailMapMatrix = new Matrix();
    private Matrix mShadowMapMatrix = new Matrix();
    private BitmapRegionDecoder mMapDecoder;
    private Bitmap mShadowBitmap;
    private Bitmap mDetailBitmap;
    private Handler mDecodeBitmapHandler;
    private Context mContext;
    private Rect mScreenRect = null;
    private LocationSymbol mMyLocationSymbol;
    private RealLocationSymbol mRealLocationSymbol;

    // for the gesture detection
    private boolean mIsMoved = false;
    private boolean mIsRealLocationMove = false;

    private PointF[] mPreviousTouchPoints = {new PointF(), new PointF()};
    private int mPreviousPointerCount = 0;

    public ReentrantReadWriteLock mMapLock = new ReentrantReadWriteLock();

    // data container
    private List<BaseMapSymbol> mMapSymbols = new ArrayList<>();

    private OnRealLocationMoveListener mOnRealLocationMoveListener = null;
    private TranslateAnimRunnable mTranslateAnimRunnable = null;
    private UpdateMyLocationAnimRunnable mUpdateMyLocationAnimRunnable = null;

    private class TranslateAnimRunnable implements Runnable {

        private float mMoveToX;
        private float mMoveToY;
        private boolean mIsRunning = false;

        TranslateAnimRunnable(float x, float y) {
            mMoveToX = x;
            mMoveToY = y;
        }

        @Override
        public void run() {
            mIsRunning = true;
            try {
                while (!mInitMapView) {
                    Thread.sleep(50);
                }
                while (true) {
                    mMapLock.writeLock().lock();
                    float[] pointValue = new float[]{mMoveToX, mMoveToY};
                    mMapMatrix.mapPoints(pointValue);
                    PointF step = new PointF(
                            (getWidth() / 2f - pointValue[0]) / 2,
                            (getHeight() / 2f - pointValue[1]) / 2);
                    if (Math.abs(step.x) > 10 || Math.abs(step.y) > 10) {
                        mMapMatrix.postTranslate(step.x, step.y);
                        calScreenRect();
                        if (getHandler() != null) {
                            getHandler().post(new Runnable() {
                                @Override
                                public void run() {
                                    invalidate();
                                }
                            });
                        }
                        mMapLock.writeLock().unlock();
                    } else {
                        refreshDetailBitmap();
                        mMapLock.writeLock().unlock();
                        break;
                    }
                    Thread.sleep(30);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            mIsRunning = false;
        }

        void setMoveToPoint(float x, float y) {
            mMoveToX = x;
            mMoveToY = y;
        }

        boolean isRunning() {
            return mIsRunning;
        }

    }

    private class UpdateMyLocationAnimRunnable implements Runnable {

        private float[] mTargetPoint;
        private boolean mIsRunning = false;

        UpdateMyLocationAnimRunnable(float x, float y) {
            mTargetPoint = new float[2];
            mTargetPoint[0] = x;
            mTargetPoint[1] = y;
        }

        boolean isRunning() {
            return mIsRunning;
        }

        void setTarget(float x, float y) {
            if (mTargetPoint == null)
                mTargetPoint = new float[2];
            mTargetPoint[0] = x;
            mTargetPoint[1] = y;
        }

        @Override
        public void run() {
            while (true) {
                mIsRunning = true;
                PointF step = new PointF((float) (mTargetPoint[0] - mMyLocationSymbol
                        .getLocation().getX()) / 4,
                        (float) (mTargetPoint[1] - mMyLocationSymbol.getLocation()
                                .getY()) / 4);
                if (Math.abs(step.x) > 1 || Math.abs(step.y) > 1) {
                    mMyLocationSymbol.getLocation().setX(
                            mMyLocationSymbol.getLocation().getX() + step.x);
                    mMyLocationSymbol.getLocation().setY(
                            mMyLocationSymbol.getLocation().getY() + step.y);
                    if (getHandler() != null) {
                        getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                invalidate();
                            }
                        });
                    }
                } else {
                    mMyLocationSymbol.getLocation().setX(mTargetPoint[0]);
                    mMyLocationSymbol.getLocation().setY(mTargetPoint[1]);
                    if (getHandler() != null) {
                        getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                invalidate();
                            }
                        });
                    }
                    break;
                }
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (mIsTrackPosition) {
                centerMyLocation();
            }
            mIsRunning = false;

        }

    }

    public MapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        analyzeParams(context, attrs);
        init();
    }

    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        analyzeParams(context, attrs);
        init();
    }

    private void analyzeParams(Context context, AttributeSet attrs) {
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.MapView, 0, 0);
        try {
            mDebug = a.getBoolean(R.styleable.MapView_debug, false);
            mMaxScale = a.getFloat(R.styleable.MapView_max_scale, 4f);
            mMinScale = a.getFloat(R.styleable.MapView_min_scale, 0.1f);
            mMyLocationColor = a.getColor(R.styleable.MapView_mylocation_color, 0xFFFF0000);
            mCircleEdgeColor = a.getColor(R.styleable.MapView_circle_edge_color, 0xFFFFFFFF);
            mMyLocationRangeColor = a.getColor(R.styleable.MapView_mylocation_range_color, 0x1EFF0000);
            mMyLocationRadius = a.getDimension(R.styleable.MapView_mylocation_radius, 10);
            mMyLocationRangeRadius = a.getDimension(R.styleable.MapView_mylocation_range_radius, 20);
        } finally {
            a.recycle();
        }
    }

    private void init() {
        mContext = getContext();
        mMyLocationSymbol = new LocationSymbol(mMyLocationColor,
                mCircleEdgeColor, mMyLocationRadius);
        mMyLocationSymbol.setRangeCircle(mMyLocationRangeRadius,
                mMyLocationRangeColor);

        setBackgroundColor(Color.GRAY);
        HandlerThread decodeBitmapThread = new HandlerThread("decodeBitmap");
        decodeBitmapThread.start();
        mDecodeBitmapHandler = new Handler(decodeBitmapThread.getLooper());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mMapLock.isWriteLocked() || mShadowBitmap == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_UP:
                if ((!mIsMoved) && event.getPointerCount() == 1) {
                    onClick(event.getX(), event.getY(), mMapMatrix, mScale);
                }
                refreshDetailBitmap();
                mIsMoved = false;
                mIsRealLocationMove = false;
                mRealLocationSymbol.setMoving(mIsRealLocationMove);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                mPreviousTouchPoints[0].set(event.getX(), event.getY());
                if (event.getPointerCount() > 1)
                    mPreviousTouchPoints[1].set(event.getX(1), event.getY(1));
                mPreviousPointerCount = event.getPointerCount();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN:

                float[] xy = {event.getX(), event.getY()};
                xy = transformToMapCoordinate(xy);
                if (mRealLocationSymbol.isPointInClickRect(xy[0], xy[1])
                        && mRealLocationSymbol.mOnMapSymbolListener != null) {
                    mIsRealLocationMove = true;
                    mRealLocationSymbol.setMoving(mIsRealLocationMove);
                }

                mPreviousTouchPoints[0].set(event.getX(), event.getY());
                if (event.getPointerCount() > 1)
                    mPreviousTouchPoints[1].set(event.getX(1), event.getY(1));
                mPreviousPointerCount = event.getPointerCount();
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1
                        && (event.getX() - mPreviousTouchPoints[0].x)
                        * (event.getX() - mPreviousTouchPoints[0].x)
                        + (event.getY() - mPreviousTouchPoints[0].y)
                        * (event.getY() - mPreviousTouchPoints[0].y) < 1)
                    break;
                if (event.getPointerCount() >= 2
                        && MathUtil.squareDistance(
                        new PointF(event.getX(), event.getY()),
                        mPreviousTouchPoints[0]) < 20
                        && MathUtil.squareDistance(
                        new PointF(event.getX(1), event.getY(1)),
                        mPreviousTouchPoints[1]) < 20)
                    break;
                mIsMoved = true;
                if (event.getPointerCount() == mPreviousPointerCount) {
                    if (event.getPointerCount() == 1) {
                        // Judge is dragging the real locaiton symbol
                        if (!mIsRealLocationMove) {
                            float delX = event.getX() - mPreviousTouchPoints[0].x;
                            float delY = event.getY() - mPreviousTouchPoints[0].y;
                            mMapMatrix.postTranslate(delX, delY);
                            calScreenRect();
                            Log.v("onTouch", "trans:" + delX + "," + delY + " matrix:" + mMapMatrix.toShortString());
                            onTranslate(delX, delY, mMapMatrix, mScale);
                        } else {
                            Position curPos = new Position();
                            float delX = event.getX() - mPreviousTouchPoints[0].x;
                            float delY = event.getY() - mPreviousTouchPoints[0].y;

                            float[] viewPos = transformToViewCoordinate(
                                    new float[]{(float) mRealLocationSymbol.getLocation().getX(),
                                            (float) mRealLocationSymbol.getLocation().getY()});
                            float[] mapPos = transformToMapCoordinate(new float[]{viewPos[0] + delX, viewPos[1] + delY});
                            curPos.setX(mapPos[0]);
                            curPos.setY(mapPos[1]);
                            mRealLocationSymbol.setLocation(curPos);

                            if (mOnRealLocationMoveListener != null) {
                                mOnRealLocationMoveListener.onMove(curPos);
                            }
                        }
                    } else {
                        PointF preMid = MathUtil.midPoint(mPreviousTouchPoints[0],
                                mPreviousTouchPoints[1]);
                        PointF curMid = MathUtil.midPoint(new PointF(event.getX(),
                                        event.getY()),
                                new PointF(event.getX(1), event.getY(1)));
                        double preDis = MathUtil.distance(mPreviousTouchPoints[0],
                                mPreviousTouchPoints[1]);
                        double curDis = MathUtil.distance(new PointF(event.getX(),
                                        event.getY()),
                                new PointF(event.getX(1), event.getY(1)));
                        if (MathUtil.distance(curMid, preMid) > 8) {
                            mMapMatrix.postTranslate(curMid.x - preMid.x, curMid.y
                                    - preMid.y);
                            calScreenRect();
                        }
                        float scale = (float) (curDis / preDis);
                        Log.v("scale", scale + "#####" + mScale);
                        if ((scale >= 1 && mScale < mMaxScale)
                                || (scale <= 1 && mScale > mMinScale)) {
                            mMapMatrix.postScale(scale, scale, preMid.x, preMid.y);
                            updateScale();
                            onScale(scale, scale, mMapMatrix, mScale);
                        }
                        double preAngle = MathUtil.angle(mPreviousTouchPoints[0],
                                preMid);
                        double curAngle = MathUtil.angle(new PointF(event.getX(),
                                event.getY()), curMid);
                        float delAngle = (float) ((curAngle - preAngle) / Math.PI * 180);
                        mMapMatrix.postRotate(delAngle, preMid.x, preMid.y);
                        calScreenRect();
                        onRotate(delAngle, preMid.x, preMid.y, mMapMatrix, mScale);
                    }
                }
                invalidate();
                break;
            default:
                break;
        }
        if (event.getPointerCount() == 1) {
            mPreviousTouchPoints[0].set(event.getX(), event.getY());
        } else if (event.getPointerCount() > 1) {
            mPreviousTouchPoints[0].set(event.getX(), event.getY());
            mPreviousTouchPoints[1].set(event.getX(1), event.getY(1));
        }
        mPreviousPointerCount = event.getPointerCount();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Log.d("On Draw", mMapLock.isWriteLocked() + "");

        canvas.drawColor(Color.GRAY);
        mMapLock.readLock().lock();
        Matrix curMapMatrix = new Matrix(mMapMatrix);
        Matrix shadowMatrix = new Matrix(mShadowMapMatrix);
        shadowMatrix.postConcat(curMapMatrix);
        if (mShadowBitmap != null) {
            canvas.drawBitmap(mShadowBitmap, shadowMatrix, null);
            if (mDetailBitmap != null) {
                Matrix detailMatrix = new Matrix(mDetailMapMatrix);
                detailMatrix.postConcat(curMapMatrix);
                canvas.drawBitmap(mDetailBitmap, detailMatrix, null);
            }
            drawMapSymbols(canvas);
            if (mMyLocationSymbol != null
                    && mMyLocationSymbol.getLocation() != null) {
                mMyLocationSymbol.draw(canvas, mMapMatrix, mScale);
            }

            if (mRealLocationSymbol != null && mRealLocationSymbol.getLocation() != null) {
                mRealLocationSymbol.draw(canvas, mMapMatrix, mScale);
            }
        } else {
            canvas.drawColor(getResources().getColor(
                    android.R.color.holo_blue_light));
        }
        mMapLock.readLock().unlock();
    }

    private void drawMapSymbols(Canvas canvas) {
        if (mMapSymbols != null) {
            for (int i = 0; i < mMapSymbols.size(); i++) {
                BaseMapSymbol mapSymbol = mMapSymbols.get(i);
                mapSymbol.draw(canvas, mMapMatrix, mScale);
            }
        }
    }

    private void onRotate(float angle, float centerX, float centerY,
                          Matrix matrix, float scale) {
        Log.v("callback", "onTranslate" + " center: (" + centerX + ","
                + centerY + ")" + " matrix:" + matrix + " scale:" + scale);
    }

    private void onScale(float xScale, float yScale, Matrix matrix, float scale) {
        Log.v("callback", "onTranslate" + " ScaleDel: (" + xScale + ","
                + yScale + ")" + " matrix:" + matrix + " scale:" + scale);
    }

    private void onTranslate(float tranX, float tranY, Matrix matrix,
                             float scale) {
        mIsTrackPosition = false;
        Log.v("callback", "onTranslate" + " transDis: (" + tranX + "," + tranY
                + ")" + " matrix:" + matrix + " scale:" + scale);
    }

    private void onClick(float x, float y, Matrix matrix, float scale) {
        Log.v("callback", "onClick" + " Position: (" + x + "," + y + ")"
                + " matrix:" + matrix + " scale:" + scale);
        for (int i = mMapSymbols.size() - 1; i >= 0; i--) {
            BaseMapSymbol symbol = mMapSymbols.get(i);
            if ((!symbol.isVisible()) || symbol.getThreshold() > mScale)
                continue;
            Position location = symbol.getLocation();
            if (location != null) {
                float[] xy = {x, y};
                xy = transformToMapCoordinate(xy);
                if (symbol.isPointInClickRect(xy[0], xy[1])
                        && symbol.mOnMapSymbolListener != null) {
                    if (symbol.mOnMapSymbolListener
                            .onMapSymbolClick(mMapSymbols.get(i)))
                        break;
                }
            }
        }
    }

    private void calScreenRect() {
        Matrix invertMatrix = new Matrix();
        mMapMatrix.invert(invertMatrix);
        float[] center = {getWidth() / 2, getHeight() / 2};
        invertMatrix.mapPoints(center);
        double diagnal = Math.sqrt(getWidth() * getWidth() + getHeight()
                * getHeight())
                / mScale;
        int left = (int) Math.max(0, Math.round(center[0] - diagnal / 2));
        int top = (int) Math.max(0, Math.round(center[1] - diagnal / 2));
        int right = (int) Math.min(mMapDecoder.getWidth(),
                Math.round(center[0] + diagnal / 2));
        int bottom = (int) Math.min(mMapDecoder.getHeight(),
                Math.round(center[1] + diagnal / 2));
        Rect rect = new Rect(left, top, right, bottom);
        updateScreenRect(rect);
    }

    private void updateScreenRect(Rect rect) {
        mScreenRect = rect;
    }

    public void setMap(Bitmap bitmap) {
        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
        setBackground(drawable);
    }

    private void updateScale() {
        float[] matrixValue = new float[9];
        mMapMatrix.getValues(matrixValue);
        float scalex = matrixValue[Matrix.MSCALE_X];
        float skewy = matrixValue[Matrix.MSKEW_Y];
        mScale = (float) Math.sqrt(scalex * scalex + skewy * skewy);
        Log.v("update scale", mScale + "");
    }

    private void refreshDetailBitmap() {
        mDecodeBitmapHandler.post(new Runnable() {

            @Override
            public void run() {
                calScreenRect();
                Rect rect = mScreenRect;
                if (!rect.isEmpty()) {
                    Options options = new Options();
                    options.inSampleSize = dealSampeSize(
                            rect.right - rect.left, rect.bottom - rect.top);
                    mDetailBitmap = mMapDecoder.decodeRegion(rect, options);
                    mDetailMapMatrix = new Matrix();
                    mDetailMapMatrix.setScale(
                            rect.width() / mDetailBitmap.getWidth(),
                            rect.height() / mDetailBitmap.getHeight());
                    mDetailMapMatrix.postTranslate(rect.left, rect.top);
                    if (getHandler() != null) {
                        getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                postInvalidate();
                            }
                        });
                    }
                }
            }
        });
    }

    private int dealSampeSize(int width, int height) {
        int inSampleSize = (int) Math.ceil(1f / mScale);
        long freeMem = Runtime.getRuntime().maxMemory()
                - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime()
                .freeMemory()) - 15 * 1024 * 1024;
        if (freeMem - width * height * 4 / inSampleSize / inSampleSize < 0) {
            if (freeMem < 15 * 1024 * 1024)
                freeMem = (freeMem + 15 * 1024 * 1024) / 3;
            float size = ((float) (width * height * 4) / freeMem);
            inSampleSize = (int) Math.ceil(size);
        }
        return inSampleSize;
    }

    public float[] transformToViewCoordinate(float[] mapCoordinate) {
        float[] viewCoordinate = {mapCoordinate[0], mapCoordinate[1]};
        mMapMatrix.mapPoints(viewCoordinate);
        return viewCoordinate;
    }

    public float[] transformToMapCoordinate(float[] viewCoordinate) {
        float[] mapCoordinate = {viewCoordinate[0], viewCoordinate[1]};
        Matrix invertMatrix = new Matrix();
        mMapMatrix.invert(invertMatrix);
        invertMatrix.mapPoints(mapCoordinate);
        return mapCoordinate;
    }

    public void centerMyLocation() {
        centerSpecificLocation(mMyLocationSymbol.getLocation());
    }

    public void centerSpecificLocation(Position location) {
        translateToSpecificPoint((float) location.getX(), (float) location.getY());
    }

    private void translateToSpecificPoint(float x, float y) {
        if (mTranslateAnimRunnable == null) {
            mTranslateAnimRunnable = new TranslateAnimRunnable(x, y);
        } else {
            mTranslateAnimRunnable.setMoveToPoint(x, y);
        }
        if (!mTranslateAnimRunnable.isRunning()) {
            new Thread(mTranslateAnimRunnable).start();
        }
    }

    public long getFloorId() {
        return mFloorId;
    }

    public float getScale() {
        return mScale;
    }

    public void initNewMap(InputStream inputStream, double scale,
                           double rotation, Position currentPosition) {
        try {
            mMyLocationSymbol.setLocation(null);
            mMapDecoder = BitmapRegionDecoder.newInstance(inputStream, false);
            Rect rect = new Rect(0, 0, mMapDecoder.getWidth(),
                    mMapDecoder.getHeight());
            Options options = new Options();
            options.inJustDecodeBounds = true;
            options.inSampleSize = Math.round(Math.max(mMapDecoder.getWidth(),
                    mMapDecoder.getHeight()) / 1024f);
            Bitmap bitmap = mMapDecoder.decodeRegion(rect, options);
            mShadowBitmap = bitmap;
            mShadowMapMatrix = new Matrix();
            mShadowMapMatrix.setScale(rect.width() / mShadowBitmap.getWidth(),
                    rect.height() / mShadowBitmap.getHeight(), 0, 0);
            Matrix initMatrix = new Matrix();
            initMatrix.postScale((float) scale / mInitScale, (float) scale
                    / mInitScale);
            initMatrix.postRotate((float) rotation - mInitRotation);
            mMapMatrix = initMatrix;
            refreshDetailBitmap();
            invalidate();
            mInitScale = (float) scale;
            mInitRotation = (float) rotation;
            mInitMapView = true;

            Bitmap symbolBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.marker);

            mRealLocationSymbol = new RealLocationSymbol(symbolBitmap,
                    symbolBitmap.getWidth() / 2, symbolBitmap.getHeight() / 2);
            if (currentPosition == null) {
                Position centerInMap = new Position();
                centerInMap.setX(rect.width() / 2);
                centerInMap.setY(rect.height() / 2);
                mRealLocationSymbol.setLocation(centerInMap);
            } else {
                mRealLocationSymbol.setLocation(currentPosition);
            }
            if (mOnRealLocationMoveListener != null) {
                mOnRealLocationMoveListener.onMove(mRealLocationSymbol.getLocation());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void setMapSymbols(List<BaseMapSymbol> mapSymbols) {
        mMapSymbols = mapSymbols;
    }

    /**
     * update my location with animation
     *
     * @param location location
     */
    public void updateMyLocation(Position location) {
        if (mMyLocationSymbol.getLocation() == null) {
            mMyLocationSymbol.setLocation(location);
            centerMyLocation();
        } else {
            if (mUpdateMyLocationAnimRunnable == null) {
                mUpdateMyLocationAnimRunnable = new UpdateMyLocationAnimRunnable((float)
                        location.getX(), (float) location.getY());
            } else {
                mUpdateMyLocationAnimRunnable.setTarget((float) location.getX(),
                        (float) location.getY());
            }
            if (!mUpdateMyLocationAnimRunnable.isRunning()) {
                new Thread(mUpdateMyLocationAnimRunnable).start();
            }
        }
    }

    public List<BaseMapSymbol> getMapSymbols() {
        return mMapSymbols;
    }

    public LocationSymbol getmMyLocationSymbol() {
        return mMyLocationSymbol;
    }

    public void setTrackPosition() {
        mIsTrackPosition = true;
    }

    public Position getRealLocation() {
        return mRealLocationSymbol.getLocation();
    }

    public void setOnRealLocationMoveListener(OnRealLocationMoveListener mOnRealLocationMoveListener) {
        this.mOnRealLocationMoveListener = mOnRealLocationMoveListener;
        if (mOnRealLocationMoveListener != null && mRealLocationSymbol != null) {
            mOnRealLocationMoveListener.onMove(mRealLocationSymbol.getLocation());
        }
    }

    public void toggleRealLocationSymbol() {
        if (mRealLocationSymbol.isVisible()) {
            mRealLocationSymbol.setVisible(false);
            mMyLocationSymbol.setVisible(true);
        } else {
            mRealLocationSymbol.setVisible(true);
            mMyLocationSymbol.setVisible(false);
        }
    }

    private Size getScreenSize(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return new Size(dm.widthPixels, dm.heightPixels);
    }
}