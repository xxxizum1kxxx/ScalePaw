package com.izum1k.scalepaw.data.transfer

import com.izum1k.scalepaw.data.model.InputType
import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.MetricValue
import com.izum1k.scalepaw.data.repository.ScaleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ImportReport(
    val imported: Int,
    val skippedRows: Int,
    val skippedColumns: List<String>,
)

/** A decoded file: rows ready for the repository plus what was skipped. */
data class DecodedCsv(
    val rows: List<Pair<Long, Map<Long, MetricValue>>>,
    val skippedRows: Int,
    val skippedColumns: List<String>,
)

/**
 * openScale-compatible CSV: `DATE` (ISO date) and `TIME` (ISO time) columns, then one column per
 * metric keyed by its uppercased identity (`WEIGHT`, `BODY_FAT`). Older openScale files with a
 * single `dateTime` column ("yyyy-MM-dd HH:mm") and user types exported by name also import.
 */
class CsvTransfer(private val repository: ScaleRepository) {

    suspend fun export(userId: Long, out: OutputStream): Int = withContext(Dispatchers.IO) {
        val types = repository.typesNow()
        val measurements = repository.measurementsNow(userId)
        out.bufferedWriter().use { it.write(encode(measurements, types)) }
        measurements.size
    }

    suspend fun import(userId: Long, input: InputStream, replace: Boolean): ImportReport = withContext(Dispatchers.IO) {
        val text = input.bufferedReader().use { it.readText() }
        val decoded = decode(text, repository.typesNow())
        val count = repository.importMeasurements(userId, decoded.rows, replace)
        ImportReport(count, decoded.skippedRows, decoded.skippedColumns)
    }

    companion object {
        const val DATE = "DATE"
        const val TIME = "TIME"
        const val LEGACY_DATE_TIME = "dateTime"
        private val legacyFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun encode(measurements: List<Measurement>, types: List<MetricType>, zone: ZoneId = ZoneId.systemDefault()): String {
            val columns = types.sortedBy { it.columnKey }
            val header = listOf(DATE, TIME) + columns.map { it.columnKey }
            val rows = measurements.sortedBy { it.timestamp }.map { m ->
                val dt = Instant.ofEpochMilli(m.timestamp).atZone(zone)
                listOf(DateTimeFormatter.ISO_LOCAL_DATE.format(dt), DateTimeFormatter.ISO_LOCAL_TIME.format(dt.toLocalTime().withNano(0))) +
                    columns.map { t ->
                        val v = m.values[t.id]
                        when (t.inputType) {
                            InputType.FLOAT -> v?.float?.toString()
                            InputType.INT -> v?.int?.toString()
                            InputType.TEXT -> v?.text
                        }
                    }
            }
            return Csv.write(listOf(header) + rows)
        }

        fun decode(text: String, types: List<MetricType>, zone: ZoneId = ZoneId.systemDefault()): DecodedCsv {
            val table = Csv.parse(text)
            val header = table.firstOrNull() ?: throw IllegalArgumentException("Empty file")
            val dateIdx = header.indexOfFirst { it.trim().equals(DATE, ignoreCase = true) }
            val timeIdx = header.indexOfFirst { it.trim().equals(TIME, ignoreCase = true) }
            val legacyIdx = header.indexOfFirst { it.trim().equals(LEGACY_DATE_TIME, ignoreCase = true) }
            require(dateIdx >= 0 || legacyIdx >= 0) { "Missing $DATE or $LEGACY_DATE_TIME column" }

            val skippedColumns = mutableListOf<String>()
            val columnTypes = mutableMapOf<Int, MetricType>()
            header.forEachIndexed { i, raw ->
                if (i == dateIdx || i == timeIdx || i == legacyIdx) return@forEachIndexed
                val name = raw.trim()
                val type = types.firstOrNull { it.columnKey.equals(name, ignoreCase = true) }
                    ?: types.firstOrNull { it.isUserOwned && it.customName.equals(name, ignoreCase = true) }
                if (type != null) columnTypes[i] = type else skippedColumns += name
            }

            var skipped = 0
            val rows = table.drop(1).mapNotNull { row ->
                val timestamp = parseTimestamp(row, dateIdx, timeIdx, legacyIdx, zone)
                if (timestamp == null) {
                    skipped++
                    return@mapNotNull null
                }
                val values = columnTypes.mapNotNull { (i, type) ->
                    val cell = row.getOrNull(i)?.trim().orEmpty()
                    if (cell.isEmpty()) return@mapNotNull null
                    val value = when (type.inputType) {
                        InputType.FLOAT -> cell.replace(',', '.').toFloatOrNull()?.let { MetricValue.of(it) }
                        InputType.INT -> (cell.toIntOrNull() ?: cell.replace(',', '.').toFloatOrNull()?.toInt())?.let { MetricValue.of(it) }
                        InputType.TEXT -> MetricValue.of(cell)
                    }
                    value?.let { type.id to it }
                }.toMap()
                if (values.isEmpty()) {
                    skipped++
                    null
                } else {
                    timestamp to values
                }
            }
            return DecodedCsv(rows, skipped, skippedColumns)
        }

        private fun parseTimestamp(row: List<String>, dateIdx: Int, timeIdx: Int, legacyIdx: Int, zone: ZoneId): Long? = try {
            val dateTime = if (dateIdx >= 0 && row.getOrNull(dateIdx)?.isNotBlank() == true) {
                val date = LocalDate.parse(row[dateIdx].trim())
                val time = row.getOrNull(timeIdx)?.trim()?.takeIf { it.isNotEmpty() }?.let { parseTime(it) } ?: LocalTime.NOON
                date.atTime(time)
            } else {
                row.getOrNull(legacyIdx)?.trim()?.takeIf { it.isNotEmpty() }?.let { LocalDateTime.parse(it, legacyFormat) }
            }
            dateTime?.atZone(zone)?.toInstant()?.toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }

        private fun parseTime(text: String): LocalTime? = try {
            LocalTime.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
