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

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to trigger processing of a {@link SnapinTemplate} and define a snap-in. This is an abstract class defining
 * template methods that should be given the opportunity to make calls against a "snapped in" implementation of a given
 * interface as though the methods called belonged to the snap-in itself. A good way to think of this pattern is as a
 * structure for managing a temporary mixin.
 *
 * The hosting template should be {@code abstract}; it is further considered a best practice to define the template with
 * package-scope access.
 *
 * The generated snap-in class will be {@code public abstract} and will live in the same package as the hosting class.
 * It will clone any type parameters from the template, and implement the interface specified by {@link SnapinTemplate}
 * type parameter {@code T}, forwarding all calls to a {@code private} variable {@code snapin} of the interface type.
 *
 * For every {@code protected abstract} (template) method defined on the hosting class, a package-access
 * {@code final synchronized} method will be defined prepending an argument of the interface type which will invoke the
 * template method using the first argument as the snap-in. These methods may then be invoked as desired.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Snapin {

    /**
     * Defines a valid simple snap-in classname to be defined in the package where the template is found.
     *
     * @return String
     */
    String value();

    /**
     * Documentation.
     */
    @Target({ ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_PARAMETER })
    public @interface Doc {

        /**
         * Each element is a line.
         *
         * @return String[]
         */
        String[] value();
    }

    /**
     * {@link DocThrow} container annotation.
     */
    @Target(ElementType.METHOD)
    public @interface DocThrows {
        DocThrow[] value();
    }

    /**
     * Permits documentation of a thrown type.
     */
    @Target(ElementType.METHOD)
    @Repeatable(DocThrows.class)
    public @interface DocThrow {
        /**
         * The thrown type.
         *
         * @return {@link Throwable} subclass
         */
        Class<? extends Throwable> type();

        /**
         * Each element is a line.
         *
         * @return String[]
         */
        String[] value();
    }
}
