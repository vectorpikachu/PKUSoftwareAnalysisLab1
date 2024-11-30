package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;
import benchmark.objects.B;


public class Interprocedural2 {

  public Interprocedural2() {}

  public void alloc(A x, A y) {
    x.f = y.f;
  }

  public static void main(String[] args) {

    A a = new A();
    A b = new A();

    Benchmark.alloc(1);
    b.f = new B();
    Interprocedural2 m2 = new Interprocedural2();
    m2.alloc(a, b);

    B x = a.f;
    B y = b.f;
    Benchmark.test(1, x);
  }
}
