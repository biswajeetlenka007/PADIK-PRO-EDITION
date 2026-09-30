package padik.deadreckoning.sensor;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

/**
 * Defines vehicle-motion constraints (Non-Holonomic Constraints).
 * Assumes lateral and vertical velocity in vehicle frame are zero.
 */
public class NonHolonomicConstraint {

    /**
     * Calculates the NHC Jacobian H and Innovation y.
     * 
     * @param R_vb Vehicle-to-Body rotation matrix
     * @param R_nb Navigation-to-Body rotation matrix
     * @param v_n Velocity in Navigation frame (NED)
     * @return Results containing H and y
     */
    public static NHCResult calculate(DMatrixRMaj R_vb, DMatrixRMaj R_nb, double[] v_n) {
        // R_vn = R_vb^T * R_nb^T
        DMatrixRMaj R_bn = new DMatrixRMaj(3, 3);
        CommonOps_DDRM.transpose(R_nb, R_bn);
        
        DMatrixRMaj R_vn = new DMatrixRMaj(3, 3);
        CommonOps_DDRM.multTransA(R_vb, R_bn, R_vn);

        // Innovation y = z - h(x) = [0,0] - [v_v_y, v_v_z]
        double[] vv = new double[3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                vv[i] += R_vn.get(i, j) * v_n[j];
            }
        }
        
        DMatrixRMaj y = new DMatrixRMaj(2, 1);
        y.set(0, 0, 0 - vv[1]);
        y.set(1, 0, 0 - vv[2]);

        // Jacobian H = [0, R_vn_row23, -R_vn_row23*[v_n]x, 0, 0] (2x15)
        DMatrixRMaj vnx = skewSymmetric(v_n);
        DMatrixRMaj H = new DMatrixRMaj(2, 15);
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                // Velocity part: rows 2 and 3 of R_vn
                H.set(r, 3 + c, R_vn.get(r + 1, c));
                
                // Attitude part: rows 2 and 3 of (-R_vn * [vn]x)
                double attVal = 0;
                for (int k = 0; k < 3; k++) {
                    attVal += -R_vn.get(r + 1, k) * vnx.get(k, c);
                }
                H.set(r, 6 + c, attVal);
            }
        }

        return new NHCResult(H, y);
    }

    private static DMatrixRMaj skewSymmetric(double[] v) {
        DMatrixRMaj m = new DMatrixRMaj(3, 3);
        m.set(0, 1, -v[2]); m.set(0, 2, v[1]);
        m.set(1, 0, v[2]);  m.set(1, 2, -v[0]);
        m.set(2, 0, -v[1]); m.set(2, 1, v[0]);
        return m;
    }

    public static class NHCResult {
        public final DMatrixRMaj H;
        public final DMatrixRMaj y;

        public NHCResult(DMatrixRMaj H, DMatrixRMaj y) {
            this.H = H;
            this.y = y;
        }
    }
}
