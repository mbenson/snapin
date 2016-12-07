package mbenson.snapin.example.param;

public interface Parameterized<T, R> {

    R apply(T t);
}
