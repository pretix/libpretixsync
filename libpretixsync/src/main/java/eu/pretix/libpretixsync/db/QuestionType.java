package eu.pretix.libpretixsync.db;

public enum QuestionType {
    NUMBER("N"), STRING("S"), TEXT("T"), BOOLEAN("B"), CHOICE("C"), CHOICE_MULTIPLE("M"), FILE("F"),
    DATE("D"), TIME("H"), DATETIME("W");

    private final String code;

    private QuestionType(String value) {
        code = value;
    }

    public String toString() {
        return code;
    }
}
