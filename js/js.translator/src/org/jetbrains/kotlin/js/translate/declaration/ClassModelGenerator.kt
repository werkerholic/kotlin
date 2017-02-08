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

package org.jetbrains.kotlin.js.translate.declaration

import org.jetbrains.kotlin.backend.common.CodegenUtil
import org.jetbrains.kotlin.backend.common.bridges.Bridge
import org.jetbrains.kotlin.backend.common.bridges.generateBridgesForFunctionDescriptor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.js.translate.context.StaticContext
import org.jetbrains.kotlin.js.translate.context.TranslationContext
import org.jetbrains.kotlin.js.translate.utils.AnnotationsUtils
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils.prototypeOf
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils.pureFqn
import org.jetbrains.kotlin.js.translate.utils.generateDelegateCall
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.utils.identity

class ClassModelGenerator(val context: StaticContext) {
    fun generateClassModel(descriptor: ClassDescriptor): JsClassModel {
        val superName = descriptor.getSuperClassNotAny()?.let { context.getInnerNameForDescriptor(it) }
        val model = JsClassModel(context.getInnerNameForDescriptor(descriptor), superName)
        if (descriptor.kind != ClassKind.INTERFACE && descriptor.kind != ClassKind.ANNOTATION_CLASS &&
            !AnnotationsUtils.isNativeObject(descriptor)
        ) {
            copyDefaultMembers(descriptor, model)
            generateBridgeMethods(descriptor, model)
        }
        return model
    }

    private fun copyDefaultMembers(descriptor: ClassDescriptor, model: JsClassModel) {
        val members = descriptor.unsubstitutedMemberScope
                .getContributedDescriptors(DescriptorKindFilter.FUNCTIONS)
                .mapNotNull { it as? CallableMemberDescriptor }

        for (member in members.filter { !it.kind.isReal && it.modality != Modality.ABSTRACT }) {
            val memberToCopy = findMemberToCopy(member) ?: continue
            val classToCopyFrom = memberToCopy.containingDeclaration as ClassDescriptor
            if (classToCopyFrom.kind != ClassKind.INTERFACE || AnnotationsUtils.isNativeObject(classToCopyFrom)) continue

            val name = context.getNameForDescriptor(member).ident
            when (member) {
                is FunctionDescriptor -> {
                    copyMethod(name, name, classToCopyFrom, descriptor, model.postDeclarationBlock)
                }
                is PropertyDescriptor -> copyProperty(name, classToCopyFrom, descriptor, model.postDeclarationBlock)
            }
        }
    }

    private fun findMemberToCopy(member: CallableMemberDescriptor): CallableMemberDescriptor? {
        val memberToCopy = member.overriddenDescriptors
                .filter { it.modality != Modality.ABSTRACT }
                .singleOrNull() ?: return null
        return if (!memberToCopy.kind.isReal) findMemberToCopy(memberToCopy) else memberToCopy
    }

    private fun generateBridgeMethods(descriptor: ClassDescriptor, model: JsClassModel) {
        generateBridgesToTraitImpl(descriptor, model)
        generateOtherBridges(descriptor, model)
    }

    private fun generateBridgesToTraitImpl(descriptor: ClassDescriptor, model: JsClassModel) {
        val translationContext = TranslationContext.rootContext(context)
        for ((key, value) in CodegenUtil.getNonPrivateTraitMethods(descriptor)) {
            val sourceName = context.getNameForDescriptor(key).ident
            val targetName = context.getNameForDescriptor(value).ident
            if (sourceName != targetName) {
                generateDelegateCall(descriptor, key, value, JsLiteral.THIS, translationContext) {
                    model.postDeclarationBlock.statements += it
                }
            }
        }
    }

    private fun generateOtherBridges(descriptor: ClassDescriptor, model: JsClassModel) {
        for (memberDescriptor in descriptor.defaultType.memberScope.getContributedDescriptors()) {
            if (memberDescriptor is FunctionDescriptor) {
                val bridgesToGenerate = generateBridgesForFunctionDescriptor(memberDescriptor, identity()) {
                    //There is no DefaultImpls in js backend so if method non-abstract it should be recognized as non-abstract on bridges calculation
                    false
                }

                for (bridge in bridgesToGenerate) {
                    generateBridge(descriptor, model, bridge)
                }
            }
        }
    }

    private fun generateBridge(descriptor: ClassDescriptor, model: JsClassModel, bridge: Bridge<FunctionDescriptor>) {
        val fromDescriptor = bridge.from
        val toDescriptor = bridge.to

        val sourceName = context.getNameForDescriptor(fromDescriptor).ident
        val targetName = context.getNameForDescriptor(toDescriptor).ident
        if (sourceName == targetName) return
        if (fromDescriptor.kind.isReal && fromDescriptor.modality != Modality.ABSTRACT && !toDescriptor.kind.isReal) return

        val translationContext = TranslationContext.rootContext(context)
        generateDelegateCall(descriptor, fromDescriptor, toDescriptor, JsLiteral.THIS, translationContext) {
            model.postDeclarationBlock.statements += it
        }
    }

    private fun copyMethod(
            sourceName: String,
            targetName: String,
            sourceDescriptor: ClassDescriptor,
            targetDescriptor: ClassDescriptor,
            block: JsBlock
    ) {
        if (targetDescriptor.module != context.currentModule) return

        val targetPrototype = prototypeOf(pureFqn(context.getInnerNameForDescriptor(targetDescriptor), null))
        val sourcePrototype = prototypeOf(pureFqn(context.getInnerNameForDescriptor(sourceDescriptor), null))
        val targetFunction = JsNameRef(targetName, targetPrototype)
        val sourceFunction = JsNameRef(sourceName, sourcePrototype)
        block.statements += JsAstUtils.assignment(targetFunction, sourceFunction).makeStmt()
    }

    private fun copyProperty(
            name: String,
            sourceDescriptor: ClassDescriptor,
            targetDescriptor: ClassDescriptor,
            block: JsBlock
    ) {
        if (targetDescriptor.module != context.currentModule) return

        val targetPrototype = prototypeOf(pureFqn(context.getInnerNameForDescriptor(targetDescriptor), null))
        val sourcePrototype = prototypeOf(pureFqn(context.getInnerNameForDescriptor(sourceDescriptor), null))
        val nameLiteral = context.program.getStringLiteral(name)

        val getPropertyDescriptor = JsInvocation(JsNameRef("getOwnPropertyDescriptor", "Object"), sourcePrototype, nameLiteral)
        val defineProperty = JsAstUtils.defineProperty(targetPrototype, name, getPropertyDescriptor, context.program)

        block.statements += defineProperty.makeStmt()
    }
}