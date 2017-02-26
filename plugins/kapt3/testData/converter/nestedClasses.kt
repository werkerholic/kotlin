class Test {
    class Nested {
        class NestedNested
    }

    inner class Inner

    object NestedObject

    interface NestedInterface

    enum class NestedEnum {
        BLACK, WHITE
    }
}

class Foo {
    companion object Foo
}

class A {
    interface B {
        class A {
            object B
        }
    }

    object C {
        interface C
    }
}

class A2 {
    class B {
        class C {
            class D {
                class A2
                class B
                class Cme
                class D
                class E
            }
        }
    }
}