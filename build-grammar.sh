#!/usr/bin/env bash
# Regenerates the ANTLR lexers/parsers into src/gen.
# ANTLR mirrors the input path under -o, so everything is generated into a
# temp dir and then flattened into src/gen.
set -euo pipefail
cd "$(dirname "$0")"

JAR=libs/antlr-4.13.2-complete.jar
OUT=src/gen
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$OUT"
cp flaskTemplate/*.g4 "$TMP/"

# Lexers first: the parsers need their .tokens files.
java -jar "$JAR" -o "$TMP" "$TMP/FlaskPythonLexer.g4"
java -jar "$JAR" -o "$TMP" "$TMP/FlaskJinjaLexer.g4"
java -jar "$JAR" -visitor -lib "$TMP" -o "$TMP" "$TMP/FlaskPythonParser.g4"
java -jar "$JAR" -visitor -lib "$TMP" -o "$TMP" "$TMP/FlaskTemplateParser.g4"

cp "$TMP"/*.java "$TMP"/*.tokens "$TMP"/*.interp "$OUT"/
echo "Generated into $OUT:"
ls "$OUT"/*.java | sed 's|.*/|  |'
