package ast.template.jinja.blocks;

import ast.template.NodeKind;
import ast.template.TemplateNode;
import ast.visitors.TemplateASTVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MacroBlockNode extends JinjaBlockNode {
    private String name;
    private final List<String> parameters = new ArrayList<>();

    public MacroBlockNode(int line, int column, String name) {
        super(NodeKind.JINJA_MACRO_BLOCK, line, column);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public void addParameter(String parameter) {
        if (parameter != null) {
            this.parameters.add(parameter);
        }
    }

    @Override
    public List<TemplateNode> getChildren() {
        return super.getChildren();
    }

    @Override
    public <T> T accept(TemplateASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
