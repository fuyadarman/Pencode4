public class TestGrep {
    public static void main(String[] args) {
        String rawCommand = "grep -rn \"executeCommand\" app/src/main/java/";
        String trimmed = rawCommand.trim();
        java.util.List<String> list = splitCommand(trimmed);
        System.out.println(list.get(0));
    }
    public static java.util.List<String> splitCommand(String command) {
        java.util.List<String> list = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        int i = 0;
        while (i < command.length()) {
            char c = command.charAt(i);
            if (c == '\"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (c == ' ' && !inDoubleQuotes && !inSingleQuotes) {
                if (current.length() > 0) {
                    list.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
            i++;
        }
        if (current.length() > 0) {
            list.add(current.toString());
        }
        return list;
    }
}
