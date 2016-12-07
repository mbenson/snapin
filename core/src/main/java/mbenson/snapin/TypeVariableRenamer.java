package mbenson.snapin;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.helger.jcodemodel.AbstractJGenerifiableImpl;
import com.helger.jcodemodel.IJGenerifiable;
import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JTypeVar;

public class TypeVariableRenamer extends AbstractJGenerifiableImpl {
    private final JCodeModel owner;
    private final Set<String> typeVariableNames;
    private final UnaryOperator<String> typeVariableRenamer;

    public TypeVariableRenamer(JCodeModel owner, Set<String> typeVariableNames) {
        this(owner, typeVariableNames, v -> "_" + v);
    }

    public TypeVariableRenamer(JCodeModel owner, Set<String> typeVariableNames,
        UnaryOperator<String> typeVariableRenamer) {
        super();
        this.owner = Objects.requireNonNull(owner, "owner");
        if (typeVariableNames == null) {
            this.typeVariableNames = Collections.emptySet();
        } else {
            this.typeVariableNames = Collections.unmodifiableSet(new HashSet<>(typeVariableNames));
        }
        this.typeVariableRenamer = Objects.requireNonNull(typeVariableRenamer, "typeVariableRenamer");
    }

    @Override
    public JCodeModel owner() {
        return owner;
    }

    public Map<String, JTypeVar> copyTo(IJGenerifiable target) {
        final Set<String> usedVariableNames = new HashSet<>(typeVariableNames);
        final Map<String, JTypeVar> result = new HashMap<>();
        for (JTypeVar var : typeParams()) {
            String name = var.name();
            while (usedVariableNames.contains(name)) {
                name = typeVariableRenamer.apply(name);
            }
            usedVariableNames.add(name);
            result.put(var.name(), target.generify(name).boundLike(var));
        }
        return result;
    }
}
