package ast.python.declarations;

import ast.python.PythonNode;
import ast.python.expressions.ExpressionNode;
import ast.python.visitors.PythonASTVisitor;

public class ParameterNode extends PythonNode {

    /** Plain 'a', vararg '*a', or keyword-vararg '**a'. */
    public enum Kind { NORMAL, VARARG, KWARG }

    private String name;
    private String type;
    private ExpressionNode defaultValue;
    private Kind kind = Kind.NORMAL;

    public ParameterNode(int line, int column, String name) {
        super(line, column);
        this.name = name;
    }

    public ParameterNode(int line, int column, String name, Kind kind) {
        this(line, column, name);
        this.kind = kind;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }

    public boolean isVararg() { return kind == Kind.VARARG; }
    public boolean isKwarg()  { return kind == Kind.KWARG; }

    public ExpressionNode getDefaultValue() { return defaultValue; }
    public void setDefaultValue(ExpressionNode defaultValue) {
        this.defaultValue = defaultValue;
        addChild(defaultValue);
    }

    public boolean hasType() { return type != null && !type.isEmpty(); }
    public boolean hasDefaultValue() { return defaultValue != null; }

    /** Source-like form: 'a', '*a', '**a', 'a=<expr>'. */
    public String getDisplayName() {
        String prefix = kind == Kind.VARARG ? "*" : kind == Kind.KWARG ? "**" : "";
        return prefix + name + (hasDefaultValue() ? "=..." : "");
    }

    @Override
    public <T> T accept(PythonASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
