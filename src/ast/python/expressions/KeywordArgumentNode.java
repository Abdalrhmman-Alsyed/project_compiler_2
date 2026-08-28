package ast.python.expressions;

import ast.python.visitors.PythonASTVisitor;

/**
 * A named call argument: {@code render_template('index.html', products=items)}.
 *
 * <p>Without this node the keyword name is lost and only the value survives,
 * which makes it impossible to tell which context variable a template receives.
 */
public class KeywordArgumentNode extends ExpressionNode {
    private final String name;
    private final ExpressionNode value;

    public KeywordArgumentNode(int line, int column, String name, ExpressionNode value) {
        super(line, column);
        this.name = name;
        this.value = value;
        addChild(value);
    }

    public String getName() { return name; }
    public ExpressionNode getValue() { return value; }

    @Override
    public <T> T accept(PythonASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
