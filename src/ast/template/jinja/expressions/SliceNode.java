package ast.template.jinja.expressions;

import ast.template.NodeKind;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTVisitor;

import java.util.ArrayList;
import java.util.List;

public class SliceNode extends ExpressionNode {
    private ExpressionNode start;
    private ExpressionNode stop;
    private ExpressionNode step;

    public SliceNode(int line, int column, ExpressionNode start, ExpressionNode stop, ExpressionNode step) {
        super(NodeKind.JINJA_EXPR_VARIABLE, line, column);
        this.start = start;
        this.stop = stop;
        this.step = step;
    }

    public ExpressionNode getStart() { return start; }
    public ExpressionNode getStop() { return stop; }
    public ExpressionNode getStep() { return step; }

    @Override
    public List<TemplateNode> getChildren() {
        List<TemplateNode> children = new ArrayList<>();
        if (start != null) children.add(start);
        if (stop != null) children.add(stop);
        if (step != null) children.add(step);
        return children;
    }

    @Override
    public <T> T accept(TemplateASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
