package padik.deadreckoning.model;

public class IMUSample {

    private final long timestamp;

    private final double ax;
    private final double ay;
    private final double az;

    private final double gx;
    private final double gy;
    private final double gz;

    public IMUSample(
            long timestamp,
            double ax,
            double ay,
            double az,
            double gx,
            double gy,
            double gz) {

        this.timestamp = timestamp;

        this.ax = ax;
        this.ay = ay;
        this.az = az;

        this.gx = gx;
        this.gy = gy;
        this.gz = gz;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getAx() {
        return ax;
    }

    public double getAy() {
        return ay;
    }

    public double getAz() {
        return az;
    }

    public double getGx() {
        return gx;
    }

    public double getGy() {
        return gy;
    }

    public double getGz() {
        return gz;
    }
}
