package test;

import benchmark.internal.Benchmark;

public class Loops1 {

    public class N {
        public String value = "";
        public N next;

        public N() {
            Benchmark.alloc(2);
            next = new N();
        }
    }

    private void test() {
        Benchmark.alloc(1);
        N node = new N();

        int i = 0;
        while (i < 10) {
            node = node.next;
            i++;
        }

        N o = node.next;
        N p = node.next.next;
        N q = node.next.next.next;

        Benchmark.test(1, node);
        Benchmark.test(2, o);
        Benchmark.test(3, p);
        Benchmark.test(4, q);
    }

    public static void main(String[] args) {
        Loops1 l1 = new Loops1();
        l1.test();
    }
}
