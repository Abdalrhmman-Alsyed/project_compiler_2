package ast.template.jinja.blocks;

import ast.template.NodeKind;
import ast.template.TemplateNode;
import ast.template.jinja.expressions.ExpressionNode;
import ast.visitors.TemplateASTVisitor;

import java.util.List;
public class GenericBlockNode extends JinjaBlockNode {
    private String blockName;
    private ExpressionNode expression;

    public GenericBlockNode(int line, int column, String blockName) {
        super(NodeKind.JINJA_GENERIC_BLOCK, line, column);
        this.blockName = blockName;
    }

    public String getBlockName() { return blockName; }
    public void setBlockName(String blockName) { this.blockName = blockName; }

    public ExpressionNode getExpression() { return expression; }
    public void setExpression(ExpressionNode expression) { this.expression = expression; }
    @Override
    public List<TemplateNode> getChildren() {
        List<TemplateNode> children = new java.util.ArrayList<>();
        if (expression != null) children.add(expression);
        children.addAll(getContent());
        return children;
    }

    @Override
    public <T> T accept(TemplateASTVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
