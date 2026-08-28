package ast.python.statements;

import ast.python.expressions.ExpressionNode;
import ast.python.visitors.PythonASTVisitor;

public class RaiseNode extends StatementNode {
    private ExpressionNode exception;   // null for a bare 're-raise'

    public RaiseNode(int line, int column, ExpressionNode exception) {
        super(line, column);
        this.exception = exception;
        addChild(exception);
    }

    public ExpressionNode getException() { return exception; }
    public boolean hasException() { return exception != null; }

    @Override
    public <T> T accept(PythonASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
