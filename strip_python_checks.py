import re

with open("src/semantic/PythonSemanticAnalyzer.java", "r") as f:
    content = f.read()

# Remove Check 4, 14, 16 from FunctionNode
content = re.sub(r'// فحص 4.*?visitedFunctions\.add\(n\.getName\(\)\);\n', '', content, flags=re.DOTALL)
content = re.sub(r'// فحص 14.*?n\.getColumn\(\)\);\n\s*\}\n', '', content, flags=re.DOTALL)
content = re.sub(r'// فحص 16.*?n\.getColumn\(\)\);\n\s*\}\n', '', content, flags=re.DOTALL)
content = re.sub(r'globalKind\.put\(n\.getName\(\), "function"\);\n\s*functionParamCount\.put\(n\.getName\(\), n\.getParameters\(\)\.size\(\)\);\n', '', content)

# Remove Check 12 from exitScope
content = re.sub(r'// فحص 12.*?n\.getColumn\(\)\);\n\s*\}\n\s*\}\n', '', content, flags=re.DOTALL)

# Remove Check 9 from ImportNode
content = re.sub(r'// فحص 9: استيراد مكرر.*?\n\s*\}\n\s*else\s*\{.*?\n\s*\}\n', '', content, flags=re.DOTALL)

# Remove Check 11 and 10 from AssignmentNode
content = re.sub(r'// فحص 11: إخفاء دالة مبنية مسبقاً.*?n\.getColumn\(\)\);\n\s*\}\n', '', content, flags=re.DOTALL)
content = re.sub(r'// فحص 10: الكتابة فوق اسم دالة.*?n\.getColumn\(\)\);\n\s*\}\n', '', content, flags=re.DOTALL)

# Remove Check 6 from GlobalNode
content = re.sub(r'// فحص 6: استخدام global بعد إسناد محلي.*?n\.getColumn\(\)\);\n\s*\}\n\s*currentGlobals\.add\(n\.getName\(\)\);\n', '', content, flags=re.DOTALL)

# Remove Check 17 and 18 from BinaryOpNode
content = re.sub(r'// فحص 17: مقارنة مع None.*?n\.getColumn\(\)\);\n\s*\}\n', '', content, flags=re.DOTALL)
content = re.sub(r'// فحص 18: مقارنة مع True.*?n\.getColumn\(\)\);\n\s*\}\n', '', content, flags=re.DOTALL)

# Remove Arity check from CallNode
content = re.sub(r'// فحص عدد المعاملات الممررة للدالة.*?n\.getColumn\(\)\);\n\s*\}\n\s*\}\n', '', content, flags=re.DOTALL)

# Rewrite checkNameResolution to remove Check 15
old_check = r'''    private void checkNameResolution\(String name, int line, int col\) \{
        if \(isKnown\(name\)\) return;

        if \(symbolTable\.isAssignedInFunctionScope\(currentScope, name\)\) \{
            warning\("تم استخدام الاسم '" \+ name \+ "' قبل إسناد قيمة له في النطاق المحلي",
                    line, col\);
        \} else if \(functionLocalNames\.containsKey\(name\) || \(currentScope\.resolve\(name\) != null && currentScope\.resolve\(name\)\.getScope\(\) != currentScope\)\) \{
            error\("المتغير '" \+ name \+ "' خارج النطاق \(Out of scope\)", line, col\);
        \} else if \(currentScope\.resolve\(name\) == null\) \{
            error\("المتغير '" \+ name \+ "' غير معرّف", line, col\);
        \}
    \}'''
new_check = '''    private void checkNameResolution(String name, int line, int col) {
        if (isKnown(name)) return;

        if (functionLocalNames.containsKey(name) || (currentScope.resolve(name) != null && currentScope.resolve(name).getScope() != currentScope)) {
            error("المتغير '" + name + "' خارج النطاق (Out of scope)", line, col);
        } else if (currentScope.resolve(name) == null) {
            error("المتغير '" + name + "' غير معرّف", line, col);
        }
    }'''
content = re.sub(old_check, new_check, content, flags=re.MULTILINE)

with open("src/semantic/PythonSemanticAnalyzer.java", "w") as f:
    f.write(content)
