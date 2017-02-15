// "Specify override for 'isEmpty' explicitly" "true"
// WITH_RUNTIME

import java.util.*

class <caret>B(f: MutableList<String>): ArrayList<String>(), MutableList<String> by f