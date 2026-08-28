package ast.template.jinja.expressions;

import ast.template.NodeKind;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public class CallExpressionNode extends ExpressionNode {
    private ExpressionNode callee;
    private List<ExpressionNode> arguments;
    /** Parallel to arguments: null means positional, otherwise its keyword. */
    private final List<String> keywordNames = new ArrayList<>();

    public CallExpressionNode(int line, int column, ExpressionNode callee) {
        super(NodeKind.JINJA_EXPR_CALL, line, column);
        this.callee = callee;
        this.arguments = new ArrayList<>();
    }

    // Getters
    public ExpressionNode getCallee() {
        return callee;
    }

    public List<ExpressionNode> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    public List<String> getKeywordNames() {
        return Collections.unmodifiableList(keywordNames);
    }

    public String getKeywordName(int index) {
        return index >= 0 && index < keywordNames.size() ? keywordNames.get(index) : null;
    }

    public int getArgumentCount() {
        return arguments.size();
    }

    // Setters
    public void setCallee(ExpressionNode callee) {
        this.callee = callee;
    }

    // Add methods
    public void addArgument(ExpressionNode argument) {
        if (argument != null) {
            this.arguments.add(argument);
            this.keywordNames.add(null);
        }
    }

    public void addKeywordArgument(String name, ExpressionNode argument) {
        if (argument != null) {
            this.arguments.add(argument);
            this.keywordNames.add(name);
        }
    }

    public void addAllArguments(List<ExpressionNode> arguments) {
        if (arguments != null) {
            for (ExpressionNode argument : arguments) addArgument(argument);
        }
    }

    public void addArgument(int index, ExpressionNode argument) {
        if (argument != null && index >= 0 && index <= arguments.size()) {
            this.arguments.add(index, argument);
            this.keywordNames.add(index, null);
        }
    }

    // Remove methods
    public ExpressionNode removeArgument(int index) {
        if (index >= 0 && index < arguments.size()) {
            keywordNames.remove(index);
            return this.arguments.remove(index);
        }
        return null;
    }

    public boolean removeArgument(ExpressionNode argument) {
        int index = arguments.indexOf(argument);
        if (index < 0) return false;
        arguments.remove(index);
        keywordNames.remove(index);
        return true;
    }

    // Utility methods
    public boolean hasArguments() {
        return !this.arguments.isEmpty();
    }

    public void clearArguments() {
        this.arguments.clear();
        this.keywordNames.clear();
    }

    public ExpressionNode getArgument(int index) {
        if (index >= 0 && index < arguments.size()) {
            return this.arguments.get(index);
        }
        return null;
    }

    public boolean isMethodCall() {
        return callee instanceof VariableNode &&
                ((VariableNode) callee).getPath().size() > 1;
    }

    public String getMethodName() {
        if (isMethodCall()) {
            VariableNode var = (VariableNode) callee;
            return var.getPath().get(var.getPath().size() - 1);
        }
        return null;
    }

    @Override
    public String toString() {
        String calleeStr = callee != null ? callee.toString() : "null";
        return String.format("CallExpressionNode{callee=%s, arguments=%d, line=%d, column=%d}",
                calleeStr, arguments.size(), getLine(), getColumn());
    }

    @Override
    public List<TemplateNode> getChildren() {
        List<TemplateNode> kids = new ArrayList<>();
        if (callee != null) kids.add(callee);
        kids.addAll(arguments);
        return kids;
    }

    @Override
    public <T> T accept(TemplateASTVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
