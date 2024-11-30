package test;

import benchmark.internal.Benchmark;
import benchmark.objects.A;

public class ContextSensitivity1 {

  public ContextSensitivity1() {}

  public void callee(A a, A b) {
    Benchmark.test(1, b);
  }

  public void test1() {
    Benchmark.alloc(1);
    A a1 = new A();
    A b1 = a1;
    callee(a1, b1);
    Benchmark.test(2, b1);
  }

  public void test2() {
    A a2 = new A();
    Benchmark.alloc(2);
    A b2 = new A();
    callee(a2, b2);
    Benchmark.test(3, b2);
  }

  public static void main(String[] args) {
    ContextSensitivity1 cs1 = new ContextSensitivity1();
    cs1.test1();
    cs1.test2();
  }
}
