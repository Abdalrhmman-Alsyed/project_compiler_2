package output;

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
import ast.visitors.TemplateBaseASTVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TemplateASTJson extends TemplateBaseASTVisitor<Map<String, Object>> {

    static Map<String, Object> dumpTree(TemplateNode ast) {
        return new TemplateASTJson().dump(ast);
    }

    private Map<String, Object> dump(TemplateNode n) {
        if (n == null) return null;
        Map<String, Object> m = n.accept(this);
        return m != null ? m : generic(n);
    }

    private Map<String, Object> base(TemplateNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", n.getClass().getSimpleName());
        if (n.getNodeType() != null) m.put("kind", n.getNodeType().name());
        m.put("line", n.getLine());
        m.put("column", n.getColumn());
        return m;
    }

    private List<Object> kids(TemplateNode n) {
        List<Object> out = new ArrayList<>();
        if (n == null || n.getChildren() == null) return out;
        for (TemplateNode c : n.getChildren()) {
            if (c != null) out.add(dump(c));
        }
        return out;
    }

    private Map<String, Object> generic(TemplateNode n) {
        Map<String, Object> m = base(n);
        if (n instanceof CSSStyleNode style) {
            m.put("rules", style.getRules().size());
        }
        if (n instanceof HTMLClosingTagNode close) {
            m.put("tag", close.getTagName());
        }
        m.put("children", kids(n));
        return m;
    }

    private Map<String, Object> withKids(TemplateNode n) {
        Map<String, Object> m = base(n);
        m.put("children", kids(n));
        return m;
    }

    @Override public Map<String, Object> visit(TemplateRootNode node) { return withKids(node); }

    @Override
    public Map<String, Object> visit(DoctypeNode node) {
        Map<String, Object> m = base(node);
        m.put("doctype", node.getDoctype());
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(HTMLDocumentNode node) {
        return withKids(node);
    }

    @Override
    public Map<String, Object> visit(HTMLNormalElementNode node) {
        Map<String, Object> m = base(node);
        m.put("tag", node.getTagName());
        m.put("closingTag", node.getClosingTagName());
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(HTMLSelfClosingElementNode node) {
        Map<String, Object> m = base(node);
        m.put("tag", node.getTagName());
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(HTMLVoidElementNode node) {
        Map<String, Object> m = base(node);
        m.put("tag", node.getTagName());
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(HTMLAttributeNode node) {
        Map<String, Object> m = base(node);
        m.put("name", node.getName());
        m.put("boolean", node.isBoolean());
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(HTMLTextNode node) {
        Map<String, Object> m = base(node);
        m.put("text", node.getText());
        return m;
    }

    @Override
    public Map<String, Object> visit(HTMLAttributeTextNode node) {
        Map<String, Object> m = base(node);
        m.put("text", node.getText());
        return m;
    }

    @Override
    public Map<String, Object> visit(CSSRuleNode node) {
        Map<String, Object> m = base(node);
        m.put("selector", node.getSelectorText());
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(CSSSelectorNode node) {
        Map<String, Object> m = base(node);
        m.put("selector", node.getSelector());
        return m;
    }

    @Override
    public Map<String, Object> visit(CSSDeclarationNode node) {
        Map<String, Object> m = base(node);
        m.put("property", node.getProperty());
        m.put("value", node.getValueText());
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(CSSStringValueNode node) {
        Map<String, Object> m = base(node);
        m.put("value", node.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(CSSNumericValueNode node) {
        Map<String, Object> m = base(node);
        m.put("value", node.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(CSSKeywordValueNode node) {
        Map<String, Object> m = base(node);
        m.put("value", node.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(CSSColorValueNode node) {
        Map<String, Object> m = base(node);
        m.put("value", node.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(CSSAttributeNode node) {
        Map<String, Object> m = base(node);
        m.put("name", node.getName());
        m.put("value", node.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(JinjaExpressionNode node) {
        Map<String, Object> m = base(node);
        m.put("expression", dump(node.getExpression()));
        return m;
    }

    @Override
    public Map<String, Object> visit(VariableNode node) {
        Map<String, Object> m = base(node);
        m.put("name", node.getName());
        m.put("path", node.getPath());
        return m;
    }

    @Override
    public Map<String, Object> visit(StringLiteralNode node) {
        Map<String, Object> m = base(node);
        m.put("value", node.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(NumberLiteralNode node) {
        Map<String, Object> m = base(node);
        m.put("value", node.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(BooleanLiteralNode node) {
        Map<String, Object> m = base(node);
        m.put("value", node.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(NoneLiteralNode node) {
        return base(node);
    }

    @Override
    public Map<String, Object> visit(UnaryExpressionNode node) {
        Map<String, Object> m = base(node);
        m.put("op", node.getOperator());
        m.put("operand", dump(node.getOperand()));
        return m;
    }

    @Override
    public Map<String, Object> visit(BinaryExpressionNode node) {
        Map<String, Object> m = base(node);
        m.put("op", node.getOperator());
        m.put("left", dump(node.getLeft()));
        m.put("right", dump(node.getRight()));
        return m;
    }

    @Override
    public Map<String, Object> visit(CallExpressionNode node) {
        Map<String, Object> m = base(node);
        m.put("callee", dump(node.getCallee()));
        m.put("arguments", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(AttributeAccessNode node) {
        Map<String, Object> m = base(node);
        m.put("object", dump(node.getObject()));
        m.put("attr", node.getAttribute());
        return m;
    }

    @Override
    public Map<String, Object> visit(FilterExpressionNode node) {
        Map<String, Object> m = base(node);
        m.put("filter", node.getFilterName());
        m.put("input", dump(node.getInput()));
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(ListExpressionNode node) {
        Map<String, Object> m = base(node);
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(DictExpressionNode node) {
        return withKids(node);
    }

    @Override
    public Map<String, Object> visit(IndexAccessNode node) {
        Map<String, Object> m = base(node);
        m.put("object", dump(node.getObject()));
        m.put("index", dump(node.getIndex()));
        return m;
    }

    @Override
    public Map<String, Object> visit(SliceNode node) {
        Map<String, Object> m = base(node);
        m.put("start", dump(node.getStart()));
        m.put("stop", dump(node.getStop()));
        m.put("step", dump(node.getStep()));
        return m;
    }

    @Override
    public Map<String, Object> visit(IfBlockNode node) {
        Map<String, Object> m = base(node);
        m.put("condition", dump(node.getCondition()));
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(ElifBlockNode node) {
        return withKids(node);
    }

    @Override
    public Map<String, Object> visit(ElseBlockNode node) {
        return withKids(node);
    }

    @Override
    public Map<String, Object> visit(ForBlockNode node) {
        Map<String, Object> m = base(node);
        m.put("variable", node.getVariable());
        m.put("iterable", dump(node.getIterable()));
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(BlockBlockNode node) {
        Map<String, Object> m = base(node);
        m.put("name", node.getBlockName());
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(SetBlockNode node) {
        Map<String, Object> m = base(node);
        m.put("variable", node.getVariable());
        m.put("expression", dump(node.getExpression()));
        return m;
    }

    @Override
    public Map<String, Object> visit(IncludeBlockNode node) {
        Map<String, Object> m = base(node);
        m.put("template", node.getTemplateName());
        return m;
    }

    @Override
    public Map<String, Object> visit(ImportBlockNode node) {
        Map<String, Object> m = base(node);
        m.put("template", node.getTemplateName());
        m.put("alias", node.getAlias());
        return m;
    }

    @Override
    public Map<String, Object> visit(FromImportBlockNode node) {
        Map<String, Object> m = base(node);
        m.put("template", node.getTemplateName());
        m.put("names", node.getImports());
        return m;
    }

    @Override
    public Map<String, Object> visit(WithBlockNode node) {
        Map<String, Object> m = base(node);
        m.put("variable", node.getVariable());
        m.put("expression", dump(node.getExpression()));
        m.put("children", kids(node));
        return m;
    }

    @Override
    public Map<String, Object> visit(ExtendsBlockNode node) {
        Map<String, Object> m = base(node);
        m.put("template", node.getTemplateName());
        return m;
    }

    @Override
    public Map<String, Object> visit(GenericBlockNode node) {
        return withKids(node);
    }
}
