package test;

import benchmark.internal.Benchmark;
import benchmark.objects.B;

public class Field5 {

    public static class BB
    {
        public B val;
        public BB() {
            Benchmark.alloc(2);
            this.val = new B();
        }

        public BB(B b) {
            this.val = b;
        }

        public B getval() {
            return val;
        }
    }

    public static class BBB
    {
        public BB val;
        public BBB() {
            this.val = new BB();
        }

        public BBB(BB b) {
            this.val = b;
        }

        public BB getval() {
            return val;
        }
    }

    public static void main(String[] args) {
        Benchmark.alloc(1);
        BB bb1 = new BB();
        //BB bb2 = new BB();
        Benchmark.alloc(3);
        BBB bbb1 = new BBB();
        Benchmark.alloc(4);
        //BBB bbb2 = new BBB();
        Benchmark.alloc(5);
        B b1 = new B();
        Benchmark.alloc(6);
        B b2 = new B();

        bb1.val = b1;
        B b3 = bb1.getval();
        Benchmark.test(1, b3);
        bbb1.val = bb1;
        bbb1.val.val = b2;
        Benchmark.test(2, bbb1.val);
        Benchmark.test(3, bbb1.val.val);

    }
}

/*
Answer:
  1 : 5
  2 : 2 3
  3 : 5 6
*/
