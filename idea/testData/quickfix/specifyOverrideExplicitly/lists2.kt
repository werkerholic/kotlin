// "Specify override for 'size' explicitly" "true"
// WITH_RUNTIME

import java.util.*

class <caret>B(private val f: MutableList<String>): ArrayList<String>(), MutableList<String> by f {
    override fun isEmpty(): kotlin.Boolean {
        return f.isEmpty()
    }
}