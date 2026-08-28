package ast.python.statements;

import ast.python.expressions.ExpressionNode;
import ast.python.program.BlockNode;
import ast.python.visitors.PythonASTVisitor;

public class WhileNode extends StatementNode {
    private ExpressionNode condition;
    private BlockNode body;

    public WhileNode(int line, int column, ExpressionNode condition, BlockNode body) {
        super(line, column);
        this.condition = condition;
        this.body = body;
        addChild(condition);
        addChild(body);
    }

    public ExpressionNode getCondition() { return condition; }
    public BlockNode getBody() { return body; }

    @Override
    public <T> T accept(PythonASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
