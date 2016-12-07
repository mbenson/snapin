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
package mbenson.snapin.example.param;

import java.util.List;

import mbenson.snapin.Snapin;
import mbenson.snapin.Snapin.Doc;
import mbenson.snapin.SnapinTemplate;

@Snapin("ParameterizedSnapin")
@Doc("snapin for Parameterized")
abstract class ParameterizedSnapinTemplate<@Doc("P") P, @Doc("Q") Q>
    implements SnapinTemplate<Parameterized<P, List<Q>>> {

    @Doc("foo")
    protected abstract P foo(@Doc("p") P p);

    @Doc("bar")
    protected abstract P bar(@Doc("bar") P bar);

}
