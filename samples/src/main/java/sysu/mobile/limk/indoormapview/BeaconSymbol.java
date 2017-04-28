package sysu.mobile.limk.indoormapview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;

import sysu.mobile.limk.library.BaseMapSymbol;
import sysu.mobile.limk.library.Position;

public class BeaconSymbol extends BaseMapSymbol {
    private Bitmap mBitmap;
    private Rect mClickRect = new Rect(0, 0, 0, 0);

    public BeaconSymbol(Context context, Position location) {
        mThreshold = 1.2f;
        mLocation = new Position(location.getX(), location.getY());
        mRotation = 0f;
        mVisibility = true;
        mBitmap = BitmapFactory.decodeResource(context.getResources(), R.mipmap.beacon);
    }

    private void calDisplayRect() {
        if (mBitmap != null) {
            int left = (int) (mLocation.getX() - mBitmap.getWidth() / 2);
            int right = left + mBitmap.getWidth();
            int top = (int) (mLocation.getY() - mBitmap.getHeight() / 2);
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

        canvas.drawBitmap(mBitmap, xy[0] - mBitmap.getWidth() / 2, xy[1] - mBitmap.getHeight() / 2,
                null);

        calDisplayRect();
    }

    @Override
    public boolean isPointInClickRect(float x, float y) {
        return isPointInRect(x, y, mClickRect);
    }

    private boolean isPointInRect(float x, float y, Rect rect) {
        return rect != null && x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
    }

}
