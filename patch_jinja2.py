with open("src/semantic/JinjaAstSemanticAnalyzer.java", "r") as f:
    content = f.read()

content = content.replace("public Void visit(CallExpressionNode node) {", 
                          "public Void visit(CallExpressionNode node) {\n        System.out.println(\"CALL: \" + node.getCallee() + \" ARGS: \" + node.getArguments() + \" KWARGS: \" + node.getKeywordNames());")

with open("src/semantic/JinjaAstSemanticAnalyzer.java", "w") as f:
    f.write(content)
