package ast.visitors;

import ast.template.TemplateNode;
import ast.template.TemplateRootNode;
import ast.template.css.*;
import ast.template.html.*;
import ast.template.jinja.blocks.*;
import ast.template.jinja.expressions.*;
import ast.template.jinja.expressions.literals.BooleanLiteralNode;
import ast.template.jinja.expressions.literals.NoneLiteralNode;
import ast.template.jinja.expressions.literals.NumberLiteralNode;
import ast.template.jinja.expressions.literals.StringLiteralNode;
import gen.FlaskTemplateParser;
import gen.FlaskTemplateParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;


public class TemplateASTBuilder extends FlaskTemplateParserBaseVisitor<TemplateNode> {

    @Override
    public TemplateNode visitTemplateRoot(FlaskTemplateParser.TemplateRootContext ctx) {

        TemplateRootNode root = new TemplateRootNode(ctx.start.getLine(), ctx.start.getCharPositionInLine());

        if (ctx.doctype() != null) {
            root.addDocument(visit(ctx.doctype()));
        }

        root.addDocument(visit(ctx.html()));

        return root;
    }


    @Override
    public TemplateNode visitDoctype(FlaskTemplateParser.DoctypeContext ctx) {


        return new DoctypeNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.HTML_DOCTYPE().getText());
    }

    @Override
    public TemplateNode visitHtmlDocument(FlaskTemplateParser.HtmlDocumentContext ctx) {

        HTMLDocumentNode node = new HTMLDocumentNode(ctx.start.getLine(), ctx.start.getCharPositionInLine());

        // attributes
        if (ctx.htmlAttributes() != null) {
            node.addAllAttributes(visitHtmlAttributeList(ctx.htmlAttributes()));
        }

        // المحتوى الداخلي
        if (ctx.templateContent() != null) {
            node.addAllContent(visitTemplateContentList(ctx.templateContent()));
        }

        return node;
    }


    private List<TemplateNode> visitTemplateContentList(FlaskTemplateParser.TemplateContentContext ctx) {
        List<TemplateNode> list = new ArrayList<>();
        if (ctx == null) return list;
        for (var node : ctx.contentItem()) {
            TemplateNode child = visit(node);
            if (child != null) {
                list.add(child);
            }
        }
        return list;
    }


    @Override
    public TemplateNode visitHtmlContent(FlaskTemplateParser.HtmlContentContext ctx) {
        return visit(ctx.htmlElement());
    }

    @Override
    public TemplateNode visitNewlineContent(FlaskTemplateParser.NewlineContentContext ctx) {
        return null;
    }

    @Override
    public TemplateNode visitNormalElement(FlaskTemplateParser.NormalElementContext ctx) {
        HTMLNormalElementNode htmlNormalElementNode = (HTMLNormalElementNode) visit(ctx.openingTag());

        if (ctx.templateContent() != null) {
            htmlNormalElementNode.addAllContent(visitTemplateContentList(ctx.templateContent()));
        }

        return htmlNormalElementNode;
    }


    @Override
    public TemplateNode visitHtmlTextContent(FlaskTemplateParser.HtmlTextContentContext ctx) {

        return visitHtmlText(ctx.htmlText());
    }

    @Override
    public TemplateNode visitHtmlText(FlaskTemplateParser.HtmlTextContext ctx) {
        StringBuilder text = new StringBuilder();
        for (TerminalNode token : ctx.HTML_TEXT()) {
            text.append(token.getText());
        }

        return new HTMLTextNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                text.toString()
        );
    }

    @Override
    public TemplateNode visitOpeningTagNode(FlaskTemplateParser.OpeningTagNodeContext ctx) {
        HTMLNormalElementNode htmlElementNode = new HTMLNormalElementNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), ctx.HTML_ID().getText());

        htmlElementNode.addAllAttributes(visitHtmlAttributeList(ctx.htmlAttributes()));

        return htmlElementNode;
    }

    @Override
    public TemplateNode visitSelfClosingElementTag(FlaskTemplateParser.SelfClosingElementTagContext ctx) {


        return visitSelfClosingTagNode((FlaskTemplateParser.SelfClosingTagNodeContext) ctx.selfClosingTag());
    }

    @Override
    public TemplateNode visitSelfClosingTagNode(FlaskTemplateParser.SelfClosingTagNodeContext ctx) {
        HTMLSelfClosingElementNode htmlElementNode = new HTMLSelfClosingElementNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.HTML_ID().getText()
        );
        htmlElementNode.addAllAttributes(visitHtmlAttributeList(ctx.htmlAttributes()));
        return htmlElementNode;
    }

    @Override
    public TemplateNode visitVoidElementTag(FlaskTemplateParser.VoidElementTagContext ctx) {


        return visitVoidTagNode((FlaskTemplateParser.VoidTagNodeContext) ctx.voidTag());
    }

    @Override
    public TemplateNode visitVoidTagNode(FlaskTemplateParser.VoidTagNodeContext ctx) {
        HTMLVoidElementNode htmlElementNode = new HTMLVoidElementNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), ctx.VOID_TAG().getText()

        );

        htmlElementNode.addAllAttributes(visitHtmlAttributeList(ctx.htmlAttributes()));
        return htmlElementNode;
    }

    private List<HTMLAttributeNode> visitHtmlAttributeList(FlaskTemplateParser.HtmlAttributesContext ctx) {

        List<HTMLAttributeNode> attrs = new ArrayList<>();

        if (ctx == null) return attrs;

        FlaskTemplateParser.HtmlAttributeListContext listCtx = (FlaskTemplateParser.HtmlAttributeListContext) ctx;

        for (FlaskTemplateParser.HtmlAttributeContext attrCtx : listCtx.htmlAttribute()) {

            attrs.add((HTMLAttributeNode) visit(attrCtx));
        }

        return attrs;
    }

    @Override
    public TemplateNode visitAttributeWithValue(FlaskTemplateParser.AttributeWithValueContext ctx) {

        String name = ctx.HTML_ID().getText();
        HTMLAttributeNode node = new HTMLAttributeNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), name);
        // إذا كانت القيمة نصية بسيطة
        node.addAllValueParts(
                visitDoubleQuotedValueList((FlaskTemplateParser.DoubleQuotedValueContext) ctx.htmlAttributeValue())
        );
        return node;
    }


    @Override
    public TemplateNode visitBooleanAttribute(FlaskTemplateParser.BooleanAttributeContext ctx) {
        HTMLAttributeNode node = new HTMLAttributeNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), ctx.HTML_BOOLEAN_ATTR().getText());
        node.setBoolean(true);
        return node;
    }

    public List<TemplateNode> visitDoubleQuotedValueList(FlaskTemplateParser.DoubleQuotedValueContext ctx) {
        if (ctx == null || ctx.attrValueContent() == null) return new ArrayList<>();
        return AttrValueContentList(ctx.attrValueContent());
    }

    @Override
    public TemplateNode visitAttrText(FlaskTemplateParser.AttrTextContext ctx) {
        return new HTMLAttributeTextNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.ATTR_VALUE_ID().getText()
        );
    }


    private List<TemplateNode> AttrValueContentList(FlaskTemplateParser.AttrValueContentContext ctx) {
        List<TemplateNode> list = new ArrayList<>();
        if (ctx == null) return list;

        for (FlaskTemplateParser.AttrValueItemContext itemCtx : ctx.attrValueItem()) {
            list.add(visit(itemCtx));
        }
        return list;
    }

    @Override
    public TemplateNode visitAttrJinjaBlockItem(FlaskTemplateParser.AttrJinjaBlockItemContext ctx) {
        return visitAttrJinjaBlock(ctx.attrJinjaBlock());
    }

    @Override
    public TemplateNode visitAttrJinjaBlock(FlaskTemplateParser.AttrJinjaBlockContext ctx) {
        return visit(ctx.attrBlockStatement());
    }

    @Override
    public TemplateNode visitAttrSetBlock(FlaskTemplateParser.AttrSetBlockContext ctx) {
        return new SetBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.BLOCK_ID().getText(),
                (ExpressionNode) visit(ctx.blockExpression())
        );
    }

    @Override
    public TemplateNode visitAttrIncludeBlock(FlaskTemplateParser.AttrIncludeBlockContext ctx) {
        return new IncludeBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.BLOCK_STRING().getText()
        );
    }

    @Override
    public TemplateNode visitAttrGenericBlock(FlaskTemplateParser.AttrGenericBlockContext ctx) {
        GenericBlockNode node = new GenericBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.BLOCK_ID().getText()
        );
        if (ctx.blockExpression() != null) {
            node.setExpression((ExpressionNode) visit(ctx.blockExpression()));
        }
        return node;
    }

    //=========================================================================================
    // Jinja blocks — bodies are nested children of the opening construct
    //=========================================================================================

    @Override
    public TemplateNode visitIfStart(FlaskTemplateParser.IfStartContext ctx) {
        ExpressionNode condition = (ExpressionNode) visit(ctx.blockExpression());
        IfBlockNode ifNode = new IfBlockNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), condition);
        ifNode.addAllContent(visitTemplateContentList(ctx.templateContent()));
        for (FlaskTemplateParser.ElifBlockContext elifCtx : ctx.elifBlock()) {
            ifNode.addElif((ElifBlockNode) visit(elifCtx));
        }
        if (ctx.elseBlock() != null) {
            ifNode.setElseBlock((ElseBlockNode) visit(ctx.elseBlock()));
        }
        return ifNode;
    }

    @Override
    public TemplateNode visitElifBlock(FlaskTemplateParser.ElifBlockContext ctx) {
        ExpressionNode condition = (ExpressionNode) visit(ctx.blockExpression());

        ElifBlockNode elifNode = new ElifBlockNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), condition);
        elifNode.addAllContent(visitTemplateContentList(ctx.templateContent()));
        return elifNode;
    }


    @Override
    public TemplateNode visitElseBlock(FlaskTemplateParser.ElseBlockContext ctx) {

        ElseBlockNode elseNode = new ElseBlockNode(ctx.start.getLine(), ctx.start.getCharPositionInLine());
        elseNode.addAllContent(visitTemplateContentList(ctx.templateContent()));
        return elseNode;
    }


    @Override
    public TemplateNode visitForStart(FlaskTemplateParser.ForStartContext ctx) {
        ExpressionNode iterable = (ExpressionNode) visit(ctx.blockExpression());

        ForBlockNode forBlockNode = new ForBlockNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), ctx.BLOCK_ID().getText(), iterable);
        forBlockNode.addAllContent(visitTemplateContentList(ctx.templateContent()));
        if (ctx.elseBlock() != null) {
            forBlockNode.setElseBlock((ElseBlockNode) visit(ctx.elseBlock()));
        }
        return forBlockNode;
    }

    @Override
    public TemplateNode visitBlockStart(FlaskTemplateParser.BlockStartContext ctx) {
        BlockBlockNode blockBlockNode = new BlockBlockNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(),ctx.BLOCK_ID().getText());
        blockBlockNode.addAllContent(visitTemplateContentList(ctx.templateContent()));
        return blockBlockNode;
    }

    @Override
    public TemplateNode visitSetBlock(FlaskTemplateParser.SetBlockContext ctx) {
        ExpressionNode value = (ExpressionNode) visit(ctx.blockExpression());

        return new SetBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.BLOCK_ID().getText(),
                value
        );
    }

    @Override
    public TemplateNode visitIncludeBlock(FlaskTemplateParser.IncludeBlockContext ctx) {
        return new IncludeBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.BLOCK_STRING().getText()
        );
    }

    @Override
    public TemplateNode visitImportBlock(FlaskTemplateParser.ImportBlockContext ctx) {
        String templateName = ctx.BLOCK_STRING().getText();
        String alias = ctx.BLOCK_ID() != null ? ctx.BLOCK_ID().getText() : null;

        return new ImportBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                templateName,
                alias
        );
    }


    @Override
    public TemplateNode visitFromImportBlock(FlaskTemplateParser.FromImportBlockContext ctx) {

        FromImportBlockNode fromImportBlockNode = new FromImportBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.BLOCK_STRING().getText()
        );

        for (var node : ctx.importList().BLOCK_ID()){
            fromImportBlockNode.addImport(node.getText());
        }
        return fromImportBlockNode;
    }


    @Override
    public TemplateNode visitWithStart(FlaskTemplateParser.WithStartContext ctx) {
        WithBlockNode withBlockNode = new WithBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                (ExpressionNode) visit(ctx.blockExpression())
        );
        withBlockNode.addAllContent(visitTemplateContentList(ctx.templateContent()));
        return withBlockNode;
    }

    @Override
    public TemplateNode visitExtendsBlock(FlaskTemplateParser.ExtendsBlockContext ctx) {

        return new ExtendsBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.BLOCK_STRING().getText()
        );
    }

    @Override
    public TemplateNode visitGenericBlock(FlaskTemplateParser.GenericBlockContext ctx) {

        GenericBlockNode genericBlockNode = new GenericBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.BLOCK_ID().getText()
        );

        if (ctx.blockExpression() != null) {
            genericBlockNode.setExpression((ExpressionNode) visit(ctx.blockExpression()));
        }
        return genericBlockNode;
    }

    @Override
    public TemplateNode visitMacroBlock(FlaskTemplateParser.MacroBlockContext ctx) {
        MacroBlockNode macroNode = new MacroBlockNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.BLOCK_ID().getText()
        );
        if (ctx.macroParameters() != null) {
            for (TerminalNode param : ctx.macroParameters().BLOCK_ID()) {
                macroNode.addParameter(param.getText());
            }
        }
        macroNode.addAllContent(visitTemplateContentList(ctx.templateContent()));
        return macroNode;
    }


    //=========================================================================================
    // Jinja expressions {{ }}
    //=========================================================================================


    @Override
    public TemplateNode visitAttrJinjaExprItem(FlaskTemplateParser.AttrJinjaExprItemContext ctx) {

        ExpressionNode expressionNode = (ExpressionNode) visitAttrJinjaExpr(ctx.attrJinjaExpr());
        return new JinjaExpressionNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                expressionNode
        );
    }

    @Override
    public TemplateNode visitAttrJinjaExpr(FlaskTemplateParser.AttrJinjaExprContext ctx) {
        return visitAttrJinjaExprContent(ctx.attrJinjaExprContent());
    }

    @Override
    public TemplateNode visitAttrJinjaExprContent(FlaskTemplateParser.AttrJinjaExprContentContext ctx) {
        return visitJinjaExpression(ctx.jinjaExpression());
    }

    @Override
    public TemplateNode visitJinjaExprNode(FlaskTemplateParser.JinjaExprNodeContext ctx) {
        if (ctx.jinjaExpression() == null || ctx.jinjaExpression().isEmpty()) {
            return null;
        }
        ExpressionNode expression = (ExpressionNode) visit(ctx.jinjaExpression(0));
        return new JinjaExpressionNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                expression
        );
    }

    @Override
    public TemplateNode visitJinjaExpression(FlaskTemplateParser.JinjaExpressionContext ctx) {

        return visit(ctx.expression());
    }

    @Override
    public TemplateNode visitExpressionRoot(FlaskTemplateParser.ExpressionRootContext ctx) {
        return visit(ctx.logicalOrExpression());
    }

    @Override
    public TemplateNode visitLogicalOrExpression(FlaskTemplateParser.LogicalOrExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitLogicalAndExpression(FlaskTemplateParser.LogicalAndExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitEqualityExpression(FlaskTemplateParser.EqualityExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitComparisonExpression(FlaskTemplateParser.ComparisonExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitAdditiveExpression(FlaskTemplateParser.AdditiveExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitMultiplicativeExpression(FlaskTemplateParser.MultiplicativeExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitUnaryOp(FlaskTemplateParser.UnaryOpContext ctx) {
        return new UnaryExpressionNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.getChild(0).getText(),
                (ExpressionNode) visit(ctx.unaryExpression())
        );
    }

    @Override
    public TemplateNode visitUnaryBase(FlaskTemplateParser.UnaryBaseContext ctx) {
        return visit(ctx.primaryExpression());
    }

    @Override
    public TemplateNode visitPrimary(FlaskTemplateParser.PrimaryContext ctx) {
        ExpressionNode expr = (ExpressionNode) visit(ctx.atom());
        return applyExprPostfixes(expr, ctx.postfix());
    }

    @Override
    public TemplateNode visitParenExpr(FlaskTemplateParser.ParenExprContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public TemplateNode visitIdentifierExpr(FlaskTemplateParser.IdentifierExprContext ctx) {

        return new VariableNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.EXPR_ID().getText()
        );
    }



    @Override
    public TemplateNode visitStringExpr(FlaskTemplateParser.StringExprContext ctx) {
        String raw = ctx.EXPR_STRING().getText();

        // إزالة علامات الاقتباس
        String value = raw.substring(1, raw.length() - 1);

        return new StringLiteralNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                value
        );
    }


    @Override
    public TemplateNode visitNumberExpr(FlaskTemplateParser.NumberExprContext ctx) {

        String text = ctx.EXPR_NUMBER().getText();
        Number value;

        if (text.contains(".")) {
            value = Double.parseDouble(text);
        } else {
            value = Integer.parseInt(text);
        }

        return new NumberLiteralNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                value
        );
    }


    @Override
    public TemplateNode visitTrueExpr(FlaskTemplateParser.TrueExprContext ctx) {
        return new BooleanLiteralNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                true
        );
    }

    @Override
    public TemplateNode visitFalseExpr(FlaskTemplateParser.FalseExprContext ctx) {
        return new BooleanLiteralNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                false
        );
    }


    @Override
    public TemplateNode visitNoneExpr(FlaskTemplateParser.NoneExprContext ctx) {
        return new NoneLiteralNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine()
        );
    }


    @Override
    public TemplateNode visitListExpr(FlaskTemplateParser.ListExprContext ctx) {

        ListExpressionNode node =
                new ListExpressionNode(ctx.start.getLine(), ctx.start.getCharPositionInLine());

        if (ctx.expressionList() != null) {
            node.addAllElement(visitExprListList((FlaskTemplateParser.ExprListContext) ctx.expressionList()));
        }

        return node;
    }

    public List<ExpressionNode> visitExprListList(FlaskTemplateParser.ExprListContext ctx) {
        List<ExpressionNode> list = new ArrayList<>();
        if (ctx == null) return list;

        for (var node : ctx.expression()){
            list.add((ExpressionNode) visit(node));
        }
        return list;
    }

    @Override
    public TemplateNode visitDictExpr(FlaskTemplateParser.DictExprContext ctx) {
        if (ctx.dictPairList() == null) {
            return new DictExpressionNode(ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return visit(ctx.dictPairList());
    }

    @Override
    public TemplateNode visitDictPairListNode(FlaskTemplateParser.DictPairListNodeContext ctx) {

        DictExpressionNode node = new DictExpressionNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine()
        );

        if (ctx.dictPair() != null) {
            for (FlaskTemplateParser.DictPairContext pairCtx
                    : ctx.dictPair()) {

                FlaskTemplateParser.DictPairNodeContext dictPair =
                        (FlaskTemplateParser.DictPairNodeContext) pairCtx;
                ExpressionNode key = (ExpressionNode) visit(dictPair.expression(0));
                ExpressionNode value = (ExpressionNode) visit(dictPair.expression(1));
                node.putPair(key, value);
            }
        }

        return node;
    }

    //=========================================================================================
    // Block expressions {% %}
    //=========================================================================================

    @Override
    public TemplateNode visitBlockExpression(FlaskTemplateParser.BlockExpressionContext ctx) {
        return visit(ctx.blockLogicalOrExpression());
    }

    @Override
    public TemplateNode visitBlockLogicalOrExpression(FlaskTemplateParser.BlockLogicalOrExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitBlockLogicalAndExpression(FlaskTemplateParser.BlockLogicalAndExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitBlockEqualityExpression(FlaskTemplateParser.BlockEqualityExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitBlockComparisonExpression(FlaskTemplateParser.BlockComparisonExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitBlockAdditiveExpression(FlaskTemplateParser.BlockAdditiveExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitBlockMultiplicativeExpression(FlaskTemplateParser.BlockMultiplicativeExpressionContext ctx) {
        return foldBinary(ctx);
    }

    @Override
    public TemplateNode visitBlockUnaryOp(FlaskTemplateParser.BlockUnaryOpContext ctx) {
        return new UnaryExpressionNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.getChild(0).getText(),
                (ExpressionNode) visit(ctx.blockUnaryExpression())
        );
    }

    @Override
    public TemplateNode visitBlockUnaryBase(FlaskTemplateParser.BlockUnaryBaseContext ctx) {
        return visit(ctx.blockPrimaryExpression());
    }

    @Override
    public TemplateNode visitBlockPrimary(FlaskTemplateParser.BlockPrimaryContext ctx) {
        ExpressionNode expr = (ExpressionNode) visit(ctx.blockAtom());
        return applyBlockPostfixes(expr, ctx.blockPostfix());
    }

    @Override
    public TemplateNode visitBlockParenExpr(FlaskTemplateParser.BlockParenExprContext ctx) {
        return visit(ctx.blockExpression());
    }

    @Override
    public TemplateNode visitBlockIdentifier(FlaskTemplateParser.BlockIdentifierContext ctx) {
        return new VariableNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), ctx.BLOCK_ID().getText());
    }

    @Override
    public TemplateNode visitBlockStringLiteral(FlaskTemplateParser.BlockStringLiteralContext ctx) {
        String raw = ctx.BLOCK_STRING().getText();
        String value = raw.substring(1, raw.length() - 1);
        return new StringLiteralNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), value);
    }

    @Override
    public TemplateNode visitBlockNumberLiteral(FlaskTemplateParser.BlockNumberLiteralContext ctx) {
        String text = ctx.BLOCK_NUMBER().getText();
        Number value = text.contains(".") ? Double.parseDouble(text) : Integer.parseInt(text);
        return new NumberLiteralNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), value);
    }

    @Override
    public TemplateNode visitBlockTrueLiteral(FlaskTemplateParser.BlockTrueLiteralContext ctx) {
        return new BooleanLiteralNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), true);
    }

    @Override
    public TemplateNode visitBlockFalseLiteral(FlaskTemplateParser.BlockFalseLiteralContext ctx) {
        return new BooleanLiteralNode(ctx.start.getLine(), ctx.start.getCharPositionInLine(), false);
    }

    @Override
    public TemplateNode visitBlockNoneLiteral(FlaskTemplateParser.BlockNoneLiteralContext ctx) {
        return new NoneLiteralNode(ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public TemplateNode visitBlockListLiteral(FlaskTemplateParser.BlockListLiteralContext ctx) {
        ListExpressionNode node = new ListExpressionNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine()
        );

        if (ctx.blockExpressionList() != null) {
            for (FlaskTemplateParser.BlockExpressionContext exprCtx : ctx.blockExpressionList().blockExpression()) {
                node.addElement((ExpressionNode) visit(exprCtx));
            }
        }

        return node;
    }


    @Override
    public TemplateNode visitBlockDictLiteral(FlaskTemplateParser.BlockDictLiteralContext ctx) {
        DictExpressionNode node = new DictExpressionNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine()
        );

        if (ctx.blockDictPairList() != null) {
            for (FlaskTemplateParser.BlockDictPairContext pairCtx : ctx.blockDictPairList().blockDictPair()) {
                ExpressionNode key = (ExpressionNode) visit(pairCtx.blockExpression(0));
                ExpressionNode value = (ExpressionNode) visit(pairCtx.blockExpression(1));
                node.putPair(key, value);
            }
        }

        return node;
    }

    //=========================================================================================
    // CSS
    //=========================================================================================

    @Override
    public TemplateNode visitCssContent(FlaskTemplateParser.CssContentContext ctx) {
        return visitStyleWithAttributes((FlaskTemplateParser.StyleWithAttributesContext) ctx.cssStyle());
    }

    @Override
    public TemplateNode visitStyleWithAttributes(FlaskTemplateParser.StyleWithAttributesContext ctx) {
        CSSStyleNode cssStyleNode = new CSSStyleNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine()
        );

        // يجب التحقق من null
        if (ctx.cssTagAttributes() != null) {
            cssStyleNode.addAttributes(getCssTagAttributes(ctx.cssTagAttributes()));
        }

        if (ctx.cssStyleContent() != null) {
            cssStyleNode.addRules(getCssStyleContent(ctx.cssStyleContent()));
        }

        return cssStyleNode; // يجب إرجاع cssStyleNode بدلاً من super
    }

    public List<CSSAttributeNode> getCssTagAttributes(FlaskTemplateParser.CssTagAttributesContext ctx) {
        List<CSSAttributeNode> list = new ArrayList<>();

        if (ctx == null) return list;

        try {
            for (var node : ctx.cssTagAttribute()) {
                CSSAttributeNode attr = (CSSAttributeNode) visitCssTagAttrNode(
                        (FlaskTemplateParser.CssTagAttrNodeContext) node
                );
                if (attr != null) {
                    list.add(attr);
                }
            }
        } catch (Exception e) {
            // تسجيل الخطأ مع الاستمرار
            System.err.println("Error processing CSS attributes: " + e.getMessage());
        }

        return list;
    }

    @Override
    public TemplateNode visitCssTagAttrNode(FlaskTemplateParser.CssTagAttrNodeContext ctx) {
        CSSAttributeNode cssAttributeNode = new CSSAttributeNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.CSS_TAG_ATTR().getText()
        );
        cssAttributeNode.setValue(ctx.CSS_TAG_STRING().getText());
        return cssAttributeNode;
    }


    public List<CSSRuleNode> getCssStyleContent(FlaskTemplateParser.CssStyleContentContext ctx) {
        List<CSSRuleNode> list = new ArrayList<>();

        for (var node : ctx.cssRule()){
            list.add((CSSRuleNode) visitCssRuleNode((FlaskTemplateParser.CssRuleNodeContext) node));
        }
        return list;
    }

    @Override
    public TemplateNode visitCssRuleNode(FlaskTemplateParser.CssRuleNodeContext ctx) {

        CSSRuleNode cssRuleNode = new CSSRuleNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine()
        );

        cssRuleNode.addAllSelectors(getCssSelectorExpr((FlaskTemplateParser.CssSelectorExprContext) ctx.cssSelectors()));
        cssRuleNode.addAllDeclarations( getCssDeclarationExpr((FlaskTemplateParser.CssDeclarationListContext) ctx.cssDeclarations()));
        return cssRuleNode;
    }

    public List<CSSSelectorNode> getCssSelectorExpr(FlaskTemplateParser.CssSelectorExprContext ctx) {

        List<CSSSelectorNode> list = new ArrayList<>();

        for (var node : ctx.cssSelector()){
            list.add((CSSSelectorNode) visitCssSelectorNode((FlaskTemplateParser.CssSelectorNodeContext) node));
        }
        return list;
    }

    @Override
    public TemplateNode visitCssSelectorNode(FlaskTemplateParser.CssSelectorNodeContext ctx) {

        return new CSSSelectorNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.CSS_CONTENT().toString()
        );
    }

    public List<CSSDeclarationNode> getCssDeclarationExpr(FlaskTemplateParser.CssDeclarationListContext ctx) {
        List<CSSDeclarationNode> list = new ArrayList<>();

        for (var node : ctx.cssDeclaration()){
            list.add((CSSDeclarationNode) visitCssDeclarationNode((FlaskTemplateParser.CssDeclarationNodeContext) node));
        }
        return list;
    }



    @Override
    public TemplateNode visitCssDeclarationNode(FlaskTemplateParser.CssDeclarationNodeContext ctx) {
        CSSDeclarationNode cssDeclarationNode = new CSSDeclarationNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.CSS_PROPERTY().getText()
        );

        if (ctx.cssValues() != null) {
            cssDeclarationNode.addAllValues(
                    getCssValueList((FlaskTemplateParser.CssValueListContext) ctx.cssValues())
            );
        }

        return cssDeclarationNode; // إرجاع العقدة بدلاً من super
    }

    public List<CSSValueNode> getCssValueList(FlaskTemplateParser.CssValueListContext ctx) {
        List<CSSValueNode> list = new ArrayList<>();

        if (ctx == null) return list;

        for (var node : ctx.cssValue()) {
            CSSValueNode valueNode = (CSSValueNode) visit(node);
            if (valueNode != null) {
                list.add(valueNode);
            }
        }
        return list;
    }

    @Override
    public TemplateNode visitCssStringValue(FlaskTemplateParser.CssStringValueContext ctx) {

        return new CSSStringValueNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.CSS_STRING().getText()
        );
    }

    @Override
    public TemplateNode visitCssNumericValue(FlaskTemplateParser.CssNumericValueContext ctx) {
        return new CSSNumericValueNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.CSS_NUMERIC().getText()
        );
    }

    @Override
    public TemplateNode visitCssColorValue(FlaskTemplateParser.CssColorValueContext ctx) {
        return new CSSColorValueNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.CSS_COLOR().getText()
        );
    }

    @Override
    public TemplateNode visitCssKeywordValue(FlaskTemplateParser.CssKeywordValueContext ctx) {
        return new CSSKeywordValueNode(
                ctx.start.getLine(),
                ctx.start.getCharPositionInLine(),
                ctx.CSS_KEYWORD().getText()
        );
    }

    //=========================================================================================
    // Helpers
    //=========================================================================================

    private ExpressionNode foldBinary(ParserRuleContext ctx) {
        if (ctx.children == null || ctx.children.isEmpty()) {
            return null;
        }
        ExpressionNode acc = (ExpressionNode) visit(ctx.children.get(0));
        for (int i = 1; i + 1 < ctx.children.size(); i += 2) {
            ParseTree operatorNode = ctx.children.get(i);
            String operator = operatorNode.getText();
            ExpressionNode right = (ExpressionNode) visit(ctx.children.get(i + 1));
            int line = ctx.start.getLine();
            int column = ctx.start.getCharPositionInLine();
            if (operatorNode instanceof TerminalNode terminal) {
                line = terminal.getSymbol().getLine();
                column = terminal.getSymbol().getCharPositionInLine();
            }
            acc = new BinaryExpressionNode(line, column, operator, acc, right);
        }
        return acc;
    }

    private ExpressionNode applyExprPostfixes(ExpressionNode base, List<FlaskTemplateParser.PostfixContext> postfixes) {
        ExpressionNode expr = base;
        if (postfixes == null) return expr;
        for (FlaskTemplateParser.PostfixContext postfix : postfixes) {
            if (postfix instanceof FlaskTemplateParser.FilterExprContext filterCtx) {
                FilterExpressionNode filter = new FilterExpressionNode(
                        filterCtx.start.getLine(),
                        filterCtx.start.getCharPositionInLine(),
                        expr,
                        filterCtx.EXPR_ID().getText()
                );
                addFilterArguments(filter, filterCtx.argumentList());
                expr = filter;
            } else if (postfix instanceof FlaskTemplateParser.CallExprContext callCtx) {
                CallExpressionNode call = new CallExpressionNode(
                        callCtx.start.getLine(),
                        callCtx.start.getCharPositionInLine(),
                        expr
                );
                addCallArguments(call, callCtx.argumentList());
                expr = call;
            } else if (postfix instanceof FlaskTemplateParser.MemberOpContext memberCtx) {
                expr = new AttributeAccessNode(
                        memberCtx.start.getLine(),
                        memberCtx.start.getCharPositionInLine(),
                        expr,
                        memberCtx.EXPR_ID().getText()
                );
            }
        }
        return expr;
    }

    private ExpressionNode applyBlockPostfixes(ExpressionNode base, List<FlaskTemplateParser.BlockPostfixContext> postfixes) {
        ExpressionNode expr = base;
        if (postfixes == null) return expr;
        for (FlaskTemplateParser.BlockPostfixContext postfix : postfixes) {
            if (postfix instanceof FlaskTemplateParser.BlockFilterOpContext filterCtx) {
                FilterExpressionNode filter = new FilterExpressionNode(
                        filterCtx.start.getLine(),
                        filterCtx.start.getCharPositionInLine(),
                        expr,
                        filterCtx.BLOCK_ID().getText()
                );
                addBlockCallOrFilterArgs(filter, null, filterCtx.blockArgumentList());
                expr = filter;
            } else if (postfix instanceof FlaskTemplateParser.BlockCallOpContext callCtx) {
                CallExpressionNode call = new CallExpressionNode(
                        callCtx.start.getLine(),
                        callCtx.start.getCharPositionInLine(),
                        expr
                );
                addBlockCallOrFilterArgs(null, call, callCtx.blockArgumentList());
                expr = call;
            } else if (postfix instanceof FlaskTemplateParser.BlockMemberOpContext memberCtx) {
                expr = new AttributeAccessNode(
                        memberCtx.start.getLine(),
                        memberCtx.start.getCharPositionInLine(),
                        expr,
                        memberCtx.BLOCK_ID().getText()
                );
            }
        }
        return expr;
    }

    private void addCallArguments(CallExpressionNode call, FlaskTemplateParser.ArgumentListContext listCtx) {
        if (listCtx == null) return;
        FlaskTemplateParser.ArgListContext args = (FlaskTemplateParser.ArgListContext) listCtx;
        for (FlaskTemplateParser.ArgumentContext arg : args.argument()) {
            if (arg instanceof FlaskTemplateParser.KeywordArgContext keyword) {
                call.addKeywordArgument(
                        keyword.EXPR_ID().getText(),
                        (ExpressionNode) visit(keyword.expression())
                );
            } else if (arg instanceof FlaskTemplateParser.PositionalArgContext positional) {
                call.addArgument((ExpressionNode) visit(positional.expression()));
            }
        }
    }

    private void addFilterArguments(FilterExpressionNode filter, FlaskTemplateParser.ArgumentListContext listCtx) {
        if (listCtx == null) return;
        FlaskTemplateParser.ArgListContext args = (FlaskTemplateParser.ArgListContext) listCtx;
        for (FlaskTemplateParser.ArgumentContext arg : args.argument()) {
            if (arg instanceof FlaskTemplateParser.KeywordArgContext keyword) {
                filter.addArgument((ExpressionNode) visit(keyword.expression()));
            } else if (arg instanceof FlaskTemplateParser.PositionalArgContext positional) {
                filter.addArgument((ExpressionNode) visit(positional.expression()));
            }
        }
    }

    private void addBlockCallOrFilterArgs(FilterExpressionNode filter,
                                          CallExpressionNode call,
                                          FlaskTemplateParser.BlockArgumentListContext listCtx) {
        if (listCtx == null) return;
        for (FlaskTemplateParser.BlockArgumentContext arg : listCtx.blockArgument()) {
            if (arg instanceof FlaskTemplateParser.BlockKeywordArgContext keyword) {
                ExpressionNode value = (ExpressionNode) visit(keyword.blockExpression());
                if (call != null) {
                    call.addKeywordArgument(keyword.BLOCK_ID().getText(), value);
                } else if (filter != null) {
                    filter.addArgument(value);
                }
            } else if (arg instanceof FlaskTemplateParser.BlockPositionalArgContext positional) {
                ExpressionNode value = (ExpressionNode) visit(positional.blockExpression());
                if (call != null) {
                    call.addArgument(value);
                } else if (filter != null) {
                    filter.addArgument(value);
                }
            }
        }
    }
}
