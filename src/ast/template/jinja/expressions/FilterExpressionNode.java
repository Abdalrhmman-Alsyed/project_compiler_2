package ast.template.jinja.expressions;

import ast.template.NodeKind;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public class FilterExpressionNode extends ExpressionNode {
    private ExpressionNode input;
    private String filterName;
    private List<ExpressionNode> arguments;
    /** Parallel to arguments: null means positional, otherwise its keyword. */
    private final List<String> keywordNames = new ArrayList<>();

    public FilterExpressionNode(int line, int column, ExpressionNode input, String filterName) {
        super(NodeKind.JINJA_EXPR_FILTER, line, column);
        this.input = input;
        this.filterName = filterName;
        this.arguments = new ArrayList<>();
    }

    // Getters
    public ExpressionNode getInput() {
        return input;
    }

    public String getFilterName() {
        return filterName;
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

    // Setters
    public void setInput(ExpressionNode input) {
        this.input = input;
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }

    // Add methods (similar to CallExpressionNode)
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

    // Remove methods
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

    public int getTotalParameters() {
        return 1 + arguments.size(); // input + arguments
    }

    public boolean isChained() {
        return input instanceof FilterExpressionNode;
    }

    @Override
    public String toString() {
        return String.format("FilterExpressionNode{filter='%s', input=%s, arguments=%d, line=%d, column=%d}",
                filterName,
                input != null ? input.getClass().getSimpleName() : "null",
                arguments.size(), getLine(), getColumn());
    }

    @Override
    public List<TemplateNode> getChildren() {
        List<TemplateNode> kids = new ArrayList<>();
        if (input != null) kids.add(input);
        kids.addAll(arguments);
        return kids;
    }
    @Override
    public <T> T accept(TemplateASTVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
