package test;

import benchmark.internal.Benchmark;
import benchmark.objects.B;

public class MultiDimensionalArray {

    public static void main(String[] args) {
        // 创建一个 4x4 的二维数组
        Benchmark.alloc(1);
        B[][][] A = new B[2][][];
        Benchmark.alloc(2);
        A[0] = new B[2][];
        Benchmark.alloc(3);
        A[1] = new B[2][];
        Benchmark.alloc(4);
        A[0][0] = new B[2];
        Benchmark.alloc(5);
        A[0][1] = new B[2];
        Benchmark.alloc(6);
        A[1][0] = new B[2];
        Benchmark.alloc(7);
        A[1][1] = new B[2];
        Benchmark.alloc(8);
        A[0][0][0] = new B();
        Benchmark.alloc(9);
        A[0][0][1] = new B();
        Benchmark.alloc(10);
        A[0][1][0] = new B();
        Benchmark.alloc(11);
        A[0][1][1] = new B();
        Benchmark.alloc(12);
        A[1][0][0] = new B();
        Benchmark.alloc(13);
        A[1][0][1] = new B();
        Benchmark.alloc(14);
        A[1][1][0] = new B();
        Benchmark.alloc(15);
        A[1][1][1] = new B();
        Benchmark.test(1, A);
        Benchmark.test(2, A[0]);
        Benchmark.test(3, A[1]);
        Benchmark.test(4, A[0][0]);
        Benchmark.test(5, A[0][1]);
        Benchmark.test(6, A[1][0]);
        Benchmark.test(7, A[1][1]);
        Benchmark.test(8, A[1][0][1]);
        Benchmark.test(9, A[0][1][1]);
        Benchmark.test(10, A[0][1][0]);
    }
}

/*
Answer:
  1 : 1
  2 : 2
  3 : 3
  4 : 4
  5 : 5
  6 : 6
  7 : 7
  8 : 13
  9 : 11
  10 : 10
*/
