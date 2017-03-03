package samples.collections

import samples.*
import kotlin.test.*
import kotlin.coroutines.experimental.buildIterator
import kotlin.coroutines.experimental.buildSequence

@RunWith(Enclosed::class)
class Sequences {
    class Building {

        @Sample
        fun generateSequence() {
            var count = 3

            val sequence = generateSequence {
                (count--).takeIf { it > 0 }
            }

            assertPrints(sequence.toList(), "[3, 2, 1]")

            // sequence.forEach {  }  // <- iterating that sequence second time will fail
        }

        @Sample
        fun generateSequenceWithSeed() {

            fun fibonacci(): Sequence<Int> {
                // fibonacci terms
                // 0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181, 6765, 10946, ...
                return generateSequence(Pair(0, 1), { Pair(it.second, it.first + it.second) }).map { it.first }
            }

            assertPrints(fibonacci().take(10).toList(), "[0, 1, 1, 2, 3, 5, 8, 13, 21, 34]")
        }

        @Sample
        fun generateSequenceWithLazySeed() {
            class LinkedValue<T>(val value: T, val next: LinkedValue<T>? = null)

            fun <T> LinkedValue<T>?.asSequence(): Sequence<LinkedValue<T>> = generateSequence(
                    seedFunction = { this },
                    nextFunction = { it.next }
            )

            fun <T> LinkedValue<T>?.valueSequence(): Sequence<T> = asSequence().map { it.value }

            val singleItem = LinkedValue(42)
            val twoItems = LinkedValue(24, singleItem)

            assertPrints(twoItems.valueSequence().toList(), "[24, 42]")
            assertPrints(singleItem.valueSequence().toList(), "[42]")
            assertPrints(singleItem.next.valueSequence().toList(), "[]")
        }

        @Sample
        fun sequenceOfValues() {
            val sequence = sequenceOf("first", "second", "last")
            sequence.forEach(::println)
        }

        @Sample
        fun buildFibonacciSequence() {
            fun fibonacci() = buildSequence {
                var terms = Pair(0, 1)

                // this sequence is infinite
                while(true) {
                    yield(terms.first)
                    terms = Pair(terms.second, terms.first + terms.second)
                }
            }

            assertPrints(fibonacci().take(10).toList(), "[0, 1, 1, 2, 3, 5, 8, 13, 21, 34]")
        }

        @Sample
        fun buildSequenceYieldAll() {
            val sequence = buildSequence {
                val start = 0
                // yielding a single value
                yield(start)
                // yielding an iterable
                yieldAll(1..5 step 2)
                // yielding an infinite sequence
                yieldAll(generateSequence(8) { it * 3 })
            }

            assertPrints(sequence.take(7).toList(), "[0, 1, 3, 5, 8, 24, 72]")
        }

        @Sample
        fun sequenceFromIterator() {
            val array = arrayOf(2, 3, 1)

            // create a sequence with function, returning as iterator
            val sequence = Sequence { array.iterator() }
            // same as
            val sequence2 = array.asSequence()
            // same as
            val sequence3 = Sequence {
                buildIterator {
                    yield(2)
                    yield(3)
                    yield(1)
                }
            }

            assertPrints(sequence.toList(), "[2, 3, 1]")
            assertPrints(sequence2.toList(), "[2, 3, 1]")
            assertPrints(sequence3.toList(), "[2, 3, 1]")

            // but not same as
            val sequence4 = array.iterator().asSequence()
            assertPrints(sequence4.toList(), "[2, 3, 1]")

            // because the latter can be iterated only once
            // sequence4.forEach {  }  // <- iterating that sequence second time will fail
        }

    }


}