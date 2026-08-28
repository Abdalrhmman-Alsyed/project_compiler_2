package ast.template.jinja.expressions;

import ast.template.NodeKind;
import ast.template.TemplateNode;
import ast.template.jinja.JinjaNode;
import ast.visitors.TemplateASTVisitor;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
public class JinjaExpressionNode extends JinjaNode {
    private ExpressionNode expression;

    public JinjaExpressionNode(int line, int column, ExpressionNode expression) {
        super(NodeKind.JINJA_EXPR_EXPRESSION, line, column);
        this.expression = expression;
    }

    public ExpressionNode getExpression() { return expression; }
    public void setExpression(ExpressionNode expression) {
        this.expression = expression;
    }
    @Override
    public List<TemplateNode> getChildren() {
        List<TemplateNode> kids = new ArrayList<>();
        if (getExpression() != null) kids.add(getExpression());
        return kids;
    }
    @Override
    public <T> T accept(TemplateASTVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
