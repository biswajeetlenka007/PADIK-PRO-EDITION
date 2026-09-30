package padik.deadreckoning.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import padik.deadreckoning.R;

public class DegreeDialView extends View {

    private Paint circlePaint;
    private Paint tickPaint;
    private Paint tickBoldPaint;
    private Paint arrowPaint;
    private Paint textPaint;
    private Paint centerPaint;

    private float currentDegree = 0;
    private OnDegreeChangedListener listener;

    private RectF dialRect = new RectF();
    private float centerX, centerY;
    private float radius;

    private GestureDetector gestureDetector;

    private float lastTouchAngle = 0;
    private boolean isDragging = false;

    public interface OnDegreeChangedListener {
        void onDegreeChanged(float degree);
    }

    public DegreeDialView(Context context) {
        super(context);
        init(context);
    }

    public DegreeDialView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DegreeDialView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(8);
        circlePaint.setColor(ContextCompat.getColor(context, R.color.colorPrimary));

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(2);
        tickPaint.setColor(ContextCompat.getColor(context, R.color.textSecondary));

        tickBoldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickBoldPaint.setStyle(Paint.Style.STROKE);
        tickBoldPaint.setStrokeWidth(4);
        tickBoldPaint.setColor(ContextCompat.getColor(context, R.color.colorPrimary));

        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setColor(ContextCompat.getColor(context, R.color.colorAccent));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(36);
        textPaint.setColor(ContextCompat.getColor(context, R.color.colorPrimary));

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(ContextCompat.getColor(context, R.color.colorPrimary));

        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setOnDegreeChangedListener(OnDegreeChangedListener listener) {
        this.listener = listener;
    }

    public void setDegree(float degree) {
        this.currentDegree = degree % 360;
        if (this.currentDegree < 0) this.currentDegree += 360;
        invalidate();
    }

    public float getDegree() {
        return currentDegree;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float padding = 40;
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) / 2f - padding;
        
        dialRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        canvas.drawCircle(centerX, centerY, radius, circlePaint);

        for (int i = 0; i < 360; i += 10) {
            float angle = (float) Math.toRadians(i - 90);
            float innerRadius = i % 30 == 0 ? radius - 25 : radius - 15;
            
            Paint tick = (i % 30 == 0) ? tickBoldPaint : tickPaint;
            
            float startX = centerX + innerRadius * (float) Math.cos(angle);
            float startY = centerY + innerRadius * (float) Math.sin(angle);
            float endX = centerX + radius * (float) Math.cos(angle);
            float endY = centerY + radius * (float) Math.sin(angle);
            
            canvas.drawLine(startX, startY, endX, endY, tick);
            
            if (i % 30 == 0) {
                float textRadius = radius - 45;
                float textX = centerX + textRadius * (float) Math.cos(angle);
                float textY = centerY + textRadius * (float) Math.sin(angle) + 12;
                canvas.drawText(String.valueOf(i), textX, textY, textPaint);
            }
        }

        float arrowAngle = (float) Math.toRadians(currentDegree - 90);
        float arrowLength = radius - 35;
        
        float arrowTipX = centerX + arrowLength * (float) Math.cos(arrowAngle);
        float arrowTipY = centerY + arrowLength * (float) Math.sin(arrowAngle);
        
        float arrowBase1Angle = arrowAngle + (float) Math.toRadians(150);
        float arrowBase2Angle = arrowAngle - (float) Math.toRadians(150);
        
        float arrowBase1X = centerX + 20 * (float) Math.cos(arrowBase1Angle);
        float arrowBase1Y = centerY + 20 * (float) Math.sin(arrowBase1Angle);
        float arrowBase2X = centerX + 20 * (float) Math.cos(arrowBase2Angle);
        float arrowBase2Y = centerY + 20 * (float) Math.sin(arrowBase2Angle);
        
        android.graphics.Path arrowPath = new android.graphics.Path();
        arrowPath.moveTo(arrowTipX, arrowTipY);
        arrowPath.lineTo(arrowBase1X, arrowBase1Y);
        arrowPath.lineTo(arrowBase2X, arrowBase2Y);
        arrowPath.close();
        
        canvas.drawPath(arrowPath, arrowPaint);

        canvas.drawCircle(centerX, centerY, 12, centerPaint);

        String degreeText = String.format("%.0f°", currentDegree);
        textPaint.setTextSize(48);
        canvas.drawText(degreeText, centerX, centerY + radius + 60, textPaint);
        textPaint.setTextSize(36);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                float x = event.getX() - centerX;
                float y = event.getY() - centerY;
                lastTouchAngle = (float) Math.toDegrees(Math.atan2(y, x));
                isDragging = true;
                return true;
                
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    x = event.getX() - centerX;
                    y = event.getY() - centerY;
                    float newAngle = (float) Math.toDegrees(Math.atan2(y, x));
                    
                    float deltaAngle = newAngle - lastTouchAngle;
                    if (deltaAngle > 180) deltaAngle -= 360;
                    if (deltaAngle < -180) deltaAngle += 360;
                    
                    currentDegree += deltaAngle;
                    currentDegree = (currentDegree + 360) % 360;
                    
                    lastTouchAngle = newAngle;
                    
                    if (listener != null) {
                        listener.onDegreeChanged(currentDegree);
                    }
                    
                    invalidate();
                    return true;
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                break;
        }
        
        return super.onTouchEvent(event);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    }
}
