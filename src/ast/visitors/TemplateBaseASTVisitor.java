package ast.visitors;

import ast.template.TemplateNode;
import ast.template.TemplateRootNode;
import ast.template.css.*;
import ast.template.html.*;
import ast.template.jinja.blocks.*;
import ast.template.jinja.expressions.*;
import ast.template.jinja.expressions.literals.BooleanLiteralNode;
import ast.template.jinja.expressions.literals.NoneLiteralNode;
import ast.template.jinja.expressions.literals.NumberLiteralNode;
import ast.template.jinja.expressions.literals.StringLiteralNode;

/**
 * Default traversal for the template AST — the Jinja counterpart of
 * {@link ast.python.visitors.PythonBaseASTVisitor}.
 *
 * <p>Traversal is driven purely by {@code getChildren()}. Subclasses override
 * only the nodes they care about and call {@code super.visit(node)} to keep
 * walking.
 */
public abstract class TemplateBaseASTVisitor<T> implements TemplateASTVisitor<T> {

    protected T defaultResult() { return null; }

    protected T aggregateResult(T aggregate, T nextResult) { return nextResult; }

    protected T visitChild(TemplateNode node) {
        return node == null ? defaultResult() : node.accept(this);
    }

    protected T visitChildren(TemplateNode node) {
        T result = defaultResult();
        if (node == null) return result;
        for (TemplateNode child : node.getChildren()) {
            result = aggregateResult(result, visitChild(child));
        }
        return result;
    }

    @Override public T visit(TemplateRootNode node) { return visitChildren(node); }
    @Override public T visit(DoctypeNode node) { return visitChildren(node); }
    @Override public T visit(HTMLDocumentNode node) { return visitChildren(node); }
    @Override public T visit(HTMLNormalElementNode node) { return visitChildren(node); }
    @Override public T visit(HTMLSelfClosingElementNode node) { return visitChildren(node); }
    @Override public T visit(HTMLVoidElementNode node) { return visitChildren(node); }
    @Override public T visit(HTMLAttributeNode node) { return visitChildren(node); }
    @Override public T visit(HTMLTextNode node) { return visitChildren(node); }
    @Override public T visit(HTMLAttributeTextNode node) { return visitChildren(node); }
    @Override public T visit(CSSRuleNode node) { return visitChildren(node); }
    @Override public T visit(CSSSelectorNode node) { return visitChildren(node); }
    @Override public T visit(CSSDeclarationNode node) { return visitChildren(node); }
    @Override public T visit(CSSStringValueNode node) { return visitChildren(node); }
    @Override public T visit(CSSNumericValueNode node) { return visitChildren(node); }
    @Override public T visit(CSSKeywordValueNode node) { return visitChildren(node); }
    @Override public T visit(CSSColorValueNode node) { return visitChildren(node); }
    @Override public T visit(CSSAttributeNode node) { return visitChildren(node); }
    @Override public T visit(JinjaExpressionNode node) { return visitChildren(node); }
    @Override public T visit(VariableNode node) { return visitChildren(node); }
    @Override public T visit(StringLiteralNode node) { return visitChildren(node); }
    @Override public T visit(NumberLiteralNode node) { return visitChildren(node); }
    @Override public T visit(BooleanLiteralNode node) { return visitChildren(node); }
    @Override public T visit(NoneLiteralNode node) { return visitChildren(node); }
    @Override public T visit(UnaryExpressionNode node) { return visitChildren(node); }
    @Override public T visit(BinaryExpressionNode node) { return visitChildren(node); }
    @Override public T visit(CallExpressionNode node) { return visitChildren(node); }
    @Override public T visit(AttributeAccessNode node) { return visitChildren(node); }
    @Override public T visit(FilterExpressionNode node) { return visitChildren(node); }
    @Override public T visit(ListExpressionNode node) { return visitChildren(node); }
    @Override public T visit(DictExpressionNode node) { return visitChildren(node); }
    @Override public T visit(IfBlockNode node) { return visitChildren(node); }
    @Override public T visit(ElifBlockNode node) { return visitChildren(node); }
    @Override public T visit(ElseBlockNode node) { return visitChildren(node); }
    @Override public T visit(ForBlockNode node) { return visitChildren(node); }
    @Override public T visit(BlockBlockNode node) { return visitChildren(node); }
    @Override public T visit(SetBlockNode node) { return visitChildren(node); }
    @Override public T visit(IncludeBlockNode node) { return visitChildren(node); }
    @Override public T visit(ImportBlockNode node) { return visitChildren(node); }
    @Override public T visit(FromImportBlockNode node) { return visitChildren(node); }
    @Override public T visit(WithBlockNode node) { return visitChildren(node); }
    @Override public T visit(ExtendsBlockNode node) { return visitChildren(node); }
    @Override public T visit(GenericBlockNode node) { return visitChildren(node); }
}
