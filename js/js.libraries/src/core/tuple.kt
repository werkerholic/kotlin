/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlin.js

public external interface JsTuple1<out T1>

public external interface JsTuple2<out T1, out T2> : JsTuple1<T1>

public external interface JsTuple3<out T1, out T2, out T3> : JsTuple2<T1, T2>

public external interface JsTuple4<out T1, out T2, out T3,out  T4> : JsTuple3<T1, T2, T3>

public external interface JsTuple5<out T1, out T2, out T3, out T4,out  T5> : JsTuple4<T1, T2, T3, T4>

public external interface JsTuple6<out T1, out T2, out T3, out T4, out T5, out T6> : JsTuple5<T1, T2, T3, T4, T5>

public inline operator fun <T1> JsTuple1<T1>.component1(): T1 = this.asDynamic()[0].unsafeCast<T1>()

public inline operator fun <T1, T2> JsTuple2<T1, T2>.component2(): T2 = this.asDynamic()[1].unsafeCast<T2>()

public inline operator fun <T1, T2, T3> JsTuple3<T1, T2, T3>.component3(): T3 = this.asDynamic()[2].unsafeCast<T3>()

public inline operator fun <T1, T2, T3, T4> JsTuple4<T1, T2, T3, T4>.component4(): T4 = this.asDynamic()[3].unsafeCast<T4>()

public inline operator fun <T1, T2, T3, T4, T5> JsTuple5<T1, T2, T3, T4, T5>.component5(): T5 =
        this.asDynamic()[4].unsafeCast<T5>()

public inline operator fun <T1, T2, T3, T4, T5, T6> JsTuple6<T1, T2, T3, T4, T5, T6>.component6(): T6 =
        this.asDynamic()[5].unsafeCast<T6>()

public inline fun <T1> JsTuple(v1: T1): JsTuple1<T1> = js("[v1]").unsafeCast<JsTuple1<T1>>()

public inline fun <T1, T2> JsTuple(v1: T1, v2: T2): JsTuple2<T1, T2> = js("[v1, v2]").unsafeCast<JsTuple2<T1, T2>>()

public inline fun <T1, T2, T3> JsTuple(v1: T1, v2: T2, v3: T3): JsTuple3<T1, T2, T3> =
        js("[v1, v2, v3]").unsafeCast<JsTuple3<T1, T2, T3>>()

public inline fun <T1, T2, T3, T4> JsTuple(v1: T1, v2: T2, v3: T3, v4: T4): JsTuple4<T1, T2, T3, T4> =
        js("[v1, v2, v3, v4]").unsafeCast<JsTuple4<T1, T2, T3, T4>>()

public inline fun <T1, T2, T3, T4, T5> JsTuple(v1: T1, v2: T2, v3: T3, v4: T4, v5: T5): JsTuple5<T1, T2, T3, T4, T5> =
        js("[v1, v2, v3, v4, v5]").unsafeCast<JsTuple5<T1, T2, T3, T4, T5>>()

public inline fun <T1, T2, T3, T4, T5, T6> JsTuple(
        v1: T1, v2: T2, v3: T3, v4: T4, v5: T5, v6: T6
): JsTuple6<T1, T2, T3, T4, T5, T6> {
    return js("[v1, v2, v3, v4, v5, v6]").unsafeCast<JsTuple6<T1, T2, T3, T4, T5, T6>>()
}
