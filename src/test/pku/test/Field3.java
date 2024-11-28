package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class Field3 {
    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a1 = new A();
        Benchmark.alloc(2);
        A a2 = new A();
        Benchmark.alloc(3);
        B b1 = new B();
        Benchmark.alloc(4);
        B b2 = new B();
        a1.f = b1;
        a2.f = b2;
        B c = new B();
        for (int i = 0; i < args.length; i++) {
            c = a1.f;
            a1 = a2;
        }
        Benchmark.test(1, c);
    }
}
/*
Answer:
  1 : 3 4
*/
