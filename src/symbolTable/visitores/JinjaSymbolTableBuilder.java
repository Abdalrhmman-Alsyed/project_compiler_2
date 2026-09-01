package symbolTable.visitores;

import ast.template.TemplateNode;
import ast.template.jinja.blocks.*;
import ast.visitors.TemplateBaseASTVisitor;
import symbolTable.JinjaSymbolTable;
import symbolTable.scopes.ScopeType;
import symbolTable.symbols.Symbol;
import symbolTable.symbols.SymbolKind;
import symbolTable.symbols.SymbolType;

public class JinjaSymbolTableBuilder extends TemplateBaseASTVisitor<Void> {
    private final JinjaSymbolTable symbolTable;

    public JinjaSymbolTableBuilder() {
        this.symbolTable = new JinjaSymbolTable();
    }
    
    public JinjaSymbolTableBuilder(JinjaSymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    public JinjaSymbolTable getSymbolTable() {
        return symbolTable;
    }

    @Override
    public Void visit(ast.template.TemplateRootNode node) {
        // Root scope is already initialized inside JinjaSymbolTable constructor.
        return super.visitChildren(node);
    }

    @Override
    public Void visit(BlockBlockNode node) {
        Symbol blockSymbol = new Symbol(
                node.getBlockName(),
                node.getLine(),
                node.getColumn(),
                SymbolKind.VARIABLE, // using variable or function for block
                SymbolType.UNKNOWN
        );
        // Symbol doesn't have addAttribute, we'll just define it.
        symbolTable.getGlobalScope().define(blockSymbol);

        symbolTable.enterScope(ScopeType.BLOCK);
        for (TemplateNode child : node.getContent()) {
            child.accept(this);
        }
        symbolTable.exitScope();
        return null;
    }

    @Override
    public Void visit(ForBlockNode node) {
        symbolTable.enterScope(ScopeType.FOR_LOOP);
        
        Symbol loopVar = new Symbol(
                node.getVariable(),
                node.getLine(),
                node.getColumn(),
                SymbolKind.VARIABLE,
                SymbolType.UNKNOWN
        );
        symbolTable.defineSymbol(loopVar);

        for (TemplateNode child : node.getContent()) {
            child.accept(this);
        }
        if (node.hasElseBlock() && node.getElseBlock() != null) {
            node.getElseBlock().accept(this);
        }
        
        symbolTable.exitScope();
        return null;
    }

    @Override
    public Void visit(WithBlockNode node) {
        symbolTable.enterScope(ScopeType.WITH_BLOCK);
        
        if (node.hasVariable()) {
            Symbol withVar = new Symbol(
                    node.getVariable(),
                    node.getLine(),
                    node.getColumn(),
                    SymbolKind.VARIABLE,
                    SymbolType.UNKNOWN
            );
            symbolTable.defineSymbol(withVar);
        }
        
        if (node.getExpression() != null) {
            node.getExpression().accept(this);
        }

        for (TemplateNode child : node.getContent()) {
            child.accept(this);
        }
        
        symbolTable.exitScope();
        return null;
    }

    @Override
    public Void visit(SetBlockNode node) {
        Symbol setVar = new Symbol(
                node.getVariable(),
                node.getLine(),
                node.getColumn(),
                SymbolKind.VARIABLE,
                SymbolType.UNKNOWN
        );
        symbolTable.defineSymbol(setVar);
        
        if (node.getExpression() != null) {
            node.getExpression().accept(this);
        }
        return null;
    }
}
