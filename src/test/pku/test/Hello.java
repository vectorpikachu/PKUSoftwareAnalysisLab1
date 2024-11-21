package test;

import benchmark.internal.Benchmark;
import benchmark.objects.B;

public class Hello {

  public static void main(String[] args) {
    Benchmark.alloc(3);
    B b = new B();
    Benchmark.alloc(5);
    B c = new B();
    Benchmark.alloc(4);
    B d = new B();
    Benchmark.alloc(10);
    B e = new B();
    c = d;
    b = e;
    Benchmark.test(7, b);
    Benchmark.test(9, c);
  }
}
/*
Answer:
  7 : 3
*/
