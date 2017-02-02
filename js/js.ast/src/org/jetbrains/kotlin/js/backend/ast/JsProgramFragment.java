// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package org.jetbrains.kotlin.js.backend.ast;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class JsProgramFragment extends SourceInfoAwareJsNode {
    private final JsScope scope;
    private final List<JsImportedModule> importedModules = new ArrayList<JsImportedModule>();
    private final Map<JsFqName, JsExpression> imports = new LinkedHashMap<JsFqName, JsExpression>();
    private final JsGlobalBlock declarationBlock = new JsGlobalBlock();
    private final JsGlobalBlock exportBlock = new JsGlobalBlock();
    private final JsGlobalBlock initializerBlock = new JsGlobalBlock();
    private final List<JsNameBinding> nameBindings = new ArrayList<JsNameBinding>();
    private final Map<JsName, JsName> parentClasses = new LinkedHashMap<JsName, JsName>();
    private final Set<JsName> declaredClasses = new LinkedHashSet<JsName>();

    public JsProgramFragment(@NotNull JsScope scope) {
        this.scope = scope;
    }

    @NotNull
    public JsScope getScope() {
        return scope;
    }

    @NotNull
    public List<JsImportedModule> getImportedModules() {
        return importedModules;
    }

    @NotNull
    public Map<JsFqName, JsExpression> getImports() {
        return imports;
    }

    @NotNull
    public JsBlock getDeclarationBlock() {
        return declarationBlock;
    }

    @NotNull
    public JsGlobalBlock getExportBlock() {
        return exportBlock;
    }

    @NotNull
    public JsGlobalBlock getInitializerBlock() {
        return initializerBlock;
    }

    @NotNull
    public List<JsNameBinding> getNameBindings() {
        return nameBindings;
    }

    @NotNull
    public Map<JsName, JsName> getParentClasses() {
        return parentClasses;
    }

    @Override
    public void accept(JsVisitor v) {
        v.visitProgramFragment(this);
    }

    @Override
    public void acceptChildren(JsVisitor visitor) {
        visitor.accept(declarationBlock);
    }

    @Override
    public void traverse(JsVisitorWithContext v, JsContext ctx) {
        if (v.visit(this, ctx)) {
            v.acceptStatement(declarationBlock);
            v.acceptStatement(initializerBlock);
            v.acceptStatement(exportBlock);
        }
        v.endVisit(this, ctx);
    }

    @NotNull
    @Override
    public JsProgramFragment deepCopy() {
        throw new UnsupportedOperationException();
    }
}
