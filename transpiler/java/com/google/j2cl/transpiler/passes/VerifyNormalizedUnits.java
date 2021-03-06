/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.transpiler.passes;

import static com.google.common.base.Preconditions.checkState;

import com.google.j2cl.transpiler.ast.AbstractVisitor;
import com.google.j2cl.transpiler.ast.BinaryExpression;
import com.google.j2cl.transpiler.ast.BreakStatement;
import com.google.j2cl.transpiler.ast.CompilationUnit;
import com.google.j2cl.transpiler.ast.ContinueStatement;
import com.google.j2cl.transpiler.ast.Field;
import com.google.j2cl.transpiler.ast.FieldAccess;
import com.google.j2cl.transpiler.ast.JavaScriptConstructorReference;
import com.google.j2cl.transpiler.ast.LabeledStatement;
import com.google.j2cl.transpiler.ast.LoopStatement;
import com.google.j2cl.transpiler.ast.Member;
import com.google.j2cl.transpiler.ast.MemberReference;
import com.google.j2cl.transpiler.ast.Method;
import com.google.j2cl.transpiler.ast.MethodCall;
import com.google.j2cl.transpiler.ast.MultiExpression;
import com.google.j2cl.transpiler.ast.Type;
import com.google.j2cl.transpiler.ast.UnaryExpression;

/** Verifies that the AST satisfies the normalization invariants. */
public class VerifyNormalizedUnits extends NormalizationPass {

  private final boolean verifyForWasm;

  public VerifyNormalizedUnits(boolean verifyForWasm) {
    this.verifyForWasm = verifyForWasm;
  }

  public VerifyNormalizedUnits() {
    this(false);
  }

  @Override
  public void applyTo(CompilationUnit compilationUnit) {
    compilationUnit.accept(
        new AbstractVisitor() {
          @Override
          public void exitType(Type type) {
            if (!verifyForWasm) {
              // TODO(b/180149762): Review normalizations related to native types.
              // Native and JsFunction types should have been removed from the AST.
              checkState(!type.isNative());
              checkState(!type.isJsFunctionInterface());
            }
          }

          @Override
          public void exitField(Field field) {
            if (!verifyForWasm) {
              checkState(!field.isNative());
            }
          }

          @Override
          public void exitMethod(Method method) {
            // All native methods should be empty.
            checkState(!method.isNative() || method.getBody().getStatements().isEmpty());
          }

          @Override
          public void exitMember(Member member) {
            // JsEnum only contains the enum fields.
            if (getCurrentType().isJsEnum()) {
              checkState(member.isField() || member.isStatic());
            }
          }

          @Override
          public void exitFieldAccess(FieldAccess fieldAccess) {
            verifyStaticMemberQualifiers(fieldAccess);
          }

          @Override
          public void exitMethodCall(MethodCall methodCall) {
            verifyStaticMemberQualifiers(methodCall);
            if (verifyForWasm) {
              checkState(!methodCall.isPolymorphic() || methodCall.getQualifier().isIdempotent());
            }
          }

          @Override
          public void exitBinaryExpression(BinaryExpression binaryExpression) {
            if (verifyForWasm) {
              checkState(!binaryExpression.getOperator().isCompoundAssignment());
            }
          }

          @Override
          public void exitUnaryExpression(UnaryExpression unaryExpression) {
            if (verifyForWasm) {
              // No increment nor decrement expressions are expected in the normalized tree.
              // All unary and binary operators that have side effect have been replaced by
              // explicit assignments.
              checkState(!unaryExpression.getOperator().hasSideEffect());
            }
          }

          @Override
          public void exitMultiExpression(MultiExpression multiExpression) {
            // No empty nor singleton multiexpressions should remain.
            checkState(multiExpression.getExpressions().size() > 1);
          }

          @Override
          public void exitLoopStatement(LoopStatement loopStatement) {
            if (verifyForWasm) {
              checkState(getParent() instanceof LabeledStatement);
            }
          }

          @Override
          public void exitBreakStatement(BreakStatement breakStatement) {
            if (verifyForWasm) {
              checkState(breakStatement.getLabelReference() != null);
            }
          }

          @Override
          public void exitContinueStatement(ContinueStatement continueStatement) {
            if (verifyForWasm) {
              checkState(continueStatement.getLabelReference() != null);
            }
          }
        });
  }

  private void verifyStaticMemberQualifiers(MemberReference memberReference) {
    checkState(
        !memberReference.getTarget().isStatic()
            || memberReference.getQualifier() instanceof JavaScriptConstructorReference);
  }
}
