package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;

public class Interprocedural1 {

    public static void alloc(A x, A y) {
        x.f = y.f;
    }

    public static void main(String[] args) {

        A a = new A();
        A b = new A();

        Benchmark.alloc(1);
        b.f = new B();
        alloc(a, b);

        B x = a.f;
        B y = b.f;
        Benchmark.test(2, x);
    }
}
