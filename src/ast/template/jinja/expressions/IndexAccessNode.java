package ast.template.jinja.expressions;

import ast.template.NodeKind;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTVisitor;

import java.util.ArrayList;
import java.util.List;

public class IndexAccessNode extends ExpressionNode {
    private ExpressionNode object;
    private ExpressionNode index;

    public IndexAccessNode(int line, int column, ExpressionNode object, ExpressionNode index) {
        super(NodeKind.JINJA_EXPR_VARIABLE, line, column);
        this.object = object;
        this.index = index;
    }

    public ExpressionNode getObject() { return object; }
    public ExpressionNode getIndex() { return index; }

    @Override
    public List<TemplateNode> getChildren() {
        List<TemplateNode> children = new ArrayList<>();
        if (object != null) children.add(object);
        if (index != null) children.add(index);
        return children;
    }

    @Override
    public <T> T accept(TemplateASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
