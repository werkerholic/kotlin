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

package org.jetbrains.kotlin.js.translate.general

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.js.inline.clean.resolveTemporaryNames
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils

class Merger(private val rootFunction: JsFunction, val module: ModuleDescriptor) {
    private val nameTable = mutableMapOf<JsFqName, JsName>()
    private val importBlock = JsGlobalBlock()
    private val declarationBlock = JsGlobalBlock()
    private val initializerBlock = JsGlobalBlock()
    private val exportBlock = JsGlobalBlock()
    private val declaredImports = mutableSetOf<JsFqName>()
    private val parentClasses = mutableMapOf<JsName, JsName>()

    fun addFragment(fragment: JsProgramFragment) {
        val nameMap = buildNameMap(fragment)
        nameMap.rename(fragment)

        for ((key, importExpr) in fragment.imports) {
            if (declaredImports.add(key)) {
                val name = nameTable[key]!!
                importBlock.statements += JsAstUtils.newVar(nameMap.rename(name), importExpr)
            }
        }

        declarationBlock.statements += fragment.declarationBlock
        initializerBlock.statements += fragment.initializerBlock
        exportBlock.statements += fragment.exportBlock
        parentClasses += fragment.parentClasses.map { (cls, parent) -> nameMap.rename(cls) to nameMap.rename(parent) }
    }

    private fun Map<JsName, JsName>.rename(name: JsName): JsName = getOrElse(name) { name }

    private fun buildNameMap(fragment: JsProgramFragment): Map<JsName, JsName> {
        val nameMap = mutableMapOf<JsName, JsName>()
        for (nameBinding in fragment.nameBindings) {
            nameMap[nameBinding.name] = nameTable.getOrPut(nameBinding.key) {
                rootFunction.scope.declareTemporaryName(nameBinding.name.ident)
            }
        }
        return nameMap
    }

    private fun Map<JsName, JsName>.rename(fragment: JsProgramFragment) {
        rename(fragment.declarationBlock)
        rename(fragment.exportBlock)
        rename(fragment.initializerBlock)
    }

    private fun Map<JsName, JsName>.rename(statement: JsStatement) {
        statement.accept(object : RecursiveJsVisitor() {
            override fun visitElement(node: JsNode) {
                super.visitElement(node)
                if (node is HasName) {
                    node.name = node.name?.let { name -> rename(name) }
                }
            }
        })
    }

    fun merge() {
        rootFunction.body.statements.apply {
            this += importBlock.statements
            addClassPrototypes(this)
            this += declarationBlock.statements
            this += initializerBlock.statements
        }
        rootFunction.body.resolveTemporaryNames()
    }

    private fun addClassPrototypes(statements: MutableList<JsStatement>) {
        val visited = mutableSetOf<JsName>()
        for (cls in parentClasses.keys) {
            addClassPrototypes(cls, visited, statements)
        }
    }

    private fun addClassPrototypes(
            cls: JsName,
            visited: MutableSet<JsName>,
            statements: MutableList<JsStatement>
    ) {
        if (!visited.add(cls)) return
        val superclass = parentClasses[cls] ?: return

        addClassPrototypes(superclass, visited, statements)

        val superclassRef = superclass.makeRef()
        val superPrototype = JsAstUtils.prototypeOf(superclassRef)
        val superPrototypeInstance = JsInvocation(JsNameRef("create", "Object"), superPrototype)

        val classRef = cls.makeRef()
        val prototype = JsAstUtils.prototypeOf(classRef)
        statements += JsAstUtils.assignment(prototype, superPrototypeInstance).makeStmt()

        val constructorRef = JsNameRef("constructor", prototype.deepCopy())
        statements += JsAstUtils.assignment(constructorRef, classRef.deepCopy()).makeStmt()
    }
}