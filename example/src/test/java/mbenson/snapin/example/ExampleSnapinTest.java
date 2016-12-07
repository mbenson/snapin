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

import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExampleSnapinTest {
    @Mock
    private Example example;
    private ExampleSnapin<Integer> snapin;
    private ExampleUsage<Integer, Boolean> usage;
    private final Integer fooArg = Integer.valueOf(666);
    private final Boolean barArg = Boolean.TRUE;
    private final String[] bazArg = { "bazArg" };

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        snapin = new ExampleSnapin<Integer>() {

            @Override
            protected void foo(Integer t) {
                call("foo", t);

            }

            @Override
            protected <X> void bar(X x) throws Exception {
                call("bar", x);
            }

            @Override
            protected <Y> Y baz(String... s) {
                call("baz", s);
                return null;
            }

        };
        usage = new ExampleUsage<Integer, Boolean>(example, fooArg, barArg, bazArg);
    }

    @Test
    public void test() throws Exception {
        usage.foo(snapin);
        verify(example).call("foo", fooArg);
        usage.bar(snapin);
        verify(example).call("bar", barArg);
        usage.baz(snapin);
        verify(example).call("baz", bazArg);
    }

}
