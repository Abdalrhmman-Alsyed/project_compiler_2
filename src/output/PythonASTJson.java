package output;

import ast.python.PythonNode;
import ast.python.declarations.*;
import ast.python.expressions.*;
import ast.python.literals.*;
import ast.python.program.BlockNode;
import ast.python.program.ProgramNode;
import ast.python.statements.*;
import ast.python.visitors.PythonASTVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PythonASTJson implements PythonASTVisitor<Map<String, Object>> {

    static Map<String, Object> dump(PythonNode ast, String source) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("source", source);
        root.put("ast", ast == null ? null : ast.accept(new PythonASTJson()));
        return root;
    }

    private Map<String, Object> node(String type, PythonNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("line", n.getLine());
        m.put("column", n.getColumn());
        return m;
    }

    private Object ast(PythonNode n) {
        return n == null ? null : n.accept(this);
    }

    private List<Object> list(Iterable<? extends PythonNode> nodes) {
        List<Object> out = new ArrayList<>();
        if (nodes == null) return out;
        for (PythonNode n : nodes) {
            if (n != null) out.add(n.accept(this));
        }
        return out;
    }

    @Override
    public Map<String, Object> visit(ProgramNode n) {
        Map<String, Object> m = node("Program", n);
        if (n.getFileName() != null) m.put("fileName", n.getFileName());
        m.put("statements", list(n.getStatements()));
        return m;
    }

    @Override
    public Map<String, Object> visit(BlockNode n) {
        Map<String, Object> m = node("Block", n);
        m.put("statements", list(n.getStatements()));
        return m;
    }

    @Override
    public Map<String, Object> visit(FunctionNode n) {
        Map<String, Object> m = node("Function", n);
        m.put("name", n.getName());
        if (n.hasReturnType()) m.put("returnType", n.getReturnType());
        m.put("decorators", list(n.getDecorators()));
        m.put("parameters", list(n.getParameters()));
        m.put("body", ast(n.getBody()));
        return m;
    }

    @Override
    public Map<String, Object> visit(ParameterNode n) {
        Map<String, Object> m = node("Parameter", n);
        m.put("name", n.getName());
        m.put("kind", n.getKind().name());
        if (n.hasType()) m.put("annotation", n.getType());
        if (n.hasDefaultValue()) m.put("default", ast(n.getDefaultValue()));
        return m;
    }

    @Override
    public Map<String, Object> visit(ImportNode n) {
        Map<String, Object> m = node("Import", n);
        m.put("fromImport", n.isFromImport());
        m.put("importAll", n.isImportAll());
        if (n.getModule() != null) m.put("module", n.getModule());
        m.put("names", list(n.getImports()));
        return m;
    }

    @Override
    public Map<String, Object> visit(DecoratorNode n) {
        Map<String, Object> m = node("Decorator", n);
        m.put("name", n.getName());
        m.put("arguments", list(n.getArguments()));
        return m;
    }

    @Override
    public Map<String, Object> visit(AssignmentNode n) {
        Map<String, Object> m = node("Assignment", n);
        m.put("operator", n.getOperator());
        m.put("target", ast(n.getTarget()));
        m.put("value", ast(n.getValue()));
        return m;
    }

    @Override
    public Map<String, Object> visit(IfNode n) {
        Map<String, Object> m = node("If", n);
        m.put("condition", ast(n.getCondition()));
        m.put("then", ast(n.getThenBlock()));
        List<Object> elifs = new ArrayList<>();
        for (IfNode.ElifBranch e : n.getElifBranches()) {
            Map<String, Object> br = new LinkedHashMap<>();
            br.put("condition", ast(e.getCondition()));
            br.put("block", ast(e.getBlock()));
            elifs.add(br);
        }
        m.put("elif", elifs);
        m.put("else", ast(n.getElseBlock()));
        return m;
    }

    @Override
    public Map<String, Object> visit(ForNode n) {
        Map<String, Object> m = node("For", n);
        m.put("variable", ast(n.getVariable()));
        m.put("iterable", ast(n.getIterable()));
        m.put("body", ast(n.getBody()));
        return m;
    }

    @Override
    public Map<String, Object> visit(WhileNode n) {
        Map<String, Object> m = node("While", n);
        m.put("condition", ast(n.getCondition()));
        m.put("body", ast(n.getBody()));
        return m;
    }

    @Override
    public Map<String, Object> visit(TryNode n) {
        Map<String, Object> m = node("Try", n);
        m.put("body", ast(n.getTryBlock()));
        List<Object> handlers = new ArrayList<>();
        for (TryNode.ExceptHandler h : n.getHandlers()) {
            Map<String, Object> hm = new LinkedHashMap<>();
            hm.put("type", ast(h.getExceptionType()));
            hm.put("alias", h.getAlias());
            hm.put("block", ast(h.getBlock()));
            handlers.add(hm);
        }
        m.put("except", handlers);
        m.put("finally", ast(n.getFinallyBlock()));
        return m;
    }

    @Override
    public Map<String, Object> visit(RaiseNode n) {
        Map<String, Object> m = node("Raise", n);
        m.put("exception", ast(n.getException()));
        return m;
    }

    @Override
    public Map<String, Object> visit(ReturnNode n) {
        Map<String, Object> m = node("Return", n);
        m.put("value", ast(n.getValue()));
        return m;
    }

    @Override
    public Map<String, Object> visit(ExpressionStatementNode n) {
        Map<String, Object> m = node("ExprStmt", n);
        m.put("expression", ast(n.getExpression()));
        return m;
    }

    @Override
    public Map<String, Object> visit(GlobalNode n) {
        Map<String, Object> m = node("Global", n);
        m.put("names", n.getVariables());
        return m;
    }

    @Override
    public Map<String, Object> visit(WithNode n) {
        Map<String, Object> m = node("With", n);
        m.put("expression", ast(n.getExpression()));
        m.put("alias", ast(n.getAlias()));
        m.put("body", ast(n.getBody()));
        return m;
    }

    @Override
    public Map<String, Object> visit(PassNode n) {
        return node("Pass", n);
    }

    @Override
    public Map<String, Object> visit(BreakNode n) {
        return node("Break", n);
    }

    @Override
    public Map<String, Object> visit(ContinueNode n) {
        return node("Continue", n);
    }

    @Override
    public Map<String, Object> visit(BinaryOpNode n) {
        Map<String, Object> m = node("BinaryOp", n);
        m.put("op", n.getOperator());
        m.put("left", ast(n.getLeft()));
        m.put("right", ast(n.getRight()));
        return m;
    }

    @Override
    public Map<String, Object> visit(UnaryOpNode n) {
        Map<String, Object> m = node("UnaryOp", n);
        m.put("op", n.getOperator());
        m.put("operand", ast(n.getOperand()));
        return m;
    }

    @Override
    public Map<String, Object> visit(CallNode n) {
        Map<String, Object> m = node("Call", n);
        m.put("function", ast(n.getFunction()));
        m.put("arguments", list(n.getArguments()));
        return m;
    }

    @Override
    public Map<String, Object> visit(AttributeNode n) {
        Map<String, Object> m = node("Attribute", n);
        m.put("object", ast(n.getObject()));
        m.put("attr", n.getAttributeName());
        return m;
    }

    @Override
    public Map<String, Object> visit(IndexNode n) {
        Map<String, Object> m = node("Index", n);
        m.put("collection", ast(n.getCollection()));
        m.put("index", ast(n.getIndex()));
        return m;
    }

    @Override
    public Map<String, Object> visit(IdentifierNode n) {
        Map<String, Object> m = node("Identifier", n);
        m.put("name", n.getName());
        return m;
    }

    @Override
    public Map<String, Object> visit(KeywordArgumentNode n) {
        Map<String, Object> m = node("KeywordArg", n);
        m.put("name", n.getName());
        m.put("value", ast(n.getValue()));
        return m;
    }

    @Override
    public Map<String, Object> visit(IntLiteralNode n) {
        Map<String, Object> m = node("IntLiteral", n);
        m.put("value", n.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(FloatLiteralNode n) {
        Map<String, Object> m = node("FloatLiteral", n);
        m.put("value", n.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(StringLiteralNode n) {
        Map<String, Object> m = node("StringLiteral", n);
        m.put("value", n.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(BoolLiteralNode n) {
        Map<String, Object> m = node("BoolLiteral", n);
        m.put("value", n.getValue());
        return m;
    }

    @Override
    public Map<String, Object> visit(NoneLiteralNode n) {
        return node("NoneLiteral", n);
    }

    @Override
    public Map<String, Object> visit(ListLiteralNode n) {
        Map<String, Object> m = node("ListLiteral", n);
        m.put("elements", list(n.getElements()));
        return m;
    }

    @Override
    public Map<String, Object> visit(DictLiteralNode n) {
        Map<String, Object> m = node("DictLiteral", n);
        List<Object> entries = new ArrayList<>();
        for (DictLiteralNode.DictEntry e : n.getEntries()) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("key", ast(e.getKey()));
            em.put("value", ast(e.getValue()));
            entries.add(em);
        }
        m.put("entries", entries);
        return m;
    }

    @Override
    public Map<String, Object> visit(SetLiteralNode n) {
        Map<String, Object> m = node("SetLiteral", n);
        m.put("elements", list(n.getElements()));
        return m;
    }
}
