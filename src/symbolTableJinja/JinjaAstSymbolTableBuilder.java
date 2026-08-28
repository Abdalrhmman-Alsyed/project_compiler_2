package symbolTableJinja;

import ast.template.TemplateNode;
import ast.template.jinja.blocks.*;
import ast.template.jinja.expressions.FilterExpressionNode;
import ast.template.jinja.expressions.VariableNode;
import ast.visitors.TemplateBaseASTVisitor;

import java.util.Set;

/**
 * Builds the Jinja symbol table from the project's AST, never from ANTLR's
 * parse tree.  This keeps scope construction aligned with semantic analysis
 * and with code generation.
 */
public final class JinjaAstSymbolTableBuilder extends TemplateBaseASTVisitor<Void> {
    private final JinjaSymbolTable symbolTable;

    public JinjaAstSymbolTableBuilder(String templateName, Set<String> contextVariables) {
        symbolTable = new JinjaSymbolTable(templateName);
        if (contextVariables != null) {
            for (String name : contextVariables) {
                if (name != null && !name.isBlank()) {
                    symbolTable.defineSymbol(new VariableSymbol(name, -1, -1, "context"));
                }
            }
        }
    }

    public JinjaSymbolTable getSymbolTable() {
        return symbolTable;
    }

    @Override
    public Void visit(BlockBlockNode node) {
        symbolTable.defineSymbol(new BlockSymbol(node.getBlockName(), node.getLine(), node.getColumn()));
        symbolTable.enterScope("block:" + node.getBlockName(), JinjaSymbolType.BLOCK);
        super.visit(node);
        symbolTable.exitScope();
        return null;
    }

    @Override
    public Void visit(ForBlockNode node) {
        // The iterable belongs to the enclosing scope; the loop variable does not.
        visitChild(node.getIterable());
        symbolTable.enterScope("for:" + node.getVariable(), JinjaSymbolType.LOOP_VARIABLE);
        symbolTable.defineSymbol(new VariableSymbol(
                node.getVariable(), node.getLine(), node.getColumn(), "any", true));
        // Jinja exposes `loop` only inside a for body.
        symbolTable.defineSymbol(new VariableSymbol("loop", node.getLine(), node.getColumn(), "Loop"));
        for (TemplateNode child : node.getContent()) visitChild(child);
        symbolTable.exitScope();
        if (node.hasElseBlock()) visitChild(node.getElseBlock());
        return null;
    }

    @Override
    public Void visit(WithBlockNode node) {
        // Evaluate the assigned expression in the enclosing scope first.
        visitChild(node.getExpression());
        if (!node.hasVariable()) return super.visit(node);

        symbolTable.enterScope("with:" + node.getVariable(), JinjaSymbolType.VARIABLE);
        symbolTable.defineSymbol(new VariableSymbol(
                node.getVariable(), node.getLine(), node.getColumn(), "any"));
        for (TemplateNode child : node.getContent()) visitChild(child);
        symbolTable.exitScope();
        return null;
    }

    @Override
    public Void visit(SetBlockNode node) {
        visitChild(node.getExpression());
        symbolTable.defineSymbol(new VariableSymbol(
                node.getVariable(), node.getLine(), node.getColumn(), "any"));
        return null;
    }

    @Override
    public Void visit(ExtendsBlockNode node) {
        symbolTable.addExtendsTemplate(stripQuotes(node.getTemplateName()));
        return null;
    }

    @Override
    public Void visit(IncludeBlockNode node) {
        symbolTable.addIncludedTemplate(stripQuotes(node.getTemplateName()));
        return null;
    }

    @Override
    public Void visit(ImportBlockNode node) {
        ImportSymbol imported = new ImportSymbol(
                stripQuotes(node.getTemplateName()), node.getLine(), node.getColumn());
        imported.setAlias(node.getAlias());
        symbolTable.defineSymbol(imported);
        if (node.getAlias() != null && !node.getAlias().isBlank()) {
            symbolTable.defineSymbol(new VariableSymbol(
                    node.getAlias(), node.getLine(), node.getColumn(), "module"));
        }
        return null;
    }

    @Override
    public Void visit(FromImportBlockNode node) {
        ImportSymbol imported = new ImportSymbol(
                stripQuotes(node.getTemplateName()), node.getLine(), node.getColumn());
        for (String name : node.getImports()) {
            imported.addImportedName(name);
            symbolTable.defineSymbol(new VariableSymbol(name, node.getLine(), node.getColumn(), "import"));
        }
        symbolTable.defineSymbol(imported);
        return null;
    }

    @Override
    public Void visit(VariableNode node) {
        if (node.getName() != null) symbolTable.recordSymbolUsage(node.getName(), node.getLine());
        return null;
    }

    @Override
    public Void visit(FilterExpressionNode node) {
        symbolTable.recordSymbolUsage(node.getFilterName(), node.getLine());
        return super.visit(node);
    }

    private static String stripQuotes(String text) {
        if (text != null && text.length() >= 2 &&
                ((text.startsWith("\"") && text.endsWith("\"")) ||
                 (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
}
