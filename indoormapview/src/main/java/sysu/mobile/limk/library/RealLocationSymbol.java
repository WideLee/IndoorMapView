package sysu.mobile.limk.library;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

public class RealLocationSymbol extends BaseMapSymbol {

    private Bitmap mBitmap;
    private boolean isMoving;

    public void setMoving(boolean isMoving) {
        this.isMoving = isMoving;
    }

    private Rect mClickRect = new Rect(0, 0, 0, 0);
    private OnMapSymbolListener mRealClickListener = new OnMapSymbolListener() {

        @Override
        public boolean onMapSymbolClick(BaseMapSymbol mapSymbol) {
            return true;
        }
    };

    public RealLocationSymbol(Bitmap bitmap, int width, int height) {
        mBitmap = getResizedBitmap(bitmap, width, height);

        mLocation = new Position();
        mRotation = 0f;
        mVisibility = true;
        mThreshold = 0f;
        setOnMapSymbolListener(mRealClickListener);
    }

    private void calDisplayRect() {
        if (mBitmap != null) {
            int left = (int) (mLocation.getX() - mBitmap.getWidth() / 2);
            int right = left + mBitmap.getWidth();
            int top = (int) (mLocation.getY() - mBitmap.getHeight());
            int bottom = top + mBitmap.getHeight();
            mClickRect.set(left, top, right, bottom);
        }
    }

    @Override
    public void draw(Canvas canvas, Matrix matrix, float scale) {
        if (!mVisibility || scale < mThreshold)
            return;
        float[] xy = {(float) mLocation.getX(), (float) mLocation.getY()};
        matrix.mapPoints(xy);

        Paint paint = new Paint();
        paint.setAlpha(isMoving ? 128 : 255);
        canvas.drawBitmap(mBitmap, xy[0] - mBitmap.getWidth() / 2, xy[1] - mBitmap.getHeight(), paint);

        calDisplayRect();
    }

    @Override
    public boolean isPointInClickRect(float x, float y) {
        return isPointInRect(x, y, mClickRect);
    }

    private boolean isPointInRect(float x, float y, Rect rect) {
        return rect != null && x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
    }

    private Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);

        return Bitmap.createBitmap(bm, 0, 0, width, height,
                matrix, false);
    }
}
