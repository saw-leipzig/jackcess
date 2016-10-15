/*
Copyright (c) 2016 James Ahlborn

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.healthmarketscience.jackcess.util;

import junit.framework.TestCase;

/**
 *
 * @author James Ahlborn
 */
public class ExpressionatorTest extends TestCase 
{

  public ExpressionatorTest(String name) {
    super(name);
  }

  public void testOrderOfOperations() throws Exception
  {
    Expressionator.Expr expr = Expressionator.parse(
        Expressionator.Type.FIELD_VALIDATOR, "\"A\" Eqv \"B\"", null);
    assertEquals("<ELogicalOp>{<ELiteralValue>{\"A\"} Eqv <ELiteralValue>{\"B\"}}",
                 expr.toDebugString());

    expr = Expressionator.parse(
        Expressionator.Type.FIELD_VALIDATOR, "\"A\" Eqv \"B\" Xor \"C\"", null);
    assertEquals("<ELogicalOp>{<ELiteralValue>{\"A\"} Eqv <ELogicalOp>{<ELiteralValue>{\"B\"} Xor <ELiteralValue>{\"C\"}}}",
                 expr.toDebugString());

    expr = Expressionator.parse(
        Expressionator.Type.FIELD_VALIDATOR, "\"A\" Eqv \"B\" Xor \"C\" Or \"D\"", null);
    assertEquals("<ELogicalOp>{<ELiteralValue>{\"A\"} Eqv <ELogicalOp>{<ELiteralValue>{\"B\"} Xor <ELogicalOp>{<ELiteralValue>{\"C\"} Or <ELiteralValue>{\"D\"}}}}",
                 expr.toDebugString());

    expr = Expressionator.parse(
        Expressionator.Type.FIELD_VALIDATOR, "\"A\" Eqv \"B\" Xor \"C\" Or \"D\" And \"E\"", null);
    assertEquals("<ELogicalOp>{<ELiteralValue>{\"A\"} Eqv <ELogicalOp>{<ELiteralValue>{\"B\"} Xor <ELogicalOp>{<ELiteralValue>{\"C\"} Or <ELogicalOp>{<ELiteralValue>{\"D\"} And <ELiteralValue>{\"E\"}}}}}",
                 expr.toDebugString());
  }
}