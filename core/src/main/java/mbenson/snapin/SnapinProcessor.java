/*
 * Copyright the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mbenson.snapin;

import static com.helger.jcodemodel.JExpr._new;
import static com.helger.jcodemodel.JExpr._null;
import static com.helger.jcodemodel.JExpr._this;
import static com.helger.jcodemodel.JOp.eq;
import static com.helger.jcodemodel.JOp.ne;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;

import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.AbstractJType;
import com.helger.jcodemodel.IJExpression;
import com.helger.jcodemodel.JBlock;
import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JDefinedClass;
import com.helger.jcodemodel.JDocComment;
import com.helger.jcodemodel.JExpr;
import com.helger.jcodemodel.JFieldRef;
import com.helger.jcodemodel.JFieldVar;
import com.helger.jcodemodel.JInvocation;
import com.helger.jcodemodel.JMethod;
import com.helger.jcodemodel.JMod;
import com.helger.jcodemodel.JTryBlock;
import com.helger.jcodemodel.JTypeVar;
import com.helger.jcodemodel.JVar;
import com.helger.jcodemodel.meta.CodeModelBuildingException;
import com.helger.jcodemodel.meta.ErrorTypeFound;

import mbenson.annotationprocessing.CodeModelProcessorBase;
import mbenson.annotationprocessing.util.LangModel;
import mbenson.snapin.Snapin.Doc;
import mbenson.snapin.Snapin.DocThrow;

/**
 * {@link Snapin} annotation processor.
 */
@SupportedAnnotationTypes("mbenson.snapin.Snapin")
@SupportedSourceVersion(SourceVersion.RELEASE_5)
public class SnapinProcessor extends CodeModelProcessorBase {

    private static String doc(AnnotatedConstruct host) {
        final String[] value =
            Optional.ofNullable(host).map(h -> h.getAnnotation(Doc.class)).map(Doc::value).orElse(null);
        return ArrayUtils.isEmpty(value) ? null : Stream.of(value).collect(Collectors.joining("\n"));
    }

    private TypeElement templateInterface;

    /**
     * Initialize the processor.
     *
     * @param processingEnv environment
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        templateInterface = elements().getTypeElement(SnapinTemplate.class.getCanonicalName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean processTo(JCodeModel codeModel, Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
        throws Throwable {
        if (annotations != null) {
            roundEnv.getElementsAnnotatedWith(Snapin.class).stream().filter(e -> e.getKind() == ElementKind.CLASS)
                .map(e -> new Worker((TypeElement) e, codeModel)).forEach(Worker::process);
        }
        return true;
    }

    private class Worker extends CodeModelProcess<TypeElement> {

        private static final String TYPE_PARAMETER_FORMAT = "<%s>";
        private static final String DELEGATE_FIELD_NAME = "delegate";

        final Snapin annotation;
        final DeclaredType snapinType;
        final JDefinedClass snapin;
        final AbstractJClass delegateType;

        /**
         * Create a new Worker instance.
         *
         * @param element
         */
        Worker(TypeElement element, JCodeModel codeModel) {
            super(element, codeModel);
            validate(element.getSuperclass().getKind() != TypeKind.NONE, "%s should be a class", element);
            validate(element.getModifiers().contains(Modifier.ABSTRACT), "%s should be abstract", element);
            validate(types().isAssignable(types().erasure(element.asType()), templateInterface.asType()),
                "%s should implement SnapinTemplate", element);
            annotation = element.getAnnotation(Snapin.class);
            validate(annotation != null, "Cannot find Snapin annotation on %s; thus how did we even start processing?",
                element);

            validate(LangModel.filterByModifier(ElementFilter.methodsIn(element.getEnclosedElements()),
                Modifier.ABSTRACT, Modifier.PROTECTED).iterator().hasNext(), "found no template methods in %s",
                element);

            final TypeMirror _snapinType = getSnapinType(element);
            validate(_snapinType != null, "Unable to discover snapin type for %s", element);
            validate(_snapinType.getKind() == TypeKind.DECLARED, "Unexpected snapin type %s", _snapinType);
            snapinType = DeclaredType.class.cast(_snapinType);

            final String pkg = elements().getPackageOf(element).toString();
            final String simple = Validate.notBlank(annotation.value(), "Snapin basename was blank");

            try {
                snapin = codeModel._package(pkg)._class(JMod.PUBLIC | JMod.ABSTRACT, simple);
            } catch (Exception e) {
                error(e, "Unable to define class %s.%s:", pkg, simple);
                throw new IllegalStateException(e);
            }
            LangModel.to(codeModel).copyTo(element.getTypeParameters(), snapin);

            try {
                delegateType =
                    represent(snapinType, Collections.singletonMap(element.getQualifiedName().toString(), snapin));
            } catch (Exception e) {
                error(e, "Unable to get code model for %s", snapinType);
                throw new IllegalStateException(e);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void processImpl() {
            // apply class-level commentary:
            snapin.javadoc().add(doc(element));

            // apply type parameter Docs:
            element.getTypeParameters().forEach(tp -> {
                snapin.javadoc().addParam(String.format(TYPE_PARAMETER_FORMAT, tp.getSimpleName())).add(doc(tp));
            });

            snapin.field(JMod.PRIVATE, delegateType, DELEGATE_FIELD_NAME);

            for (ExecutableElement templateMethod : LangModel.filterByModifier(
                ElementFilter.methodsIn(element.getEnclosedElements()), Modifier.ABSTRACT, Modifier.PROTECTED)) {
                addTo(snapin, templateMethod);
            }

            implementSnapin(snapin);
        }

        private void addTo(JDefinedClass snapin, ExecutableElement templateMethod) {
            final JFieldRef delegateField = JExpr.ref(_this(), DELEGATE_FIELD_NAME);

            final AbstractJType rt = naiveType(templateMethod.getReturnType().toString());
            // copy template method:
            final JMethod template =
                snapin.method(JMod.PROTECTED | JMod.ABSTRACT, rt, templateMethod.getSimpleName().toString());
            template.javadoc().add(doc(templateMethod));
            if (codeModel.VOID != rt) {
                template.javadoc().addReturn().add(rt);
            }
            // create snapin wrapper method:
            final JMethod wrapper =
                snapin.method(JMod.FINAL | JMod.SYNCHRONIZED, rt, templateMethod.getSimpleName().toString());

            final List<? extends VariableElement> methodParameters = templateMethod.getParameters();
            final String paramTypes = methodParameters.stream().map(VariableElement::asType).map(Object::toString)
                .collect(Collectors.joining(","));

            wrapper.javadoc().append(String.format("Call {@link #%s(%s)} using {@code delegate}",
                templateMethod.getSimpleName(), paramTypes)).addReturn().add(rt);

            LangModel.to(codeModel).copyTo(templateMethod.getTypeParameters(), template, wrapper);

            // document method type parameters:
            templateMethod.getTypeParameters().forEach(tp -> Stream.of(template, wrapper).forEach(m -> {
                m.javadoc().addParam(String.format(TYPE_PARAMETER_FORMAT, tp.getSimpleName())).add(doc(tp));
            }));

            // add delegate param to wrapper method:
            final JVar delegateParam = wrapper.param(delegateType, DELEGATE_FIELD_NAME);
            wrapper.javadoc().addParam(delegateParam).append(delegateType).add("delegate");

            final Map<String, String[]> docThrows = Stream.of(templateMethod.getAnnotationsByType(DocThrow.class))
                .collect(Collectors.toMap(dt -> getClassName(dt, DocThrow::type), DocThrow::value));

            templateMethod.getThrownTypes().stream().map(Object::toString).<AbstractJClass> map(this::naiveType)
                .forEach(twn -> {
                    final String[] doc = docThrows.get(twn.fullName());
                    Stream.of(template, wrapper).peek(method -> method._throws(twn)).map(JMethod::javadoc)
                        .forEach(javadoc -> {
                            javadoc.addThrows(twn).add(doc);
                        });
                });

            // define wrapper method body:
            JBlock block = wrapper.body();

            // if delegateField != null throw new IllegalStateException:
            block._if(ne(delegateField, _null()))._then()
                ._throw(_new(codeModel._ref(IllegalStateException.class)).arg("Re-entry not permitted"));

            // if delegateParam == null throw new NPE:
            block._if(eq(delegateParam, _null()))._then()._throw(_new(codeModel._ref(NullPointerException.class)));

            // enter synchronized block:
            block = block.synchronizedBlock(_this()).body();

            // next, assign:
            block = block.assign(delegateField, delegateParam);

            // try to defer to original
            final JTryBlock tryBlock = block._try();
            block = tryBlock.body();
            final JInvocation invocation = JExpr.invoke(template);

            int index = 0;
            // handle params, sending from wrapper to original
            for (VariableElement p : methodParameters) {
                final boolean varParam = ++index == methodParameters.size() && templateMethod.isVarArgs();
                final int mods = LangModel.encodeModifiers(p.getModifiers());
                final AbstractJType t = naiveType(p.asType().toString());

                final String parameterDocs = doc(p);
                final String paramName = p.getSimpleName().toString();

                final JVar templateParam;
                if (varParam) {
                    templateParam = template.varParam(mods, t.elementType(), paramName);
                } else {
                    templateParam = template.param(mods, t, paramName);
                }
                template.javadoc().addParam(templateParam).add(parameterDocs);

                final JVar wrapperParam;
                if (varParam) {
                    wrapperParam = wrapper.varParam(mods, t.elementType(), paramName);
                } else {
                    wrapperParam = wrapper.param(mods, t, paramName);
                }
                wrapper.javadoc().addParam(wrapperParam).add(parameterDocs);
                invocation.arg(wrapperParam);
            }
            returnFrom(block, templateMethod.getReturnType(), invocation);

            // clear delegate field in finally block:
            tryBlock._finally().assign(delegateField, JExpr._null());
        }

        private void implementSnapin(JDefinedClass snapin) {
            final Element snapinTypeElement = snapinType.asElement();

            final boolean inheritance;
            if (snapinTypeElement.getKind().isInterface()) {
                snapin._implements(delegateType);
                inheritance = true;
            } else if (isExtensibleClass(snapinTypeElement)) {
                snapin._extends(delegateType);
                inheritance = true;
            } else {
                inheritance = false;
            }

            final Map<String, AbstractJClass> delegateTypeArguments = typeArguments(delegateType);

            final JFieldVar delegateField = snapin.fields().get(DELEGATE_FIELD_NAME);
            for (ExecutableElement method : ElementFilter.methodsIn(snapinTypeElement.getEnclosedElements())) {
                if (method.getModifiers().contains(Modifier.STATIC)) {
                    continue;
                }
                final String name = method.getSimpleName().toString();

                // the return type may be a mapped type variable, so defer until we can handle it
                final JMethod impl = snapin.method(JMod.PUBLIC | JMod.SYNCHRONIZED | JMod.FINAL, codeModel.NULL, name);

                final TypeVariableRenamer utv = new TypeVariableRenamer(codeModel,
                    snapin.typeParamList().stream().map(JTypeVar::name).collect(Collectors.toSet()));
                LangModel.to(codeModel).copyTo(method.getTypeParameters(), utv);

                final Map<String, AbstractJClass> methodTypeArguments = new HashMap<>(delegateTypeArguments);
                methodTypeArguments.putAll(utv.copyTo(impl));

                final AbstractJType returnType = resolveVariables(method.getReturnType(), methodTypeArguments);
                impl.type(returnType);
                if (codeModel.VOID != returnType) {
                    impl.javadoc().addReturn().add(returnType);
                }
                if (inheritance) {
                    impl.annotate(Override.class);
                }

                // implement body:
                // if delegate field == null throw new IllegalStateException:
                impl.body()._if(eq(delegateField, _null()))._then()
                    ._throw(_new(codeModel.ref(IllegalStateException.class)));

                final JInvocation invocation = JExpr.invoke(delegateField, name);

                final List<? extends VariableElement> methodParameters = method.getParameters();

                int index = 0;
                for (VariableElement p : methodParameters) {
                    final boolean varParam = ++index == methodParameters.size() && method.isVarArgs();
                    final int mods = LangModel.encodeModifiers(p.getModifiers());
                    final AbstractJType t = resolveVariables(p.asType(), methodTypeArguments);
                    final String paramName = p.getSimpleName().toString();

                    final JVar param;
                    if (varParam) {
                        param = impl.varParam(mods, t.elementType(), paramName);
                    } else {
                        param = impl.param(mods, t, paramName);
                    }
                    invocation.arg(param);
                    impl.javadoc().addParam(param).add("see interface");
                }

                returnFrom(impl.body(), method.getReturnType(), invocation);

                if (inheritance) {
                    impl.javadoc().add("{@inheritDoc}");
                }
                impl.javadoc().addTag(JDocComment.TAG_SEE)
                    .add(String.format("%s#%s(%s)", delegateType.erasure().name(), name,
                        impl.params().stream().map(JVar::type).map(this::seeParameter).map(AbstractJType::name)
                            .collect(Collectors.joining(", "))));

                method.getThrownTypes().stream().map(Object::toString).<AbstractJClass> map(this::naiveType)
                    .forEach(((Consumer<AbstractJClass>) impl::_throws).andThen(twn -> {
                        impl.javadoc().addThrows(twn).add("see interface");
                    }));
            }
        }

        private AbstractJType seeParameter(AbstractJType type) {
            if (type.isArray()) {
                return seeParameter(type.elementType()).array();
            }
            if (type instanceof JTypeVar) {
                return seeParameter(((JTypeVar) type)._extends());
            }
            return type.erasure();
        }

        private AbstractJClass represent(TypeMirror type, Map<String, AbstractJClass> variableMappingTypes)
            throws ErrorTypeFound, CodeModelBuildingException {
            switch (type.getKind()) {
            case DECLARED:
                final DeclaredType declaredType = DeclaredType.class.cast(type);

                AbstractJClass result =
                    codeModel.refWithErrorTypes(TypeElement.class.cast(declaredType.asElement()), elements());

                for (TypeMirror arg : declaredType.getTypeArguments()) {
                    result = result.narrow(represent(arg, variableMappingTypes));
                }
                return result;
            case TYPEVAR:
                final TypeVariable tv = TypeVariable.class.cast(type);

                final TypeParameterElement typeParameter = TypeParameterElement.class.cast(tv.asElement());
                final String genericElementName =
                    TypeElement.class.cast(typeParameter.getGenericElement()).getQualifiedName().toString();

                if (variableMappingTypes.containsKey(genericElementName)) {

                    final Optional<JTypeVar> var = Stream.of(variableMappingTypes.get(genericElementName).typeParams())
                        .filter(tp -> typeParameter.getSimpleName().contentEquals(tp.name())).findFirst();

                    if (var.isPresent()) {
                        return var.get();
                    }
                }
                break;
            default:
                break;
            }
            return naiveType(type.toString());
        }

        private boolean isExtensibleClass(Element snapinTypeElement) {
            if (snapinTypeElement.getKind() == ElementKind.CLASS) {
                if (!snapinTypeElement.getModifiers().contains(Modifier.FINAL)) {
                    return ElementFilter.constructorsIn(snapinTypeElement.getEnclosedElements()).stream()
                        .filter(this::isExtensibleCtor).findFirst().isPresent();
                }
            }
            return false;
        }

        private boolean isExtensibleCtor(ExecutableElement ctor) {
            if ((ctor.getKind() == ElementKind.CONSTRUCTOR) && ctor.getParameters().isEmpty()) {
                final Set<Modifier> mods = ctor.getModifiers();
                if (mods.contains(Modifier.PUBLIC) || mods.contains(Modifier.PROTECTED)) {
                    return true;
                }
                if (!mods.contains(Modifier.PRIVATE)) {
                    return elements().getPackageOf(ctor).equals(elements().getPackageOf(element));
                }
            }
            return false;
        }

        private void returnFrom(JBlock block, TypeMirror resultType, IJExpression expr) {
            if (resultType.getKind() == TypeKind.VOID) {
                block.add((JInvocation) expr);
                block._return();
            } else {
                block._return(expr);
            }
        }

        private Map<String, AbstractJClass> typeArguments(AbstractJClass type) {

            final JTypeVar[] typeParams = type.erasure().typeParams();
            final List<? extends AbstractJClass> typeArguments = type.getTypeParameters();

            final Map<String, AbstractJClass> result = new LinkedHashMap<>();
            for (int i = 0; (i < typeParams.length) && (i < typeArguments.size()); i++) {
                result.put(typeParams[i].name(), typeArguments.get(i));
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        private <T extends AbstractJType> T resolveVariables(TypeMirror type,
            Map<String, ? extends AbstractJClass> typeMappings) {
            switch (type.getKind()) {
            case TYPEVAR:
                return (T) typeMappings.get(TypeVariable.class.cast(type).asElement().getSimpleName().toString());

            case WILDCARD:
                final WildcardType wild = WildcardType.class.cast(type);
                final TypeMirror xBound = wild.getExtendsBound();

                if (xBound != null) {
                    return (T) this.<AbstractJClass> resolveVariables(xBound, typeMappings).wildcard();
                }
                final TypeMirror sBound = wild.getSuperBound();
                if (sBound != null) {
                    return (T) this.<AbstractJClass> resolveVariables(sBound, typeMappings).wildcardSuper();
                }
                return (T) codeModel.wildcard();

            case DECLARED:
                final DeclaredType declaredType = DeclaredType.class.cast(type);

                try {
                    AbstractJClass result =
                        codeModel.refWithErrorTypes(TypeElement.class.cast(declaredType.asElement()), elements());

                    for (TypeMirror arg : declaredType.getTypeArguments()) {
                        result = result.narrow(this.<AbstractJType> resolveVariables(arg, typeMappings));
                    }
                    return (T) result;
                } catch (CodeModelBuildingException ex) {
                    // fall through:
                }
            default:
                return naiveType(type.toString());
            }
        }
    }

    private TypeMirror getSnapinType(TypeElement templateType) {
        return templateType.getInterfaces().stream().filter(iface -> iface.getKind() == TypeKind.DECLARED)
            .map(DeclaredType.class::cast)
            .filter(declared -> templateInterface.getQualifiedName()
                .equals(((TypeElement) declared.asElement()).getQualifiedName()))
            .map(DeclaredType::getTypeArguments).flatMap(List::stream).findFirst().orElse(null);
    }

}
