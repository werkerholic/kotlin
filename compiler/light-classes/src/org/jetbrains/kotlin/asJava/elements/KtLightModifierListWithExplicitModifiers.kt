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

package org.jetbrains.kotlin.asJava.elements

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.util.ArrayUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.utils.indexOfFirst

abstract class KtLightModifierListWithExplicitModifiers(
        private val owner: KtLightElement<*, *>,
        modifiers: Array<String>
) : LightModifierList(owner.manager, KotlinLanguage.INSTANCE, *modifiers) {
    abstract val delegate: PsiAnnotationOwner

    private val _annotations = KtLightAnnotations(this) { delegate.annotations }

    override fun getParent() = owner

    override fun getAnnotations(): Array<out PsiAnnotation> = _annotations.annotations

    override fun getApplicableAnnotations() = delegate.applicableAnnotations

    override fun findAnnotation(@NonNls qualifiedName: String) = annotations.firstOrNull { it.qualifiedName == qualifiedName }

    override fun addAnnotation(@NonNls qualifiedName: String) = delegate.addAnnotation(qualifiedName)
}

class KtLightModifierList(
        private val owner: PsiModifierListOwner,
        private val computeDelegateAnnotations: () -> Array<PsiAnnotation>
) : LightElement(owner.manager, KotlinLanguage.INSTANCE), PsiModifierList {

    private val _annotations = KtLightAnnotations(this, computeDelegateAnnotations)

    override fun getAnnotations(): Array<out PsiAnnotation> = _annotations.annotations

    override fun findAnnotation(@NonNls qualifiedName: String) = annotations.firstOrNull { it.qualifiedName == qualifiedName }

    override fun addAnnotation(@NonNls qualifiedName: String) = throwCanNotModify()

    override fun getParent() = owner

    override fun getText(): String? = ""

    override fun getLanguage() = KotlinLanguage.INSTANCE
    override fun getTextRange() = TextRange.EMPTY_RANGE
    override fun getStartOffsetInParent() = -1
    override fun getPrevSibling(): PsiElement? = null
    override fun getNextSibling(): PsiElement? = null
    override fun findElementAt(offset: Int): PsiElement? = null
    override fun findReferenceAt(offset: Int): PsiReference? = null
    override fun getTextOffset() = -1
    override fun isWritable() = false
    override fun isPhysical() = false
    override fun textToCharArray(): CharArray = ArrayUtil.EMPTY_CHAR_ARRAY;
    override fun copy(): PsiElement = KtLightModifierList(owner, computeDelegateAnnotations)
    override fun getReferences() = PsiReference.EMPTY_ARRAY
    override fun isEquivalentTo(another: PsiElement?) =
            another is KtLightModifierList && owner == another.owner

    override fun hasModifierProperty(name: String) = owner.hasModifierProperty(name)

    override fun hasExplicitModifier(name: String) = hasModifierProperty(name)

    override fun setModifierProperty(name: String, value: Boolean) = throwCanNotModify()
    override fun checkSetModifierProperty(name: String, value: Boolean) = throwCanNotModify()
    override fun getApplicableAnnotations() = annotations

    override fun toString() = "${this::class} for $owner"
}

class KtLightAnnotations(
        private val parent: PsiModifierList,
        private val computeDelegateAnnotations: () -> Array<PsiAnnotation>
) {
    val annotations by lazyPub(this::computeAnnotations)

    private fun computeAnnotations(): Array<PsiAnnotation> {
        val delegateAnnotations = computeDelegateAnnotations()
        if (delegateAnnotations.isEmpty()) return emptyArray()

        val lightOwner = parent.parent as? KtLightElement<*, *>
        val declaration = lightOwner?.kotlinOrigin as? KtDeclaration
        if (declaration != null && !declaration.isValid) return PsiAnnotation.EMPTY_ARRAY
        val descriptor = declaration?.let { LightClassGenerationSupport.getInstance(parent.project).resolveToDescriptor(it) }
        val annotatedDescriptor = when {
            descriptor !is PropertyDescriptor || lightOwner !is KtLightMethod -> descriptor
            lightOwner.isGetter -> descriptor.getter
            lightOwner.isSetter -> descriptor.setter
            else -> descriptor
        }
        val ktAnnotations = annotatedDescriptor?.annotations?.getAllAnnotations() ?: emptyList()
        var nextIndex = 0
        val result = delegateAnnotations
                .map { clsAnnotation ->
                    val currentIndex = ktAnnotations.indexOfFirst(nextIndex) {
                        it.annotation.type.constructor.declarationDescriptor?.fqNameUnsafe?.asString() == clsAnnotation.qualifiedName
                    }
                    if (currentIndex >= 0) {
                        nextIndex = currentIndex + 1
                        val ktAnnotation = ktAnnotations[currentIndex]
                        val entry = ktAnnotation.annotation.source.getPsi() as? KtAnnotationEntry ?: return@map clsAnnotation
                        KtLightAnnotation(clsAnnotation, entry, parent)
                    }
                    else clsAnnotation
                }
                .toTypedArray()
        return result
    }

}
