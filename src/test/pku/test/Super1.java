package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.P;
import benchmark.objects.Q;

public class Super1 {
    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a = new A();
        Benchmark.alloc(2);
        P p = new P(a);
        Benchmark.alloc(3);
        A a2 = new A();
        p.alias(a2);
        A a1 = p.getA();
        Benchmark.test(1, p);
        Benchmark.test(2, a1);
    }
}
