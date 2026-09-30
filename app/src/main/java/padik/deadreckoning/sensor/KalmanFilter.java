package padik.deadreckoning.sensor;

public class KalmanFilter {
    
    private double q;
    private double r;
    private double x;
    private double p;
    private double k;
    
    public KalmanFilter(double processNoise, double measurementNoise, double initialEstimate, double initialError) {
        this.q = processNoise;
        this.r = measurementNoise;
        this.x = initialEstimate;
        this.p = initialError;
        this.k = 0;
    }
    
    public double update(double measurement) {
        prediction();
        correction(measurement);
        return x;
    }
    
    private void prediction() {
        p = p + q;
    }
    
    private void correction(double measurement) {
        k = p / (p + r);
        x = x + k * (measurement - x);
        p = (1 - k) * p;
    }
    
    public void reset(double initialEstimate, double initialError) {
        this.x = initialEstimate;
        this.p = initialError;
        this.k = 0;
    }

    public void wrapX(double min, double max) {
        double range = max - min;
        while (x > max) x -= range;
        while (x < min) x += range;
    }
    
    public double getEstimate() {
        return x;
    }
    
    public double getError() {
        return p;
    }
}
