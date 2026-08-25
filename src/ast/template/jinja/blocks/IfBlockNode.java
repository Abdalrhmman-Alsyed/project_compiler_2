package ast.template.jinja.blocks;

import ast.template.NodeKind;
import ast.template.TemplateNode;
import ast.template.jinja.expressions.ExpressionNode;
import ast.visitors.TemplateASTVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IfBlockNode extends JinjaBlockNode {
    private ExpressionNode condition;
    private final List<ElifBlockNode> elifBlocks = new ArrayList<>();
    private ElseBlockNode elseBlock;


    public IfBlockNode(int line, int column, ExpressionNode condition) {
        super(NodeKind.JINJA_IF_BLOCK, line, column);
        this.condition = condition;
    }

    // Getters
    public ExpressionNode getCondition() {
        return condition;
    }

    public List<ElifBlockNode> getElifBlocks() {
        return Collections.unmodifiableList(elifBlocks);
    }

    public ElseBlockNode getElseBlock() {
        return elseBlock;
    }



    // Setters
    public void setCondition(ExpressionNode condition) {
        this.condition = condition;
    }

    public void addElif(ElifBlockNode elifBlock) {
        if (elifBlock != null) {
            this.elifBlocks.add(elifBlock);
        }
    }

    public void setElseBlock(ElseBlockNode elseBlock) {
        this.elseBlock = elseBlock;
    }



    @Override
    public List<TemplateNode> getChildren() {
        List<TemplateNode> children = new ArrayList<>();
        if (condition != null) children.add(condition);
        children.addAll(getContent());
        children.addAll(elifBlocks);
        if (elseBlock != null) children.add(elseBlock);
        return children;
    }
    @Override
    public <T> T accept(TemplateASTVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
