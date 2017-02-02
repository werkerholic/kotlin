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

import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.js.inline.clean.resolveTemporaryNames
import org.jetbrains.kotlin.js.translate.declaration.InterfaceFunctionCopier
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils

class Merger(private val rootFunction: JsFunction) {
    private val nameTable = mutableMapOf<JsFqName, JsName>()
    private val importBlock = JsGlobalBlock()
    private val declarationBlock = JsGlobalBlock()
    private val initializerBlock = JsGlobalBlock()
    private val exportBlock = JsGlobalBlock()
    private val declaredImports = mutableSetOf<JsFqName>()

    fun addFragment(fragment: JsProgramFragment) {
        val nameMap = buildNameMap(fragment)
        rename(fragment, nameMap)

        for ((key, importExpr) in fragment.imports) {
            if (declaredImports.add(key)) {
                val name = nameTable[key]!!
                importBlock.statements += JsAstUtils.newVar(name, importExpr)
            }
        }

        declarationBlock.statements += fragment.declarationBlock
        initializerBlock.statements += fragment.initializerBlock
        exportBlock.statements += fragment.exportBlock
    }

    private fun buildNameMap(fragment: JsProgramFragment): Map<JsName, JsName> {
        val nameMap = mutableMapOf<JsName, JsName>()
        for (nameBinding in fragment.nameBindings) {
            nameMap[nameBinding.name] = nameTable.getOrPut(nameBinding.key) {
                rootFunction.scope.declareTemporaryName(nameBinding.name.ident)
            }
        }
        return nameMap
    }

    private fun rename(fragment: JsProgramFragment, nameMap: Map<JsName, JsName>) {
        rename(fragment.declarationBlock, nameMap)
        rename(fragment.exportBlock, nameMap)
        rename(fragment.initializerBlock, nameMap)
    }

    private fun rename(statement: JsStatement, nameMap: Map<JsName, JsName>) {
        statement.accept(object : RecursiveJsVisitor() {
            override fun visitElement(node: JsNode) {
                super.visitElement(node)
                if (node is HasName) {
                    node.name = nameMap.getOrElse(node.name) { node.name }
                }
            }
        })
    }

    fun merge() {
        rootFunction.body.statements.apply {
            this += importBlock.statements
            this += declarationBlock.statements
            this += initializerBlock.statements
        }
        rootFunction.body.resolveTemporaryNames()
    }
}