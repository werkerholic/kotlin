/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.js.inline.clean

import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.js.backend.ast.metadata.coroutineMetadata

fun JsNode.resolveTemporaryNames() {
    val renamings = resolveNames()
    accept(object : RecursiveJsVisitor() {
        override fun visitElement(node: JsNode) {
            super.visitElement(node)
            if (node is HasName) {
                val name = node.name
                if (name != null) {
                    renamings[name]?.let { node.name = it }
                }
            }
        }

        override fun visitFunction(x: JsFunction) {
            x.coroutineMetadata?.apply {
                accept(suspendObjectRef)
                accept(baseClassRef)
            }
            super.visitFunction(x)
        }
    })
}

private fun JsNode.resolveNames(): Map<JsName, JsName> {
    val rootScope = computeScopes().liftNonTemporaryNames()
    val replacements = mutableMapOf<JsName, JsName>()
    val usedNames = mutableSetOf<String>()
    fun traverse(scope: Scope) {
        val newNames = scope.names.asSequence().filter { !it.isTemporary }.map { it.ident }.toMutableSet()
        usedNames += newNames
        for (temporaryName in scope.names.asSequence().filter { it.isTemporary }) {
            var resolvedName = temporaryName.ident
            var suffix = 0
            while (!usedNames.add(resolvedName)) {
                resolvedName = "${temporaryName.ident}_${suffix++}"
            }
            replacements[temporaryName] = JsDynamicScope.declareName(resolvedName).apply { copyMetadataFrom(temporaryName) }
            newNames += resolvedName
        }
        scope.children.forEach(::traverse)
        usedNames -= newNames
    }

    traverse(rootScope)

    accept(object : RecursiveJsVisitor() {
        var labels = mutableSetOf<String>()

        override fun visitLabel(x: JsLabel) {
            if (x.name.isTemporary) {
                var resolvedName = x.name.ident
                var suffix = 0
                while (!labels.add(resolvedName)) {
                    resolvedName = "${x.name.ident}_${suffix++}"
                }
                replacements[x.name] = JsDynamicScope.declareName(resolvedName)
            }
            super.visitLabel(x)
        }

        override fun visitFunction(x: JsFunction) {
            val oldLabels = labels
            labels = mutableSetOf<String>()
            super.visitFunction(x)
            labels = oldLabels
        }
    })

    return replacements
}

private fun Scope.liftNonTemporaryNames(): Scope {
    fun traverse(scope: Scope) {
        scope.children.forEach { child ->
            traverse(child)
            scope.names += child.names.filter { !it.isTemporary }
        }
    }
    traverse(this)
    return this
}

private fun JsNode.computeScopes(): Scope {
    val rootScope = Scope()
    accept(object : RecursiveJsVisitor() {
        var currentScope: Scope = rootScope

        override fun visitFunction(x: JsFunction) {
            x.name?.let { currentScope.names += it }
            val oldScope = currentScope
            currentScope = Scope().apply {
                parent = currentScope
                currentScope.children += this
            }
            currentScope.names += x.parameters.map { it.name }
            super.visitFunction(x)
            currentScope = oldScope
        }

        override fun visitCatch(x: JsCatch) {
            currentScope.names += x.parameter.name
            super.visitCatch(x)
        }

        override fun visit(x: JsVars.JsVar) {
            currentScope.names += x.name
            super.visit(x)
        }

        override fun visitNameRef(nameRef: JsNameRef) {
            if (nameRef.qualifier == null) {
                val name = nameRef.name
                if (name != null) {
                    if (!name.isTemporary) {
                        currentScope.names += name
                    }
                }
                else {
                    currentScope.names += JsDynamicScope.declareName(nameRef.ident)
                }
            }

            super.visitNameRef(nameRef)
        }
    })

    rootScope.names += JsFunctionScope.RESERVED_WORDS.map { JsDynamicScope.declareName(it) }

    return rootScope
}

private class Scope {
    var parent: Scope? = null
    val names = mutableSetOf<JsName>()
    val children = mutableSetOf<Scope>()
}