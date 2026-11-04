package com.team04.splitsmart.logic.history;

/** CSV cell quoting and spreadsheet formula protection, independent of transaction columns. */
final class CsvRows {
    private CsvRows() {
    }

    static void appendRow(StringBuilder csv, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            String value = fields[i];
            csv.append('"');
            if (looksLikeFormula(value)) {
                csv.append('\'');
            }
            csv.append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    /** Quoting alone does not stop spreadsheet programs from evaluating cell formulas. */
    private static boolean looksLikeFormula(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\t' || c == '\r' || c == '\n') {
                return true;
            }
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)
                    || Character.getType(c) == Character.FORMAT) {
                continue;
            }
            return c == '=' || c == '+' || c == '-' || c == '@';
        }
        return false;
    }
}
