package test;

import benchmark.internal.Benchmark;

public class Invocation {
    public static class Obj {
      public Obj() {}
      public Obj f1() { return this; }
        public Obj f3(Obj b) { return b; }
    }

    public static Obj f2(Obj a1) {
      Benchmark.test(1, a1);
      Benchmark.alloc(2);
      Obj a2 = new Obj();
      return a2;
    }

    public static void main(String[] args) {
      Benchmark.alloc(1);
      Obj a3 = new Obj();
      Obj a4 = f2(a3);
      Benchmark.test(2, a4);
      Obj a5 = a4.f1();
      //Obj a6 = a5.f3(a3);
      Benchmark.test(3, a5);
      //Benchmark.test(4, a6);
    }

}
/*
Answer:
  1 : 1
  2 : 2
  3 : 2
*/
