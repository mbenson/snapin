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
package mbenson.snapin.example;

public class ExampleUsage<T, BAR_ARG> {
    private final Example example;
    private final T fooArg;
    private final BAR_ARG barArg;
    private final String bazArg;

    public ExampleUsage(Example example, T fooArg, BAR_ARG barArg, String bazArg) {
        super();
        this.example = example;
        this.fooArg = fooArg;
        this.barArg = barArg;
        this.bazArg = bazArg;
    }

    public void foo(ExampleSnapin<T> snapin) {
        snapin.foo(example, fooArg);
    }

    public void bar(ExampleSnapin<T> snapin) throws Exception {
        snapin.<BAR_ARG> bar(example, barArg);
    }

    public <BAZ_TYPE> BAZ_TYPE baz(ExampleSnapin<T> snapin) {
        return snapin.<BAZ_TYPE> baz(example, bazArg);
    }
}
