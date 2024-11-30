package test;

import benchmark.internal.BenchmarkN;
import benchmark.objects.A;

public class Cast1 {
    public static void main(String[] args) {
        BenchmarkN.alloc(1);
        Object a = new A();
        BenchmarkN.test(1, a);

        if (a instanceof A) {
            A b = (A) a; // Safe cast
            BenchmarkN.test(2, b);
        }
    }
}
/*
Expected Output:
        1: 1
        2: 1
*/
