package com.gpo.scalepaw.data.transfer

/** Minimal RFC 4180 CSV: comma separated, double-quote escaping, CRLF or LF line ends. */
object Csv {
    fun write(rows: List<List<String?>>): String = buildString {
        rows.forEach { row ->
            row.forEachIndexed { i, cell ->
                if (i > 0) append(',')
                append(escape(cell.orEmpty()))
            }
            append("\r\n")
        }
    }

    private fun escape(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    /** Parses [text] into rows; blank lines are skipped and a leading BOM is ignored. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = if (text.startsWith('\uFEFF')) 1 else 0
        fun endCell() {
            row += cell.toString()
            cell.clear()
        }
        fun endRow() {
            endCell()
            if (row.size > 1 || row.first().isNotEmpty()) rows += row
            row = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> endCell()
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
