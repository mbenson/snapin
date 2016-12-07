package mbenson.snapin.example.external;

import java.util.function.Function;

import mbenson.snapin.Snapin;
import mbenson.snapin.Snapin.Doc;
import mbenson.snapin.SnapinTemplate;

@Snapin("FunctionSnapin")
@Doc("function snapin blah blah")
abstract class FunctionSnapinTemplate<@Doc("I maps to Function&lt;T&gt;") I, @Doc("O maps to Function&lt;R&gt;") O,
    @Doc("V is a decoy to show that we rename delegate method type parameters when they collide with snapin type parameters") V>
    implements SnapinTemplate<Function<I, O>> {

    @Doc("suggests a simple passthrough to Function.apply")
    protected abstract O blah(@Doc("input of type I") I input);
}
