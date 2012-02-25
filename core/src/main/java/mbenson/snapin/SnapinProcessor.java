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

import static com.sun.codemodel.JExpr._new;
import static com.sun.codemodel.JExpr._null;
import static com.sun.codemodel.JOp.eq;

import java.util.ArrayList;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import mbenson.annotationprocessing.CodeModelProcessorBase;
import mbenson.annotationprocessing.util.CodeModel;
import mbenson.annotationprocessing.util.LangModel;
import mbenson.snapin.Snapin.Doc;

import org.apache.commons.lang3.StringUtils;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JTryBlock;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

/**
 * Snapin processor.
 */
@SupportedAnnotationTypes("mbenson.snapin.Snapin")
@SupportedSourceVersion(SourceVersion.RELEASE_5)
public class SnapinProcessor extends CodeModelProcessorBase {
    private TypeElement templateInterface;

    /**
     * Initialize the processor.
     * 
     * @param processingEnv
     *            environment
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
            for (Element element : roundEnv.getElementsAnnotatedWith(Snapin.class)) {
                if (element.getKind() != ElementKind.CLASS) {
                    continue;
                }
                final TypeElement type = (TypeElement) element;
                Worker worker = new Worker(type, codeModel);
                worker.process();
            }
        }
        return true;
    }

    private class Worker extends CodeModelProcess<TypeElement> {
        private static final String DELEGATE_FIELD_NAME = "delegate";

        final Snapin annotation;
        final DeclaredType snapinType;
        private JClass delegateType;

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

            validate(
                LangModel
                    .filterByModifier(ElementFilter.methodsIn(element.getEnclosedElements()), Modifier.ABSTRACT,
                        Modifier.PROTECTED).iterator().hasNext(), "found no template methods in %s", element);

            TypeMirror _snapinType;
            _snapinType = getSnapinType(element);
            validate(_snapinType != null, "Unable to discover snapin type for %s", element);
            validate(_snapinType.getKind() == TypeKind.DECLARED, "Unexpected snapin type %s", _snapinType);
            snapinType = (DeclaredType) _snapinType;
            validate(snapinType.asElement().getKind() == ElementKind.INTERFACE, "Non-interface snapin type %s found",
                snapinType);
            delegateType = naiveType(snapinType.toString());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void processImpl() {
            final JDefinedClass snapin;
            String pkg = elements().getPackageOf(element).toString();
            String simple = annotation.value();
            try {
                snapin = codeModel._package(pkg)._class(JMod.PUBLIC | JMod.ABSTRACT, simple);
            } catch (Exception e) {
                error(e, "Unable to define class %s.%s:", pkg, simple);
                return;
            }
            LangModel.to(codeModel).copyTo(element.getTypeParameters(), snapin);

            // apply class-level commentary:
            CodeModel.addTo(snapin.javadoc(), annotation.doc().value());

            snapin.field(JMod.PRIVATE, delegateType, DELEGATE_FIELD_NAME);

            for (ExecutableElement templateMethod : LangModel.filterByModifier(
                ElementFilter.methodsIn(element.getEnclosedElements()), Modifier.ABSTRACT, Modifier.PROTECTED)) {
                addTo(snapin, templateMethod);
            }

            implementSnapin(snapin);
        }

        private void addTo(JDefinedClass snapin, ExecutableElement templateMethod) {
            final JFieldRef delegateField = JExpr.ref(JExpr._this(), DELEGATE_FIELD_NAME);

            JType rt = naiveType(templateMethod.getReturnType().toString());
            // copy template method:
            JMethod template =
                snapin.method(JMod.PROTECTED | JMod.ABSTRACT, rt, templateMethod.getSimpleName().toString());
            CodeModel.addTo(template.javadoc(), Doc.Optional.valueOf(templateMethod.getAnnotation(Doc.class)));

            // create snapin wrapper method:
            JMethod wrapper = snapin.method(JMod.FINAL, rt, templateMethod.getSimpleName().toString());
            ArrayList<String> paramTypes = new ArrayList<String>();
            for (VariableElement p : templateMethod.getParameters()) {
                paramTypes.add(p.asType().toString());
            }
            wrapper.javadoc().add(
                String.format("Call {@link #%s(%s)} using {@code delegate}", templateMethod.getSimpleName(),
                    StringUtils.join(paramTypes, ',')));

            LangModel.to(codeModel).copyTo(templateMethod.getTypeParameters(), template, wrapper);

            // add delegate param to wrapper method:
            JVar delegateParam = wrapper.param(delegateType, DELEGATE_FIELD_NAME);
            wrapper.javadoc().addParam(delegateParam).add(delegateType);

            for (TypeMirror e : templateMethod.getThrownTypes()) {
                JClass exType = naiveType(e.toString());
                template._throws(exType);
                template.javadoc().addThrows(exType);
                wrapper._throws(exType);
                wrapper.javadoc().addThrows(exType);
            }

            // define wrapper method body:
            JBlock block = wrapper.body();

            // if delegateParam == null throw new NPE:
            block._if(eq(delegateParam, _null()))._then()._throw(_new(codeModel._ref(NullPointerException.class)));

            // enter synchronized block:
            block.directStatement("synchronized (this)");
            block = CodeModel.addTo(block, new JBlock(true, true));

            // next, assign:
            block = block.assign(delegateField, delegateParam);

            // try to defer to original
            JTryBlock tryBlock = block._try();
            block = tryBlock.body();
            JInvocation invocation = JExpr.invoke(template);

            // handle params, sending from wrapper to original
            for (VariableElement p : templateMethod.getParameters()) {
                int mods = LangModel.to(codeModel).translateModifiers(p.getModifiers());
                JType t = naiveType(p.asType().toString());

                // add parameter, add javadoc, and add any available commentary:
                CodeModel.addTo(template.javadoc().addParam(template.param(mods, t, p.getSimpleName().toString())),
                    Doc.Optional.valueOf(p.getAnnotation(Doc.class)));
                JVar param = wrapper.param(mods, t, p.getSimpleName().toString());
                invocation.arg(param);
                CodeModel.addTo(wrapper.javadoc().addParam(param), Doc.Optional.valueOf(p.getAnnotation(Doc.class)));
            }

            if (codeModel.VOID.equals(rt)) {
                block.add(invocation);
            } else {
                block._return(invocation);
            }
            // clear delegate field in finally block:
            tryBlock._finally().assign(delegateField, JExpr._null());
        }

        private void implementSnapin(JDefinedClass snapin) {
            final JFieldVar delegateField = snapin.fields().get(DELEGATE_FIELD_NAME);
            for (ExecutableElement method : ElementFilter.methodsIn(snapinType.asElement().getEnclosedElements())) {
                String name = method.getSimpleName().toString();
                JType rt = naiveType(method.getReturnType().toString());
                JMethod impl = snapin.method(JMod.PUBLIC | JMod.SYNCHRONIZED | JMod.FINAL, rt, name);
                // implement body:
                // if delegate field == null throw new IllegalStateException:
                impl.body()._if(eq(delegateField, _null()))._then()
                    ._throw(_new(codeModel._ref(IllegalStateException.class)));

                JInvocation invocation = impl.body().invoke((JExpression) delegateField, name);
                ArrayList<String> paramTypes = new ArrayList<String>();
                for (VariableElement p : method.getParameters()) {
                    int mods = LangModel.to(codeModel).translateModifiers(p.getModifiers());
                    JType t = naiveType(p.asType().toString());
                    JVar param = impl.param(mods, t, p.getSimpleName().toString());
                    invocation.arg(param);
                    impl.javadoc().addParam(param);
                    paramTypes.add(t.name());
                }
                impl.javadoc().add(
                    String.format("@see %s#%s(%s)", snapinType.asElement().getSimpleName(), name,
                        StringUtils.join(paramTypes, ',')));
            }
        }

    }

    private TypeMirror getSnapinType(TypeElement templateType) {
        for (TypeMirror iface : templateType.getInterfaces()) {
            if (iface.getKind() == TypeKind.DECLARED) {
                DeclaredType declared = (DeclaredType) iface;
                TypeElement rawType = (TypeElement) declared.asElement();
                if (rawType.getQualifiedName().equals(templateInterface.getQualifiedName())) {
                    return declared.getTypeArguments().iterator().next();
                }
            }
        }
        return null;
    }

}
