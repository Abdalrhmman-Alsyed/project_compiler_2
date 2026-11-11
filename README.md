# Flask App Compiler — Java & ANTLR4

A compiler that analyzes a full **Flask web application** — the Python source and its **Jinja templates** (with CSS) — and compiles it into static HTML. Built in **Java** with **ANTLR4** as a university compiler construction project.

## Compiler Pipeline

1. **Lexing & parsing** — custom ANTLR4 grammars for Flask Python code and for Jinja/HTML/CSS templates
2. **AST construction** — visitors build typed Abstract Syntax Trees for Python and templates, exported to JSON and rendered as images
3. **Symbol tables** — scoped symbol tables for both the Python code and the templates
4. **Semantic analysis** — detects undefined variables, misuse of names and other semantic errors, with a detailed report
5. **Context binding** — extracts the variables passed through `render_template()` and binds them to the template names
6. **Code generation** — generates the final HTML pages and serves them through a built-in HTTP server

## Tech Stack

- Java
- ANTLR 4.13
- Visitor pattern for AST building, symbol tables and analysis
- Python / Flask (input language and reference app)

## Project Structure

```
src/
├── ast/            # AST nodes and builder visitors
├── symbolTable/    # symbol tables and their builders
├── semantic/       # semantic analyzers and context extraction
├── codeGenerator/  # HTML code generator
├── server/         # HTTP server for the generated output
└── Main.java       # full compiler pipeline
tests/              # test suites for parsing, semantics and code generation
flask-app/          # sample Flask application used as input
```

## Running

```bash
./run_compiler.sh
```

Generated ASTs (JSON) and the semantic report are written to `compiler_output/`.

## Tests

The `tests/` folder covers basic and advanced parsing, semantic errors, symbol table validation, CSS and code generation:

```bash
cd tests && ./run_test.sh
```

## Author

**Abdalrhmman Alsyed** — Full Stack Software Engineer
[LinkedIn](https://www.linkedin.com/in/abdalrhmman-alsyed-4a9691348) · [GitHub](https://github.com/Abdalrhmman-Alsyed)
