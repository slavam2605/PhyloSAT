package beepp.util;

/**
 * @author Vyacheslav Moklev
 */
public class Pair<U, V> {
    public U a;
    public V b;

    public Pair(U a, V b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public String toString() {
        return "(" + a + ", " + b + ")";
    }
}
