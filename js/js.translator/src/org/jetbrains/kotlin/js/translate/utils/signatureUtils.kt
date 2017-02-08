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

package org.jetbrains.kotlin.js.translate.utils

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.js.naming.encodeSignature
import org.jetbrains.kotlin.resolve.DescriptorUtils

fun generateSignature(descriptor: DeclarationDescriptor): String? {
    if (DescriptorUtils.isDescriptorWithLocalVisibility(descriptor)) return null
    if (descriptor is DeclarationDescriptorWithVisibility && descriptor.visibility == Visibilities.PRIVATE) return null
    return when (descriptor) {
        is CallableDescriptor -> {
            val parent = generateSignature(descriptor.containingDeclaration) ?: return null
            if (descriptor !is VariableAccessorDescriptor && descriptor !is ConstructorDescriptor && descriptor.name.isSpecial) {
                return null
            }
            val separator = if (descriptor is FunctionDescriptor) "#" else "!"
            parent + separator + escape(descriptor.name.asString()) + "|" + encodeSignature(descriptor)
        }
        is PackageFragmentDescriptor -> {
            if (descriptor.fqName.isRoot) "" else escape(descriptor.fqName.pathSegments().map { escape(it.identifier) }.joinToString("."))
        }
        is ClassDescriptor -> {
            val parent = generateSignature(descriptor.containingDeclaration) ?: return null
            if (descriptor.name.isSpecial) return null
            parent + "$" + escape(descriptor.name.asString())
        }
        else -> return null
    }
}

private fun escape(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
        val escapedChar = when (c) {
            '\\', '"', '.', '$', '#', '!', '<', '>', '|', '+', '-', ':', '*', '?' -> "\\$c"
            else -> c.toString()
        }
        sb.append(escapedChar)
    }
    return sb.toString()
}
