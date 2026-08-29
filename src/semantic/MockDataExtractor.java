package semantic;

import ast.python.PythonNode;
import ast.python.declarations.*;
import ast.python.program.*;
import ast.python.expressions.*;
import ast.python.literals.*;
import ast.python.statements.*;
import ast.python.visitors.PythonBaseASTVisitor;

import java.util.*;

/**
 * Extracts top-level dictionary and list literal assignments from the Python AST
 * into Java collections (List, Map).
 * Used for both Semantic Validation in Jinja and future HTML Code Generation.
 */
public class MockDataExtractor extends PythonBaseASTVisitor<Object> {

    private final Map<String, Object> extractedData = new LinkedHashMap<>();

    public Map<String, Object> getExtractedData() {
        return Collections.unmodifiableMap(extractedData);
    }

    public void printReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  MOCK DATA EXTRACTOR: Python Literals → Java Objects");
        System.out.println("=".repeat(60));
        if (extractedData.isEmpty()) {
            System.out.println("  No literal data structures extracted.");
            return;
        }
        for (Map.Entry<String, Object> entry : extractedData.entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + entry.getValue());
        }
    }

    @Override
    public Object visit(AssignmentNode node) {
        if (node.getTarget() instanceof IdentifierNode id) {
            Object value = node.getValue().accept(this);
            if (value != null) {
                extractedData.put(id.getName(), value);
            }
        }
        return null; // Return null so we don't bubble up the value unintentionally
    }

    @Override
    public Object visit(ListLiteralNode node) {
        List<Object> list = new ArrayList<>();
        for (ExpressionNode elem : node.getElements()) {
            Object val = elem.accept(this);
            if (val != null) {
                list.add(val);
            }
        }
        return list;
    }

    @Override
    public Object visit(DictLiteralNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (DictLiteralNode.DictEntry entry : node.getEntries()) {
            Object keyObj = entry.getKey().accept(this);
            Object valObj = entry.getValue().accept(this);
            if (keyObj instanceof String keyStr && valObj != null) {
                map.put(keyStr, valObj);
            }
        }
        return map;
    }

    @Override
    public Object visit(StringLiteralNode node) {
        return stripQuotes(node.getValue());
    }

    @Override
    public Object visit(IntLiteralNode node) {
        return node.getValue();
    }

    @Override
    public Object visit(FloatLiteralNode node) {
        return node.getValue();
    }

    @Override
    public Object visit(BoolLiteralNode node) {
        return node.getValue();
    }

    @Override
    public Object visit(NoneLiteralNode node) {
        return "None";
    }

    @Override public Object visit(ProgramNode node) { return visitChildren(node); }
    @Override public Object visit(BlockNode node) { return visitChildren(node); }
    @Override public Object visit(FunctionNode node) { return visitChildren(node); }
    @Override public Object visit(ParameterNode node) { return visitChildren(node); }
    @Override public Object visit(ImportNode node) { return visitChildren(node); }
    @Override public Object visit(DecoratorNode node) { return visitChildren(node); }

    @Override public Object visit(IfNode node) { return visitChildren(node); }
    @Override public Object visit(ForNode node) { return visitChildren(node); }
    @Override public Object visit(WhileNode node) { return visitChildren(node); }
    @Override public Object visit(TryNode node) { return visitChildren(node); }
    @Override public Object visit(RaiseNode node) { return visitChildren(node); }
    @Override public Object visit(ReturnNode node) { return visitChildren(node); }
    @Override public Object visit(ExpressionStatementNode node) { return visitChildren(node); }
    @Override public Object visit(GlobalNode node) { return visitChildren(node); }
    @Override public Object visit(WithNode node) { return visitChildren(node); }
    @Override public Object visit(PassNode node) { return visitChildren(node); }
    @Override public Object visit(BreakNode node) { return visitChildren(node); }
    @Override public Object visit(ContinueNode node) { return visitChildren(node); }

    // Ignored nodes that we don't care about for mock data extraction
    @Override public Object visit(BinaryOpNode node) { return null; }
    @Override public Object visit(UnaryOpNode node) { return null; }
    @Override public Object visit(CallNode node) { return null; }
    @Override public Object visit(AttributeNode node) { return null; }
    @Override public Object visit(IndexNode node) { return null; }
    @Override public Object visit(KeywordArgumentNode node) { return null; }
    @Override public Object visit(IdentifierNode node) { return null; }
    @Override public Object visit(SetLiteralNode node) { return null; }

    private String stripQuotes(String val) {
        if (val == null) return null;
        if (val.startsWith("\"") && val.endsWith("\"") || val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }
}
