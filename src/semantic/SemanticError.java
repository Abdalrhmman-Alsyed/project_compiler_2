package semantic;

public class SemanticError {

    public enum Severity { ERROR, WARNING }

    private final Severity severity;
    private final String message;
    private final int line;
    private final int column;

    public SemanticError(Severity severity, String message, int line, int column) {
        this.severity = severity;
        this.message = message;
        this.line = line;
        this.column = column;
    }

    public Severity getSeverity() { return severity; }
    public String getMessage()    { return message; }
    public int getLine()          { return line; }
    public int getColumn()        { return column; }

    public boolean isError()   { return severity == Severity.ERROR; }
    public boolean isWarning() { return severity == Severity.WARNING; }

    @Override
    public String toString() {
        return String.format("[%-7s] Line %d:%d — %s", severity, line, column, message);
    }
}
