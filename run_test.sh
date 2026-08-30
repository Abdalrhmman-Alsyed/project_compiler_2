#!/bin/bash
export CLASSPATH="lib/*:bin"
antlr4 -o src/gen -listener -visitor -lib flaskTemplate flaskTemplate/FlaskJinjaLexer.g4
antlr4 -o src/gen -listener -visitor -lib flaskTemplate flaskTemplate/FlaskTemplateParser.g4
javac -d bin src/gen/FlaskJinjaLexer.java src/gen/FlaskTemplateParser.java src/TestSingle.java
java TestSingle
