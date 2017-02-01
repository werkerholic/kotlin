/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package kotlin.dom

import org.w3c.dom.*
import kotlin.collections.*

// DEPRECATED in 1.1-RC, drop after 1.1

/** Returns the children of the element as a list */
@JsName("children_nullable")
@Deprecated("Use children() function with safe call", ReplaceWith("this?.children().orEmpty()"), DeprecationLevel.ERROR)
fun Element?.children(): List<Node> {
    return this?.childNodes?.asList() ?: emptyList()
}

/** Returns the child elements of this element */
@JsName("childElements_nullable")
@Deprecated("Use childElements() function with safe call", ReplaceWith("this?.childElements().orEmpty()"), DeprecationLevel.ERROR)
fun Element?.childElements(): List<Element> = this?.childNodes?.filterElements() ?: emptyList()

/** Returns the child elements of this element with the given name. */
@JsName("childElements_byName_nullable")
@Deprecated("Use childElements() function with safe call", ReplaceWith("this?.childElements(name).orEmpty()"), DeprecationLevel.ERROR)
fun Element?.childElements(name: String): List<Element> = this?.childNodes?.filterElements()?.filter { it.nodeName == name } ?: emptyList()

/** Returns all the descendant elements given the local element name */
@JsName("deprecated_document_elements")
@Deprecated("Use elements function with safe call", ReplaceWith("this?.elements(localName).orEmpty()"), DeprecationLevel.ERROR)
fun Document?.elements(localName: String = "*"): List<Element> {
    return this?.elements(localName).orEmpty()
}

/** Returns all the descendant elements given the namespace URI and local element name */
@JsName("elements_nullable")
@Deprecated("Use elements() function with safe call", ReplaceWith("this?.elements(namespaceUri, localName).orEmpty()"), DeprecationLevel.ERROR)
fun Document?.elements(namespaceUri: String, localName: String): List<Element> {
    return this?.getElementsByTagNameNS(namespaceUri, localName)?.asElementList() ?: emptyList()
}

// END OF DEPRECATED



/** Returns the children of the element as a list. */
public fun Element.children(): List<Node> {
    return this.childNodes.asList()
}

/** Returns the child elements of this element. */
public fun Element.childElements(): List<Element> = this.childNodes.filterElements()

/** Returns the child elements of this element with the given name. */
public fun Element.childElements(name: String): List<Element> {
    @Suppress("UNCHECKED_CAST")
    return this.childNodes.asList().filter { it.isElement && it.nodeName == name } as List<Element>
}

/** Returns all the descendant elements given the local element name. */
public fun Element.elements(localName: String = "*"): List<Element> {
    return this.getElementsByTagName(localName).asElementList()
}

/** Returns all the descendant elements given the local element name. */
public fun Document.elements(localName: String = "*"): List<Element> {
    return this.getElementsByTagName(localName).asElementList()
}

/** Returns all the descendant elements given the namespace URI and local element name. */
public fun Element.elements(namespaceUri: String, localName: String): List<Element> {
    return this.getElementsByTagNameNS(namespaceUri, localName).asElementList()
}

/** Returns all the descendant elements given the namespace URI and local element name. */
public fun Document.elements(namespaceUri: String, localName: String): List<Element> {
    return this.getElementsByTagNameNS(namespaceUri, localName).asElementList()
}

/**
 * Returns a view of this [NodeList] as a list of nodes.
 */
public fun NodeList.asList(): List<Node> = NodeListAsList(this)

/**
 * Returns a view of this [NodeList] as a list of elements assuming that it contains only elements.
 *
 * An attempt to get an element with [List.get] indexed accessor of the returned list
 * will result in [ClassCastException] being thrown if that node is not an element.
 *
 * If you want to get a snapshot filtered to contain elements only it's better to use [filterElements] function.
 */
public fun NodeList.asElementList(): List<Element> = ElementListAsList(this)

/**
 * Returns a list containing only [Element] nodes.
 */
public fun List<Node>.filterElements(): List<Element> {
    @Suppress("UNCHECKED_CAST")
    return filter { it.isElement } as List<Element>
}

/**
 * Returns a list containing only [Element] nodes.
 */
public fun NodeList.filterElements(): List<Element> = asList().filterElements()


private class NodeListAsList(private val delegate: NodeList) : AbstractList<Node>() {
    override val size: Int get() = delegate.length

    override fun get(index: Int): Node = when {
        index in 0..size - 1 -> delegate.item(index)!!
        else -> throw IndexOutOfBoundsException("index $index is not in range [0 .. ${size - 1})")
    }
}

private class ElementListAsList(private val nodeList: NodeList) : AbstractList<Element>() {
    override fun get(index: Int): Element {
        val node = nodeList.item(index)
        if (node == null) {
            throw IndexOutOfBoundsException("NodeList does not contain a node at index: " + index)
        } else if (node.nodeType == Node.ELEMENT_NODE) {
            return node as Element
        } else {
            throw ClassCastException("Node is not an Element as expected but is $node")
        }
    }

    override val size: Int get() = nodeList.length
}

/** Returns an [Iterator] over the next siblings of this node. */
public fun Node.nextSiblings(): Iterable<Node> = NextSiblings(this)

private class NextSiblings(private var node: Node) : Iterable<Node> {
    override fun iterator(): Iterator<Node> = object : AbstractIterator<Node>() {
        override fun computeNext(): Unit {
            val nextValue = node.nextSibling
            if (nextValue != null) {
                setNext(nextValue)
                node = nextValue
            } else {
                done()
            }
        }
    }
}

/** Returns an [Iterator] over the next siblings of this node. */
public fun Node.previousSiblings(): Iterable<Node> = PreviousSiblings(this)

private class PreviousSiblings(private var node: Node) : Iterable<Node> {
    override fun iterator(): Iterator<Node> = object : AbstractIterator<Node>() {
        override fun computeNext(): Unit {
            val nextValue = node.previousSibling
            if (nextValue != null) {
                setNext(nextValue)
                node = nextValue
            } else {
                done()
            }
        }
    }
}

/**
 * Gets a value indicating whether this node is a TEXT_NODE or a CDATA_SECTION_NODE.
 */
public val Node.isText: Boolean
    get() = nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE


/**
 * Gets a value indicating whether this node is an [Element].
 */
public val Node.isElement: Boolean
    get() = nodeType == Node.ELEMENT_NODE
