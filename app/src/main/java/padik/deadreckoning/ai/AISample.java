package padik.deadreckoning.ai;

public class AISample {
    public long timestampNs;
    public float[] features = new float[15];

    public AISample(long timestampNs, float[] features) {
        this.timestampNs = timestampNs;
        if (features.length == 15) {
            System.arraycopy(features, 0, this.features, 0, 15);
        }
    }
}
