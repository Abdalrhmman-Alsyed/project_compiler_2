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

    public boolean hasElseBlock() {
        return elseBlock != null;
    }


    // Setters
    public void setCondition(ExpressionNode condition) {
        this.condition = condition;
    }

    public void addElif(ElifBlockNode elifBlock) {
        if (elifBlock != null) elifBlocks.add(elifBlock);
    }

    public void setElseBlock(ElseBlockNode elseBlock) {
        this.elseBlock = elseBlock;
    }


    @Override
    public List<TemplateNode> getChildren() {
        List<TemplateNode> kids = new ArrayList<>();
        if (condition != null) kids.add(condition);
        kids.addAll(getContent());
        kids.addAll(elifBlocks);
        if (elseBlock != null) kids.add(elseBlock);
        return kids;
    }
    @Override
    public <T> T accept(TemplateASTVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
