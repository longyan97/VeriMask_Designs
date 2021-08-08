package spqr.n95decon;

public class OptimSetting {

    public int optTemp = 0;  // optimum heating device temperature
    public int optTmh = 0;   // optimum total cycle time (min)
    public int optNumCycs = 0; // optimum total number of cycles given the
    public int optMPC = 0;  //  optimum number of successful masks per cycle
    public String[] optFailNodes = {}; // the nodes that failed and should be removed under the opt settings


    public void update(int testTemp, int testTmh, int testNumCycs, int testMPC, String[] testFailNodes) {
        optTemp = testTemp;
        optTmh = testTmh;
        optNumCycs = testNumCycs;
        optMPC = testMPC;
        optFailNodes = testFailNodes;
    }
}
