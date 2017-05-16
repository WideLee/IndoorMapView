package sysu.mobile.limk.library.indoormapview;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;

public class LocationSymbol extends BaseMapSymbol {

    private float mRadius;
    private Paint mCirclePaint = null;
    private Paint mRangeCirclePaint = null;
    private Paint mCircleEdgePaint = null;
    private float mRangeRadius = 0;
    private int mRangeCircleColor = 0x00ffffff;

    public LocationSymbol(int mMainColor, int mEdgeColor, float mRadius) {
        this.mThreshold = 0f;
        this.mRotation = 0f;
        this.mVisibility = true;
        this.mOnMapSymbolListener = null;
        this.mRadius = mRadius;
        mCirclePaint = new Paint();
        mCirclePaint.setStyle(Style.FILL);
        mCirclePaint.setColor(mMainColor);
        mCirclePaint.setAntiAlias(true);
        mCircleEdgePaint = new Paint();
        mCircleEdgePaint.setStyle(Style.STROKE);
        mCircleEdgePaint.setColor(mEdgeColor);
        mCirclePaint.setAntiAlias(true);
    }

    public void setRangeCircle(float rangeCircleRadius, int rangeCircleColor) {
        mRangeRadius = rangeCircleRadius;
        mRangeCirclePaint = new Paint();
        mRangeCirclePaint.setStyle(Style.FILL);
        mRangeCircleColor = rangeCircleColor;
        mRangeCirclePaint.setColor(mRangeCircleColor);
    }

    @Override
    public void draw(Canvas canvas, Matrix matrix, float scale) {
        if (!mVisibility || scale < mThreshold)
            return;
        float[] locationValue = new float[]{(float) mLocation.getX(),
                (float) mLocation.getY()};
        matrix.mapPoints(locationValue);

        // paint range circle
        if (mRangeCirclePaint != null) {
            float radiusValue = mRangeRadius * scale;
            canvas.drawCircle(locationValue[0], locationValue[1], radiusValue,
                    mRangeCirclePaint);
        }
        // paint circle edge
        canvas.drawCircle(locationValue[0], locationValue[1], mRadius,
                mCirclePaint);
        // paint circle
        canvas.drawCircle(locationValue[0], locationValue[1], mRadius + 1,
                mCircleEdgePaint);
    }

    @Override
    public boolean isPointInClickRect(float x, float y) {
        return false;
    }
}
