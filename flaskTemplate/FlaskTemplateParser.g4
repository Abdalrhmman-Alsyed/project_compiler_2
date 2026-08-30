parser grammar FlaskTemplateParser;

options {
    tokenVocab = FlaskJinjaLexer;
}



// A page template wraps everything in <html>; a child template that begins
// with {% extends %} has no wrapper at all. Both are valid entry points.
template
    : doctype? (html | templateContent) NEWLINE* EOF #templateRoot
    ;


doctype: HTML_DOCTYPE;

html: HTML_TAG_OPEN HTML_ID htmlAttributes TAG_CLOSE templateContent TEMPLATE_END #htmlDocument
    ;



templateContent
    : contentItem*
    ;

contentItem
    : htmlElement   # htmlContent
    | jinjaBlock    # jinjaBlockContent
    | jinjaTag      # jinjaTagContent
    | jinjaExpr     # jinjaExprContent
    | htmlText      # htmlTextContent
    | cssStyle      # cssContent
    | NEWLINE       # newlineContent
    ;
htmlElement
    : openingTag (templateContent)? closingTag      # normalElement
    | voidTag                                     # voidElementTag
    | selfClosingTag                              # selfClosingElementTag
    ;


// ============ HTML ============
openingTag: HTML_TAG_OPEN HTML_ID htmlAttributes TAG_CLOSE #openingTagNode
          ;

closingTag: HTML_TAG_OPEN_SELF HTML_ID TAG_CLOSE #closingTagNode
          ;

selfClosingTag: HTML_TAG_OPEN HTML_ID htmlAttributes SELF_CLOSE_TAG #selfClosingTagNode;

voidTag: HTML_TAG_OPEN VOID_TAG htmlAttributes TAG_CLOSE #voidTagNode;

htmlAttributes
    : htmlAttribute*  #htmlAttributeList
    ;

htmlAttribute
    : HTML_ID HTML_EQ htmlAttributeValue     # attributeWithValue
    | HTML_BOOLEAN_ATTR                      # booleanAttribute
    ;

htmlAttributeValue
    : HTML_QUOTE attrValueContent? ATTR_VALUE_QUOTE # doubleQuotedValue
    ;

attrValueContent
    : attrValueItem+
    ;

attrValueItem
    : ATTR_VALUE_ID       # attrText
    | attrJinjaExpr       # attrJinjaExprItem
    | attrJinjaBlock      # attrJinjaBlockItem
    ;

attrJinjaExpr
    : ATTR_JINJA_EXPR_START attrJinjaExprContent EXPR_END
    ;

attrJinjaExprContent
    : jinjaExpression
    ;

attrJinjaBlock
    : ATTR_JINJA_BLOCK_START jinjaTagStatement BLOCK_END
    ;

htmlText
    : HTML_TEXT+
    ;

// ============ JINJA2: PAIRED BLOCKS ============
// Each block owns its body: the opening tag closes with %}, THEN the body
// follows as real content, and the matching end tag terminates it. Because
// {% endif %} / {% endfor %} / {% endblock %} match no contentItem
// alternative, templateContent stops there on its own.

jinjaBlock
    : ifBlock
    | forBlock
    | blockBlock
    | withBlock
    ;

ifBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_IF blockExpression BLOCK_END
      templateContent
      elifClause*
      elseClause?
      TEMPLATE_JINJA_BLOCK_START BLOCK_ENDIF BLOCK_END
    ;

elifClause
    : TEMPLATE_JINJA_BLOCK_START BLOCK_ELIF blockExpression BLOCK_END
      templateContent
    ;

elseClause
    : TEMPLATE_JINJA_BLOCK_START BLOCK_ELSE BLOCK_END
      templateContent
    ;

forBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_FOR BLOCK_ID BLOCK_IN blockExpression BLOCK_END
      templateContent
      elseClause?
      TEMPLATE_JINJA_BLOCK_START BLOCK_ENDFOR BLOCK_END
    ;

blockBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_BLOCK BLOCK_ID BLOCK_END
      templateContent
      TEMPLATE_JINJA_BLOCK_START BLOCK_ENDBLOCK BLOCK_ID? BLOCK_END
    ;

// {% with messages = get_flashed_messages() %} binds a name; blockExpression
// has no '=' operator, so the binding must be modelled here explicitly.
withBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_WITH (BLOCK_ID BLOCK_EQ)? blockExpression BLOCK_END
      templateContent
      TEMPLATE_JINJA_BLOCK_START BLOCK_ENDWITH BLOCK_END
    ;

// ============ JINJA2: STANDALONE TAGS ============
// Labels deliberately keep their old names so existing analyzers that walk
// the parse tree keep compiling unchanged.

jinjaTag
    : TEMPLATE_JINJA_BLOCK_START jinjaTagStatement BLOCK_END #jinjaTagNode
    ;

jinjaTagStatement
    : BLOCK_SET BLOCK_ID BLOCK_EQ blockExpression                     # setBlock
    | BLOCK_INCLUDE BLOCK_STRING                                      # includeBlock
    | BLOCK_IMPORT BLOCK_STRING (BLOCK_AS BLOCK_ID)?                  # importBlock
    | BLOCK_FROM BLOCK_STRING BLOCK_IMPORT importList                 # fromImportBlock
    | BLOCK_EXTENDS BLOCK_STRING                                      # extendsBlock
    | BLOCK_ID (BLOCK_EQ blockExpression)?                            # genericBlock
    ;


// ============ BLOCK EXPRESSIONS ============
blockExpression
    : blockLogicalOrExpression
    ;

blockLogicalOrExpression
    : blockLogicalAndExpression (BLOCK_OR blockLogicalAndExpression)*
    ;

blockLogicalAndExpression
    : blockEqualityExpression (BLOCK_AND blockEqualityExpression)*
    ;

blockEqualityExpression
    : blockComparisonExpression ((BLOCK_EQEQ | BLOCK_NEQ) blockComparisonExpression)*
    ;

blockComparisonExpression
    : blockAdditiveExpression ((BLOCK_LT | BLOCK_LTE | BLOCK_GT | BLOCK_GTE | BLOCK_IN | BLOCK_IS) blockAdditiveExpression)*
    ;

blockAdditiveExpression
    : blockMultiplicativeExpression ((BLOCK_PLUS | BLOCK_MINUS) blockMultiplicativeExpression)*
    ;

blockMultiplicativeExpression
    : blockUnaryExpression ((BLOCK_STAR | BLOCK_SLASH | BLOCK_PERCENT) blockUnaryExpression)*
    ;

blockUnaryExpression
    : (BLOCK_PLUS | BLOCK_MINUS | BLOCK_NOT) blockUnaryExpression #blockUnaryOp
    | blockPrimaryExpression                                      #blockUnaryBase
    ;

blockPrimaryExpression
    : blockAtom (blockPostfix)* #blockPrimary
    ;

blockAtom
    : BLOCK_LPAREN blockExpression BLOCK_RPAREN         #blockParenExpr
    | BLOCK_ID                                         #blockIdentifier
    | BLOCK_STRING                                     #blockStringLiteral
    | BLOCK_NUMBER                                     #blockNumberLiteral
    | BLOCK_TRUE                                       #blockTrueLiteral
    | BLOCK_FALSE                                      #blockFalseLiteral
    | BLOCK_NONE                                       #blockNoneLiteral
    | BLOCK_LBRACK blockExpressionList? BLOCK_RBRACK   #blockListLiteral
    | BLOCK_LBRACE blockDictPairList? BLOCK_RBRACE     #blockDictLiteral
    ;

blockPostfix
    : BLOCK_PIPE BLOCK_ID (BLOCK_LPAREN blockArgumentList? BLOCK_RPAREN)? #blockFilterOp
    | BLOCK_LPAREN blockArgumentList? BLOCK_RPAREN #blockCallOp
    | BLOCK_DOT BLOCK_ID #blockMemberOp
    | BLOCK_LBRACK blockSliceItem BLOCK_RBRACK #blockIndexOp
    ;

blockSliceItem
    : blockExpression #blockIndex
    | blockExpression? BLOCK_COLON blockExpression? (BLOCK_COLON blockExpression?)? #blockSlice
    ;

blockArgumentList
    : blockArgument (BLOCK_COMMA blockArgument)*
    ;

// Keep the name of a keyword argument in the parse tree.  It is semantic
// information (for example: url_for('detail', product_id=p.id)), not a read
// of a template variable.
blockArgument
    : BLOCK_ID BLOCK_EQ blockExpression #blockKeywordArgument
    | blockExpression                   #blockPositionalArgument
    ;

blockExpressionList
    : blockExpression (BLOCK_COMMA blockExpression)*
    ;

blockDictPairList
    : blockDictPair (BLOCK_COMMA blockDictPair)*
    ;

blockDictPair
    : blockExpression BLOCK_COLON blockExpression
    ;

importList: BLOCK_ID (BLOCK_COMMA BLOCK_ID)*;

// ============ JINJA2 EXPRESSIONS ============
jinjaExpr
    : TEMPLATE_JINJA_EXPR_START jinjaExpression EXPR_END #jinjaExprNode
    ;

jinjaExpression
    : expression
    ;

expression
    : logicalOrExpression #expressionRoot
    ;

logicalOrExpression
    : logicalAndExpression (EXPR_OR logicalAndExpression)*
    ;

logicalAndExpression
    : equalityExpression (EXPR_AND equalityExpression)*
    ;

equalityExpression
    : comparisonExpression ((EXPR_EQEQ | EXPR_NEQ) comparisonExpression)*
    ;

comparisonExpression
    : additiveExpression ((EXPR_LT | EXPR_LTE | EXPR_GT | EXPR_GTE | EXPR_IN | EXPR_IS) additiveExpression)*
    ;

additiveExpression
    : multiplicativeExpression ((EXPR_PLUS | EXPR_MINUS) multiplicativeExpression)*
    ;

multiplicativeExpression
    : unaryExpression ((EXPR_STAR | EXPR_SLASH | EXPR_PERCENT | EXPR_FLOORDIV) unaryExpression)*
    ;

unaryExpression
    : (EXPR_PLUS | EXPR_MINUS | EXPR_NOT) unaryExpression #unaryOp
    | primaryExpression                                   #unaryBase
    ;

primaryExpression
    : atom postfix* #primary
    ;

// A single atom may be followed by any number of property accesses, calls,
// and filters.  This models real Jinja expressions such as
// user.get_name().to_upper() | reverse without losing the intermediate nodes.
atom
    : EXPR_LPAREN expression EXPR_RPAREN                     # parenExpr
    | EXPR_ID                                                # identifierExpr
    | EXPR_STRING                                            # stringExpr
    | EXPR_NUMBER                                            # numberExpr
    | EXPR_TRUE                                              # trueExpr
    | EXPR_FALSE                                             # falseExpr
    | EXPR_NONE                                              # noneExpr
    | EXPR_LBRACK expressionList? EXPR_RBRACK                # listExpr
    | EXPR_LBRACE dictPairList? EXPR_RBRACE                  # dictExpr
    ;

postfix
    : EXPR_PIPE EXPR_ID (EXPR_LPAREN argumentList? EXPR_RPAREN)? #filterExpr
    | EXPR_LPAREN argumentList? EXPR_RPAREN #callExpr
    | EXPR_DOT EXPR_ID                      #memberOp
    | EXPR_LBRACK sliceItem EXPR_RBRACK     #indexOp
    ;

sliceItem
    : expression #index
    | expression? EXPR_COLON expression? (EXPR_COLON expression?)? #slice
    ;

// url_for('detail', product_id=p.id): 'product_id' is a parameter NAME, not a
// variable read. Modelling it separately stops name checks from flagging it.
argumentList
    : jinjaArgument (EXPR_COMMA jinjaArgument)* #argList
    ;

jinjaArgument
    : EXPR_ID EXPR_EQ expression   # keywordArgument
    | expression                   # positionalArgument
    ;

expressionList
    : expression (EXPR_COMMA expression)* #exprList
    ;

dictPairList
    : dictPair (EXPR_COMMA dictPair)* #dictPairListNode
    ;

dictPair
    : expression EXPR_COLON expression #dictPairNode
    ;

// ============ CSS ============
cssStyle
    : CSS_START cssTagAttributes? CSS_TAG_CLOSE cssStyleContent STYLE_TAG_END # styleWithAttributes
    ;

cssTagAttributes
    : cssTagAttribute*
    ;

cssTagAttribute
    : CSS_TAG_ATTR CSS_TAG_EQ CSS_TAG_STRING #cssTagAttrNode
    ;

cssStyleContent
    : cssRule*
    ;

cssRule
    : cssSelectors CSS_CONTENT* CSS_LBRACE cssDeclarations CSS_RBRACE #cssRuleNode
    ;

cssSelectors
    : cssSelector (CSS_COMMA cssSelector)* #cssSelectorExpr
    ;

cssSelector
    : CSS_CONTENT (CSS_DOT CSS_CONTENT)* #cssSelectorNode
    ;


cssDeclarations
    : cssDeclaration* #cssDeclarationList
    ;

cssDeclaration
    : CSS_PROPERTY CSS_COLON cssValues CSS_SEMICOLON #cssDeclarationNode
    ;

cssValues
    : cssValue (CSS_COMMA? cssValue)* #cssValueList
    ;

cssValue
    : CSS_STRING     #cssStringValue
    | CSS_NUMERIC    #cssNumericValue
    | CSS_COLOR      #cssColorValue
    | CSS_KEYWORD    #cssKeywordValue
    ;
