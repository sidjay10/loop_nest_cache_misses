/*void testcase1() {
    int cachePower = 16; // cache size = 2^16B
    int blockPower = 5; // block size = 2^5B
    int stride = 1;
    int N = 1024;
    long[] A = new long[1024];
    String cacheType = "DirectMapped";

    for (int i = 0;i < N;i+=1){
        A[i] = 0;
    }
}

void testcase2() {
    int cachePower = 16; // cache size = 2^16B
    int blockPower = 5; // block size = 2^5B
    int N = 256;
    int[][] Z = new int[N][N];
    String cacheType = "DirectMapped";
    for (int i = 0; i < N; i += 1) {
        for (int j = 0; j < N; j += 1) {
            Z[i][j] = 0;
        }
    }
}

void testcase3() {
    int cachePower = 18; // cache size = 2^18B
    int blockPower = 6; // block size = 2^6B
    int N = 256;
    int[][] A = new int[N][N];
    int[][] B = new int[N][N];
    int[][] C = new int[N][N];
    String cacheType = "DirectMapped";
    for (int i = 0; i < N; i += 1) {
        for (int j = 0; j < N; j += 1) {
            int sum = 0;
            C[i][j] = 0;
            for (int k = 0; k < N; k += 1) {
                sum += A[i][k] * B[k][j];
            }
            C[i][j] = sum;
        }
    }
}*/

void testcase4() {
    int cachePower = 17; // cache size = 2^18B
    int blockPower = 5; // block size = 2^6B
    int N = 512;
    int[][] A = new int[N][512];
    int[][] B = new int[512][N];
    int[][] C = new int[512][512];
    String cacheType = "DirectMapped";
    for (int j = 0; j < N; j += 1) {
        for (int i = 0; i < N; i += 1) {
            for(int k = 0 ; k < N ; k += 1){
                C[i][j] += A[i][k] * B[k][j];
            }
        }
    }
}

void testcase5() {
    int cachePower = 24; // cache size = 2^18B
    int blockPower = 6; // block size = 2^6B
    int N = 4096;
    double[][] A = new double[N][N];
    double[][] X = new double[N][N];
    double[] y = new double[4096];
    String cacheType = "DirectMapped";
    for (int k = 0; k < N; k += 1) {
        for (int j = 0; j < N; j += 1) {
            for(int i = 0 ; i < N ; i += 1){
                y[i] = y[i] + A[i][j]*X[k][j];
            }
        }
    }
}
