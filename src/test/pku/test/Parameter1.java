package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;

public class Parameter1 {

  public static void test(A x) {
    A b = x;
    Benchmark.test(1, b);
  }

  public static void main(String[] args) {

    Benchmark.alloc(1);
    A a = new A();
    test(a);
  }
}
