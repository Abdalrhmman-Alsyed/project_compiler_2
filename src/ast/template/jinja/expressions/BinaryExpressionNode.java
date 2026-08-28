package ast.template.jinja.expressions;

import ast.template.NodeKind;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTVisitor;

import java.util.ArrayList;
import java.util.List;
public class BinaryExpressionNode extends ExpressionNode {
    private String operator;
    private ExpressionNode left;
    private ExpressionNode right;

    public BinaryExpressionNode(int line, int column, String operator,
                                ExpressionNode left, ExpressionNode right) {
        super(NodeKind.JINJA_EXPR_BINARY, line, column);
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public ExpressionNode getLeft() { return left; }
    public void setLeft(ExpressionNode left) { this.left = left; }

    public ExpressionNode getRight() { return right; }
    public void setRight(ExpressionNode right) { this.right = right; }
    @Override
    public List<TemplateNode> getChildren() {
        List<TemplateNode> kids = new ArrayList<>();
        if (left != null) kids.add(left);
        if (right != null) kids.add(right);
        return kids;
    }

    @Override
    public <T> T accept(TemplateASTVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
