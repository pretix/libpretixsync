package eu.pretix.libpretixsync.check;

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

    public static QuestionType fromString(String text) {
        for (QuestionType b : QuestionType.values()) {
            if (b.code.equals(text)) {
                return b;
            }
        }
        return null;
    }
}
