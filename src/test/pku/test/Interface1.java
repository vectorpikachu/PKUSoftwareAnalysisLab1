package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.G;
import benchmark.objects.I;

public class Interface1 {
    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a = new A();
        Benchmark.alloc(2);
        G g = new G();
        I i = g;
        A a2 = i.foo(a);
        Benchmark.test(1, a2);
    }
}
