import re

with open("src/semantic/JinjaAstSemanticAnalyzer.java", "r") as f:
    content = f.read()

import_statement = "import ast.template.jinja.expressions.literals.StringLiteralNode;\n"
if "import ast.template.jinja.expressions.literals.StringLiteralNode;" not in content:
    content = content.replace("import ast.template.jinja.expressions.*;\n", 
                              "import ast.template.jinja.expressions.*;\n" + import_statement)

method_code = """
    @Override
    public Void visit(CallExpressionNode node) {
        if (node.getCallee() instanceof VariableNode varNode) {
            if ("url_for".equals(varNode.getName())) {
                boolean isStatic = false;
                if (node.getArgumentCount() > 0 && node.getArgument(0) instanceof StringLiteralNode) {
                    StringLiteralNode str = (StringLiteralNode) node.getArgument(0);
                    if ("static".equals(stripQuotes(str.getValue()))) {
                        isStatic = true;
                    }
                }
                if (isStatic) {
                    for (int i = 0; i < node.getArgumentCount(); i++) {
                        if ("filename".equals(node.getKeywordName(i)) && node.getArgument(i) instanceof StringLiteralNode) {
                            StringLiteralNode str = (StringLiteralNode) node.getArgument(i);
                            String filename = stripQuotes(str.getValue());
                            File targetFile = new File(new File(templateDir).getParentFile(), "static/" + filename);
                            if (!targetFile.exists()) {
                                error("url_for('static', filename='" + filename + "') references a file that does not exist: " + targetFile.getPath(), node.getLine(), node.getColumn());
                            }
                        }
                    }
                }
            }
        }
        return super.visit(node);
    }
"""

if "public Void visit(CallExpressionNode node)" not in content:
    # insert before the first private method (checkName)
    content = content.replace("private void checkName(String name, int line, int col) {", 
                              method_code + "\n    private void checkName(String name, int line, int col) {")

with open("src/semantic/JinjaAstSemanticAnalyzer.java", "w") as f:
    f.write(content)
