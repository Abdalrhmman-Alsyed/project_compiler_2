package ast.python.statements;

import ast.python.expressions.ExpressionNode;
import ast.python.program.BlockNode;
import ast.python.visitors.PythonASTVisitor;

import java.util.ArrayList;
import java.util.List;

public class TryNode extends StatementNode {

    /** One 'except [Type [as name]]:' clause. */
    public static class ExceptHandler {
        private final ExpressionNode exceptionType;  // null for bare 'except:'
        private final String         alias;          // null when no 'as name'
        private final BlockNode      block;

        public ExceptHandler(ExpressionNode exceptionType, String alias, BlockNode block) {
            this.exceptionType = exceptionType;
            this.alias = alias;
            this.block = block;
        }

        public ExpressionNode getExceptionType() { return exceptionType; }
        public String    getAlias() { return alias; }
        public BlockNode getBlock() { return block; }
        public boolean   hasAlias() { return alias != null; }
    }

    private final BlockNode tryBlock;
    private final List<ExceptHandler> handlers = new ArrayList<>();
    private BlockNode finallyBlock;

    public TryNode(int line, int column, BlockNode tryBlock) {
        super(line, column);
        this.tryBlock = tryBlock;
        addChild(tryBlock);
    }

    public BlockNode getTryBlock() { return tryBlock; }
    public List<ExceptHandler> getHandlers() { return handlers; }

    public void addHandler(ExpressionNode type, String alias, BlockNode block) {
        handlers.add(new ExceptHandler(type, alias, block));
        addChild(type);
        addChild(block);
    }

    public BlockNode getFinallyBlock() { return finallyBlock; }
    public boolean hasFinally() { return finallyBlock != null; }

    public void setFinallyBlock(BlockNode finallyBlock) {
        this.finallyBlock = finallyBlock;
        addChild(finallyBlock);
    }

    @Override
    public <T> T accept(PythonASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
