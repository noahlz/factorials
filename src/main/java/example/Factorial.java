package example;

public class Factorial implements ValueComputer<Integer> {

    public static void main(String[] args) {

        System.out.println("factorial using factorial directly...");
        System.out.println("5! = " + calculate(5));

        System.out.println("factorial calculated recursively...");
        System.out.println("5! = " + calculateRecursively(5));

        System.out.println("factorial from a \"factorial builder\"...");
        Factorial.Builder builder = new Factorial.Builder();
        builder.factorial(5);
        ValueComputer computer = builder.build();
        System.out.println("5! = " + computer.compute());

    }

    public static int calculate(int n) {
        int result = 1;
        for (int i = 1; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    public static int calculateRecursively(int n) {
        if(n == 0) return 1;
        return (n * calculateRecursively(n - 1));
    }

    private final int n;

    private Factorial(int n) {
        this.n = n;
    }

    public Integer compute() {
        return calculate(n);
    }

    public static class Builder {
        private int n;
        public Builder factorial(int n) {
            this.n = n;
            return this;
        }
        public Factorial build() {
            return new Factorial(n);
        }
    }

}
