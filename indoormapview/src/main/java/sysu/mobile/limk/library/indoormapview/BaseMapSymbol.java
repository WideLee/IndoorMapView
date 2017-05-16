package sysu.mobile.limk.library.indoormapview;

import android.graphics.Canvas;
import android.graphics.Matrix;

public abstract class BaseMapSymbol {
    protected OnMapSymbolListener mOnMapSymbolListener;
    protected float mThreshold;
    protected Position mLocation;
    protected Object mTag;
    protected float mRotation;
    protected boolean mVisibility;

    public abstract void draw(Canvas canvas, Matrix matrix, float scale);

    public abstract boolean isPointInClickRect(float x, float y);

    public void setOnMapSymbolListener(OnMapSymbolListener mOnMapSymbolListener) {
        this.mOnMapSymbolListener = mOnMapSymbolListener;
    }

    public Position getLocation() {
        return mLocation;
    }

    public void setLocation(Position position) {
        mLocation = position;
    }

    public boolean isVisible() {
        return mVisibility;
    }

    public void setVisible(boolean visible) {
        mVisibility = visible;
    }

    public float getThreshold() {
        return mThreshold;
    }
}
