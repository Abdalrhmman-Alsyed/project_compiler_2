lexer grammar FlaskPythonLexer;

@header{
package gen;
}

@lexer::members {
    private java.util.Stack<Integer> indentations = new java.util.Stack<>();
    private java.util.LinkedList<Token> pendingTokens = new java.util.LinkedList<>();

    private int parenLevel = 0;
    private int bracketLevel = 0;
    private int braceLevel = 0;

    private boolean calculatedNextIndent = false;
    private int nextIndentation = 0;

    {
        indentations.push(0);
    }

    private boolean isInsideBrackets() {
        return (parenLevel + bracketLevel + braceLevel) > 0;
    }

    private void enterBracket() { parenLevel++; }
    private void exitBracket() { if (parenLevel > 0) parenLevel--; }

    private void enterSquareBracket() { bracketLevel++; }
    private void exitSquareBracket() { if (bracketLevel > 0) bracketLevel--; }

    private void enterBrace() { braceLevel++; }
    private void exitBrace() { if (braceLevel > 0) braceLevel--; }

    @Override
    public Token nextToken() {
        if (!pendingTokens.isEmpty()) {
            return pendingTokens.poll();
        }

        Token token = super.nextToken();

        switch (token.getType()) {
            case LPAREN: enterBracket(); break;
            case RPAREN: exitBracket(); break;
            case LBRACK: enterSquareBracket(); break;
            case RBRACK: exitSquareBracket(); break;
            case LBRACE: enterBrace(); break;
            case RBRACE: exitBrace(); break;
            case NEWLINE: return handleNewline(token);
            case EOF: return handleEof(token);
        }

        return token;
    }

    private Token handleNewline(Token newlineToken) {
        pendingTokens.add(newlineToken);

        if (isInsideBrackets()) {
            calculatedNextIndent = false;
            return pendingTokens.poll();
        }

        nextIndentation = getNextIndentation();
        int currentIndent = indentations.peek();

        if (nextIndentation > currentIndent) {
            indentations.push(nextIndentation);
            pendingTokens.add(createIndentToken(newlineToken));
        }
        else if (nextIndentation < currentIndent) {
            while (nextIndentation < indentations.peek()) {
                indentations.pop();
                pendingTokens.add(createDedentToken(newlineToken));
            }
        }

        calculatedNextIndent = false;

        return pendingTokens.poll();
    }

    private int getNextIndentation() {
        if (calculatedNextIndent) {
            return nextIndentation;
        }

        // Look ahead WITHOUT consuming: release() alone does not rewind the
        // stream, so the index must be restored explicitly. Consuming here
        // would bypass the lexer's line counter and shift every reported line
        // number by the number of blank lines seen so far.
        int startIndex = _input.index();
        int mark = _input.mark();
        int indent = 0;

        while (true) {
            int c = _input.LA(1);

            if (c == CharStream.EOF) {
                break;
            }

            if (c == ' ') {
                indent++;
                _input.consume();
            }
            else if (c == '\t') {
                // كل tab = 4 مسافات (معيار Python)
                indent = ((indent / 4) + 1) * 4;
                _input.consume();
            }
            else if (c == '#') {
                // تخطي التعليق
                while (_input.LA(1) != '\n' &&
                       _input.LA(1) != '\r' &&
                       _input.LA(1) != CharStream.EOF) {
                    _input.consume();
                }
            }
            else if (c == '\r' || c == '\n') {
                // سطر فارغ، ابدأ من الصفر
                skipNewline();
                indent = 0;
                continue;
            }
            else {
                break;
            }
        }

        _input.seek(startIndex);
        _input.release(mark);
        calculatedNextIndent = true;
        nextIndentation = indent;
        return indent;
    }

    private void skipNewline() {
        int c = _input.LA(1);
        if (c == '\r') {
            _input.consume();
            if (_input.LA(1) == '\n') {
                _input.consume();
            }
        } else if (c == '\n') {
            _input.consume();
        }
    }

    private Token handleEof(Token eofToken) {
        while (indentations.peek() > 0) {
            indentations.pop();
            pendingTokens.add(createDedentToken(eofToken));
        }

        pendingTokens.add(eofToken);
        return pendingTokens.poll();
    }

    private CommonToken createIndentToken(Token referenceToken) {
        CommonToken token = new CommonToken(INDENT);
        token.setText("INDENT");
        token.setLine(referenceToken.getLine());
        token.setCharPositionInLine(0);
        token.setStartIndex(referenceToken.getStartIndex());
        token.setStopIndex(referenceToken.getStopIndex());
        return token;
    }

    private CommonToken createDedentToken(Token referenceToken) {
        CommonToken token = new CommonToken(DEDENT);
        token.setText("DEDENT");
        token.setLine(referenceToken.getLine());
        token.setCharPositionInLine(0);
        token.setStartIndex(referenceToken.getStartIndex());
        token.setStopIndex(referenceToken.getStopIndex());
        return token;
    }

    public String getDebugInfo() {
        return String.format("IndentStack: %s, Levels: ()=%d []=%d {}=%d",
            indentations.toString(), parenLevel, bracketLevel, braceLevel);
    }
}

tokens { INDENT, DEDENT }


// KeyWord
DEF      : 'def' ;
IF       : 'if' ;
ELIF     : 'elif' ;
ELSE     : 'else' ;
FOR      : 'for' ;
WHILE    : 'while' ;
RETURN   : 'return' ;
BREAK    : 'break' ;
CONTINUE : 'continue' ;
PASS     : 'pass' ;
GLOBAL   : 'global' ;
WITH     : 'with' ;
AS       : 'as' ;

FROM     : 'from' ;
IMPORT   : 'import' ;
CLASS    : 'class' ;
TRY      : 'try' ;
EXCEPT   : 'except' ;
FINALLY  : 'finally' ;
RAISE    : 'raise' ;
ASSERT   : 'assert' ;


// Logical Operators
AND      : 'and';
OR       : 'or';
IN       : 'in';
//ISNOT   : 'is not';
IS      : 'is';
NOT     : 'not';
LAMBDA   : 'lambda';


// Bool Values
TRUE     : 'True' ;
FALSE    : 'False' ;
NONE     : 'None' ;

// Punctuation and decorators
AT       : '@';
DOT      : '.' ;
COMMA    : ',' ;
COLON    : ':' ;
SEMICOLON: ';' ;
ELLIPSIS : '...' ;
ARROW    : '->' ;


//  Double-Character Operators
POWER      : '**'; // value = 2 ** 2
FLOORDIV   : '//'; //  value = 2 // 2
WALRUS     : ':='; // if (n := len(data)) > 10







//String
STRING
    : '"'  ( ~["\\\r\n] | '\\' . )* '"'
    | '\'' ( ~['\\\r\n] | '\\' . )* '\''
    | '"""' ( . | '\r' | '\n' )*? '"""'
    | '\'\'\'' ( . | '\r' | '\n' )*? '\'\'\''
    | 'f'? '"' (~["\r\n])* '"'   // لا يحلل ما داخل {}
    ;

// Numbers
FLOAT    : [0-9]+ '.' [0-9]+
         | '.' [0-9]+ ;
INT      : [0-9]+
         | '0x' [0-9a-fA-F]+ // Hex
         | '0o' [0-7]+  // Oct
         | '0b' [01]+ ; // Binary

// Assignment and Arithmetic Operators
EQ       : '=' ;
PLUS     : '+' ;
MINUS    : '-' ;
STAR     : '*' ;
SLASH    : '/' ;
PERCENT  : '%' ;
STAREQ   : '*=';
SLASHEQ  : '/=';
PLUSEQ   : '+=';
MINUSEQ  : '-=';
POWEREQ  : '**=' ;
FLOORDIVEQ: '//=' ;
BITANDEQ : '&=' ;
BITOREQ  : '|=' ;


// Comparison Operators
EQEQ     : '==' ;
GT       : '>' ;
LT       : '<' ;
NOTEQ    : '!=';
LTEQ: '<=';
GTEQ: '>=';
// Braces
LPAREN   : '(' ;
RPAREN   : ')' ;
LBRACK   : '[' ;
RBRACK   : ']' ;
LBRACE   : '{' ;
RBRACE   : '}' ;

LINE_CONTINUATION
    : '\\' '\r'? '\n' -> skip
    ;
// Identifiers ( app, Flask, next_id, allowed_file, __name__)
ID       : [a-zA-Z_][a-zA-Z_0-9]* ;

// NEWLINE
NEWLINE  : '\r'? '\n' ;

// Whitespace and Comments
WS       : [ \t]+ -> skip;
COMMENT  : '#' ~[\r\n]* -> skip;
