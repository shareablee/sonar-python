/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.python.tree;

import com.sonar.sslr.api.AstNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.AnyParameter;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.HasSymbol;
import org.sonar.plugins.python.api.tree.IfStatement;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.PassStatement;
import org.sonar.plugins.python.api.tree.Statement;
import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.Tree.Kind;
import org.sonar.plugins.python.api.tree.WhileStatement;
import org.sonar.python.PythonTestUtils;
import org.sonar.python.api.PythonTokenType;
import org.sonar.python.parser.PythonParser;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeUtilsTest {

  @Test
  public void first_ancestor_of_kind() {
    String code = "" +
      "class A:\n" +
      "  def foo(): pass";
    FileInput root = parse(code);
    assertThat(TreeUtils.firstAncestorOfKind(root, Kind.CLASSDEF)).isNull();
    ClassDef classDef = (ClassDef) root.statements().statements().get(0);
    assertThat(TreeUtils.firstAncestorOfKind(classDef, Kind.FILE_INPUT, Kind.CLASSDEF)).isEqualTo(root);
    FunctionDef funcDef = (FunctionDef) classDef.body().statements().get(0);
    assertThat(TreeUtils.firstAncestorOfKind(funcDef, Kind.FILE_INPUT)).isEqualTo(root);
    assertThat(TreeUtils.firstAncestorOfKind(funcDef, Kind.CLASSDEF)).isEqualTo(classDef);

    code = "" +
      "while True:\n" +
      "  while True:\n" +
      "    pass";
    WhileStatement outerWhile = (WhileStatement) parse(code).statements().statements().get(0);
    WhileStatement innerWhile = (WhileStatement) outerWhile.body().statements().get(0);
    PassStatement passStatement = (PassStatement) innerWhile.body().statements().get(0);
    assertThat(TreeUtils.firstAncestorOfKind(passStatement, Kind.WHILE_STMT)).isEqualTo(innerWhile);
  }

  @Test
  public void first_ancestor() {
    String code = "" +
      "def outer():\n" +
      "  def inner():\n" +
      "    pass";
    FileInput root = parse(code);
    FunctionDef outerFunction = (FunctionDef) root.statements().statements().get(0);
    FunctionDef innerFunction = (FunctionDef) outerFunction.body().statements().get(0);
    Statement passStatement = innerFunction.body().statements().get(0);
    assertThat(TreeUtils.firstAncestor(passStatement, TreeUtilsTest::isOuterFunction)).isEqualTo(outerFunction);
  }

  @Test
  public void tokens() {
    // simple statement parsed so that we easily get all tokens from children or first token.
    FileInput parsed = parse("if foo:\n  pass");
    IfStatement ifStmt = (IfStatement) parsed.statements().statements().get(0);
    List<Token> collect = new ArrayList<>(ifStmt.children().stream().map(t -> t.is(Kind.TOKEN) ? (Token) t : t.firstToken()).collect(Collectors.toList()));
    collect.add(parsed.lastToken());
    assertThat(TreeUtils.tokens(parsed)).containsExactly(collect.toArray(new Token[0]));

    assertThat(TreeUtils.tokens(parsed.lastToken())).containsExactly(parsed.lastToken());

  }

  @Test
  public void non_whitespace_tokens() {
    FileInput parsed = parse("if foo:\n  pass");
    IfStatement ifStmt = (IfStatement) parsed.statements().statements().get(0);
    List<Token> nonWhitespaceTokens = TreeUtils.nonWhitespaceTokens(ifStmt);
    nonWhitespaceTokens.forEach(t -> assertThat(t.type()).isNotIn(PythonTokenType.NEWLINE, PythonTokenType.INDENT, PythonTokenType.DEDENT));
    assertThat(nonWhitespaceTokens).hasSize(4);
    assertThat(nonWhitespaceTokens.stream().map(Token::value)).containsExactly("if", "foo", ":", "pass");
  }

  @Test
  public void hasDescendants() {
    FileInput fileInput = parse("class A:\n  def foo(): pass");
    assertThat(TreeUtils.hasDescendant(fileInput, t -> t.is(Kind.PASS_STMT))).isTrue();
    assertThat(TreeUtils.hasDescendant(fileInput, t -> (t.is(Kind.NAME) && ((Name) t).name().equals("foo")))).isTrue();
    assertThat(TreeUtils.hasDescendant(fileInput, t -> (t.is(Kind.NAME) && ((Name) t).name().equals("bar")))).isFalse();
    assertThat(TreeUtils.hasDescendant(fileInput, t -> t.is(Kind.IF_STMT))).isFalse();
  }

  @Test
  public void getSymbolFromTree() {
    assertThat(TreeUtils.getSymbolFromTree(null)).isEmpty();

    Expression expression = PythonTestUtils.lastExpression(
            "x = 42",
            "x");
    assertThat(TreeUtils.getSymbolFromTree(expression)).contains(((HasSymbol) expression).symbol());

    expression = PythonTestUtils.lastExpression("foo()");
    assertThat(TreeUtils.getSymbolFromTree(expression)).isEmpty();
  }

  @Test
  public void getClassSymbolFromDef() {
    FileInput fileInput = PythonTestUtils.parse("class A:\n  def foo(): pass");
    ClassDef classDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.CLASSDEF));

    Symbol symbolA = classDef.name().symbol();
    assertThat(TreeUtils.getClassSymbolFromDef(classDef)).isEqualTo(symbolA);
    assertThat(TreeUtils.getClassSymbolFromDef(null)).isNull();

    fileInput = PythonTestUtils.parse(
      "class A:",
      "    pass",
      "A = 42"
    );
    classDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.CLASSDEF));
    assertThat(TreeUtils.getClassSymbolFromDef(classDef)).isNull();
  }

  @Test(expected = IllegalStateException.class)
  public void getClassSymbolFromDef_illegalSymbol() {
    FileInput fileInput = PythonTestUtils.parseWithoutSymbols("class A:\n  def foo(): pass");
    ClassDef classDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.CLASSDEF));

    TreeUtils.getClassSymbolFromDef(classDef);
  }

  @Test
  public void getFunctionSymbolFromDef() {
    FileInput fileInput = PythonTestUtils.parse("def foo(): pass");
    FunctionDef functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));

    Symbol symbolFoo = functionDef.name().symbol();
    assertThat(TreeUtils.getFunctionSymbolFromDef(functionDef)).isEqualTo(symbolFoo);
    assertThat(TreeUtils.getFunctionSymbolFromDef(null)).isNull();

    fileInput = PythonTestUtils.parse(
      "def foo():",
      "    pass",
      "foo = 42"
    );
    functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));
    assertThat(TreeUtils.getFunctionSymbolFromDef(functionDef)).isNull();
  }

  @Test(expected = IllegalStateException.class)
  public void getFunctionSymbolFromDef_illegalSymbol() {
    FileInput fileInput = PythonTestUtils.parseWithoutSymbols("def foo(): pass");
    FunctionDef functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));

    TreeUtils.getFunctionSymbolFromDef(functionDef);
  }

  @Test
  public void nonTupleParameters() {
    FileInput fileInput = PythonTestUtils.parse("def foo(): pass");
    FunctionDef functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));
    assertThat(TreeUtils.nonTupleParameters(functionDef)).isEmpty();

    fileInput = PythonTestUtils.parse("def foo(param1, param2): pass");
    functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));
    assertThat(TreeUtils.nonTupleParameters(functionDef)).isEqualTo(functionDef.parameters().nonTuple());
  }

  @Test
  public void positionalParameters() {
    FileInput fileInput = PythonTestUtils.parse("def foo(): pass");
    FunctionDef functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));
    assertThat(TreeUtils.positionalParameters(functionDef)).isEmpty();

    fileInput = PythonTestUtils.parse("def foo(param1, param2): pass");
    functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));
    assertThat(TreeUtils.positionalParameters(functionDef)).isEqualTo(functionDef.parameters().all());

    fileInput = PythonTestUtils.parse("def foo(param1, *param2): pass");
    functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));
    assertThat(TreeUtils.positionalParameters(functionDef)).isEqualTo(functionDef.parameters().all());

    fileInput = PythonTestUtils.parse("def foo(param1, param2, *, kw1, kw2): pass");
    functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));
    assertThat(TreeUtils.positionalParameters(functionDef)).isEqualTo(functionDef.parameters().all().subList(0, 2));

    fileInput = PythonTestUtils.parse("def foo((param1, param2), *, kw1, kw2): pass");
    functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));
    assertThat(TreeUtils.positionalParameters(functionDef)).isEmpty();

    fileInput = PythonTestUtils.parse("def foo(param1, /, param2, *, kw1, kw2): pass");
    functionDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF));
    List<AnyParameter> parameters = functionDef.parameters().all();
    assertThat(TreeUtils.positionalParameters(functionDef)).isEqualTo(
      Arrays.asList(parameters.get(0), parameters.get(2)
    ));
  }

  @Test
  public void topLevelFunctionDefs() {
    FileInput fileInput = PythonTestUtils.parse(
      "class A:",
      "    x = True",
      "    def foo(self): pass",
      "    if x:",
      "        def bar(self, x): return 1",
      "    else:",
      "        def baz(self, x, y): return x + y"
    );

    ClassDef classDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.CLASSDEF));
    List<FunctionDef> functionDefs = PythonTestUtils.getAllDescendant(fileInput, t -> t.is(Kind.FUNCDEF));

    assertThat(TreeUtils.topLevelFunctionDefs(classDef)).containsAll(functionDefs);

    fileInput = PythonTestUtils.parse(
      "class A:",
      "    x = True",
      "    def foo(self):",
      "        def foo2(x, y): return x + y",
      "        return foo2(1, 1)",
      "    class B:",
      "        def bar(self): pass"
    );
    classDef = PythonTestUtils.getFirstChild(fileInput, t -> t.is(Kind.CLASSDEF));
    FunctionDef fooDef = PythonTestUtils.getLastDescendant(fileInput, t -> t.is(Kind.FUNCDEF) && ((FunctionDef) t).name().name().equals("foo"));

    assertThat(TreeUtils.topLevelFunctionDefs(classDef)).isEqualTo(Collections.singletonList(fooDef));
  }

  private static boolean isOuterFunction(Tree tree) {
    return tree.is(Kind.FUNCDEF) && ((FunctionDef) tree).name().name().equals("outer");
  }

  private FileInput parse(String content) {
    PythonParser parser = PythonParser.create();
    AstNode astNode = parser.parse(content);
    return new PythonTreeMaker().fileInput(astNode);
  }
}
