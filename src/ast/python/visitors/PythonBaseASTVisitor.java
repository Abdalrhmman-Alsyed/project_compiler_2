package ast.python.visitors;

import ast.python.PythonNode;

public abstract class PythonBaseASTVisitor<T> implements PythonASTVisitor<T> {

    protected T defaultResult() { return null; }

    protected T aggregateResult(T aggregate, T nextResult) {
        return nextResult;
    }

    protected T visitChildren(PythonNode node) {
        T result = defaultResult();
        for (PythonNode child : node.getChildren()) {
            T childResult = child.accept(this);
             result = aggregateResult(result, childResult);
        }
        return result;
    }

    // Program
    @Override public T visit(ast.python.program.ProgramNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.program.BlockNode n) { return visitChildren(n); }

    // Declarations
    @Override public T visit(ast.python.declarations.FunctionNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.declarations.ParameterNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.declarations.ImportNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.declarations.DecoratorNode n) { return visitChildren(n); }

    // Statements
    @Override public T visit(ast.python.statements.AssignmentNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.IfNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.ForNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.WhileNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.TryNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.RaiseNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.ReturnNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.ExpressionStatementNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.GlobalNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.WithNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.PassNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.BreakNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.statements.ContinueNode n) { return visitChildren(n); }

    // Expressions
    @Override public T visit(ast.python.expressions.BinaryOpNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.expressions.UnaryOpNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.expressions.CallNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.expressions.AttributeNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.expressions.IndexNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.expressions.IdentifierNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.expressions.KeywordArgumentNode n) { return visitChildren(n); }

    // Literals
    @Override public T visit(ast.python.literals.IntLiteralNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.literals.FloatLiteralNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.literals.StringLiteralNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.literals.BoolLiteralNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.literals.NoneLiteralNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.literals.ListLiteralNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.literals.DictLiteralNode n) { return visitChildren(n); }
    @Override public T visit(ast.python.literals.SetLiteralNode n) { return visitChildren(n); }
}
