package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class Array {
    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a1 = new A();
        Benchmark.alloc(2);
        A a2 = new A();
        Benchmark.alloc(4);
        B b1 = new B();

        B[] bArray1 = new B[5];  // Array of B objects
        for (int i = 0; i < bArray1.length; i++) {
            bArray1[i] = new B();
        }
        Benchmark.alloc(3);
        B b2 = new B();
        bArray1[1] = b2;

        a1.f = bArray1[0];  // Assign array to field f of a1
        if (args.length > 1) a2.f = bArray1[1];  // Optionally assign array to field f of a2
        b1 = bArray1[0];
        Benchmark.test(1, b1);
    }
}
