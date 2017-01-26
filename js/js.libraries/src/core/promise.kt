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

public open external class Promise<out T>(executor: (resolve: (T) -> Unit, reject: (Throwable) -> Unit) -> Unit) {
    companion object {
        public fun <S> all(promise: Array<out Promise<out S>>): Promise<out Array<in S>>

        public fun <S> race(promise: Array<out Promise<out S>>): Promise<S>

        public fun reject(e: Throwable): Promise<Nothing>

        public fun <S> resolve(e: S): Promise<S>
    }

    public open fun <S> then(onFulfilled: ((T) -> S)?, onRejected: ((Throwable) -> S)? = definedExternally): Promise<S>

    public open fun <S> catch(onRejected: (Throwable) -> S): Promise<S>
}

public inline fun <T1, T2> Promise.Companion.all(v1: Promise<T1>, v2: Promise<T2>): Promise<JsTuple2<T1, T2>> {
    return all(arrayOf<Promise<Any?>>(v1, v2)).asDynamic().unsafeCast<Promise<JsTuple2<T1, T2>>>()
}

public inline fun <T1, T2, T3> Promise.Companion.all(v1: Promise<T1>, v2: Promise<T2>, v3: Promise<T3>): Promise<JsTuple3<T1, T2, T3>> {
    return all(arrayOf<Promise<Any?>>(v1, v2, v3)).asDynamic().unsafeCast<Promise<JsTuple3<T1, T2, T3>>>()
}

public inline fun <T1, T2, T3, T4> Promise.Companion.all(
        v1: Promise<T1>, v2: Promise<T2>, v3: Promise<T3>, v4: Promise<T4>
): Promise<JsTuple4<T1, T2, T3, T4>> {
    return all(arrayOf<Promise<Any?>>(v1, v2, v3, v4)).asDynamic().unsafeCast<Promise<JsTuple4<T1, T2, T3, T4>>>()
}

public inline fun <T1, T2, T3, T4, T5> Promise.Companion.all(
        v1: Promise<T1>, v2: Promise<T2>, v3: Promise<T3>, v4: Promise<T4>, v5: Promise<T5>
): Promise<JsTuple5<T1, T2, T3, T4, T5>> {
    return all(arrayOf<Promise<Any?>>(v1, v2, v3, v4, v5)).asDynamic().unsafeCast<Promise<JsTuple5<T1, T2, T3, T4, T5>>>()
}

public inline fun <T1, T2, T3, T4, T5, T6> Promise.Companion.all(
        v1: Promise<T1>, v2: Promise<T2>, v3: Promise<T3>, v4: Promise<T4>, v5: Promise<T5>, v6: Promise<T6>
): Promise<JsTuple6<T1, T2, T3, T4, T5, T6>> {
    return all(arrayOf<Promise<Any?>>(v1, v2, v3, v4, v5, v6)).asDynamic().unsafeCast<Promise<JsTuple6<T1, T2, T3, T4, T5, T6>>>()
}