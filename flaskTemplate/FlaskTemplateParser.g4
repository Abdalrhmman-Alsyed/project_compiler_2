parser grammar FlaskTemplateParser;

options {
    tokenVocab = FlaskLexer;
}

@header { package gen; }

template
    : doctype? html NEWLINE* EOF #templateRoot
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
    : ATTR_JINJA_BLOCK_START attrBlockStatement BLOCK_END
    ;

attrBlockStatement
    : BLOCK_SET BLOCK_ID BLOCK_EQ blockExpression     # attrSetBlock
    | BLOCK_INCLUDE BLOCK_STRING                      # attrIncludeBlock
    | BLOCK_ID (BLOCK_EQ blockExpression)?            # attrGenericBlock
    ;

htmlText
    : HTML_TEXT+
    ;

// ============ JINJA2 BLOCKS ============
// Each construct consumes its opening {% %}, body, and matching end tag
// so the AST can nest content instead of leaving bodies as siblings.

jinjaBlock
    : ifBlock
    | forBlock
    | namedBlock
    | withBlock
    | macroBlock
    | setBlock
    | includeBlock
    | importBlock
    | fromImportBlock
    | extendsBlock
    | genericBlock
    ;

ifBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_IF blockExpression BLOCK_END
      templateContent
      elifBlock*
      elseBlock?
      TEMPLATE_JINJA_BLOCK_START BLOCK_ENDIF BLOCK_END
      # ifStart
    ;

elifBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_ELIF blockExpression BLOCK_END
      templateContent
    ;

elseBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_ELSE BLOCK_END
      templateContent
    ;

forBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_FOR BLOCK_ID BLOCK_IN blockExpression BLOCK_END
      templateContent
      elseBlock?
      TEMPLATE_JINJA_BLOCK_START BLOCK_ENDFOR BLOCK_END
      # forStart
    ;

namedBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_BLOCK BLOCK_ID BLOCK_END
      templateContent
      TEMPLATE_JINJA_BLOCK_START BLOCK_ENDBLOCK BLOCK_END
      # blockStart
    ;

withBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_WITH blockExpression BLOCK_END
      templateContent
      TEMPLATE_JINJA_BLOCK_START BLOCK_ENDWITH BLOCK_END
      # withStart
    ;

macroBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_MACRO BLOCK_ID BLOCK_LPAREN macroParameters? BLOCK_RPAREN BLOCK_END
      templateContent
      TEMPLATE_JINJA_BLOCK_START BLOCK_ENDMACRO BLOCK_END
    ;

setBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_SET BLOCK_ID BLOCK_EQ blockExpression BLOCK_END
    ;

includeBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_INCLUDE BLOCK_STRING BLOCK_END
    ;

importBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_IMPORT BLOCK_STRING (BLOCK_AS BLOCK_ID)? BLOCK_END
    ;

fromImportBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_FROM BLOCK_STRING BLOCK_IMPORT importList BLOCK_END
    ;

extendsBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_EXTENDS BLOCK_STRING BLOCK_END
    ;

genericBlock
    : TEMPLATE_JINJA_BLOCK_START BLOCK_ID (BLOCK_EQ blockExpression)? BLOCK_END
    ;

macroParameters
    : BLOCK_ID (BLOCK_COMMA BLOCK_ID)*
    ;

importList: BLOCK_ID (BLOCK_COMMA BLOCK_ID)*;

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
    : BLOCK_PIPE BLOCK_ID blockArgumentList? #blockFilterOp
    | BLOCK_LPAREN blockArgumentList? BLOCK_RPAREN #blockCallOp
    | BLOCK_DOT BLOCK_ID #blockMemberOp
    ;

blockArgumentList
    : blockArgument (BLOCK_COMMA blockArgument)*
    ;

blockArgument
    : BLOCK_ID BLOCK_EQ blockExpression  #blockKeywordArg
    | blockExpression                    #blockPositionalArg
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

// ============ JINJA2 EXPRESSIONS ============
jinjaExpr
    : TEMPLATE_JINJA_EXPR_START jinjaExpression* EXPR_END #jinjaExprNode
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
    : EXPR_PIPE EXPR_ID argumentList?      # filterExpr
    | EXPR_LPAREN argumentList? EXPR_RPAREN # callExpr
    | EXPR_DOT EXPR_ID                      # memberOp
    ;

argumentList
    : argument (EXPR_COMMA argument)* #argList
    ;

argument
    : EXPR_ID EXPR_EQ expression  #keywordArg
    | expression                  #positionalArg
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
