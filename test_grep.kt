fun main() {
    val rawCommand = "grep -rn \"executeCommand\" app/src/main/java/"
    val trimmed = rawCommand.trim()
    val args = splitCommand(trimmed)
    println(args[0])
}
fun splitCommand(command: String): List<String> {
    val list = mutableListOf<String>()
    val current = StringBuilder()
    var inDoubleQuotes = false
    var inSingleQuotes = false
    var i = 0
    while (i < command.length) {
        val c = command[i]
        if (c == '\"' && !inSingleQuotes) {
            inDoubleQuotes = !inDoubleQuotes
        } else if (c == '\'' && !inDoubleQuotes) {
            inSingleQuotes = !inSingleQuotes
        } else if (c == ' ' && !inDoubleQuotes && !inSingleQuotes) {
            if (current.isNotEmpty()) {
                list.add(current.toString())
                current.setLength(0)
            }
        } else {
            current.append(c)
        }
        i++
    }
    if (current.isNotEmpty()) {
        list.add(current.toString())
    }
    return list
}
