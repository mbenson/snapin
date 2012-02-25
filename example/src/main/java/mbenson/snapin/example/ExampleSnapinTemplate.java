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

import mbenson.snapin.Snapin;
import mbenson.snapin.Snapin.Doc;
import mbenson.snapin.SnapinTemplate;

@Snapin(value = "ExampleSnapin", doc = @Doc({ "This is an example snapin.", "It's great.",
    "@param <T> type of blah blah blah" }))
abstract class ExampleSnapinTemplate<T> implements SnapinTemplate<Example> {

    @Doc("foo")
    protected abstract void foo(@Doc("is pronounced 'tee'") T t);

    @Doc("bar")
    protected abstract <X> void bar(@Doc("is pronounced 'eks'") X x) throws Exception;

    @Doc("baz")
    protected abstract <Y> Y baz(@Doc("is pronounced 'ess'") String s);
}
