package test;

import benchmark.internal.Benchmark;

public class Recursion1 {

  public Recursion1() {}

  public class N {
    public String value;
    public N next;

    public N(String value) {
      this.value = value;
      next = null;
    }
  }

  public N recursive(int i, N m) {
    if (i < 10) {
      int j = i + 1;
      Benchmark.alloc(2);
      m.next = new N("new");
      return recursive(j, m.next);
    }
    return m;
  }

  public void test() {
    Benchmark.alloc(1);
    N node = new N("");

    Recursion1 r1 = new Recursion1();
    N n = r1.recursive(0, node);

    N o = node.next;
    N p = node.next.next;
    N q = node.next.next.next;

    Benchmark.test(1, o);
    Benchmark.test(2, p);
    Benchmark.test(3, q);
    Benchmark.test(4, n);
  }

  public static void main(String[] args) {
    Recursion1 r1 = new Recursion1();
    r1.test();
  }
}
