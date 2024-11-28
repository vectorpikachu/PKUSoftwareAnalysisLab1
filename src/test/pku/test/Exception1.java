package test;

import benchmark.internal.Benchmark;
import benchmark.objects.B;

class CustomException extends Exception {
}


public class Exception1 {

    public static void main(String[] args) {
        Benchmark.alloc(1);
        B a = new B();
        Benchmark.alloc(2);
        B b = new B();
        Benchmark.alloc(3);
        B c = new B();
        try {
            if (args.length > 1) {
                a = b;
                throw new CustomException();
            }
        } catch (CustomException e) {
            c = a;
        }
        Benchmark.test(1, a);
        Benchmark.test(2, c);
    }
}
/*
Answer:
  1 : 1 2
  2 : 2 3
*/
