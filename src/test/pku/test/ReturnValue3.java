package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;


public class ReturnValue3 {

  public static A id(A x) {
    A y = new A();
    Benchmark.alloc(1);
    y.f = new B();
    return y;
  }

  public static void main(String[] args) {

    A a = new A();
    A b = id(a);
    B x = b.f;
    B y = a.f;
    Benchmark.test(1, x);
  }
}
