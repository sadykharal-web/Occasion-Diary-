package com.example.drive

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.ActivityCategory
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.EventEntry
import com.example.data.FamilyProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory

data class LocalBackupPayload(
    val categories: List<ActivityCategory>,
    val families: List<FamilyProfile>,
    val activities: List<ActivityRecord>,
    val events: List<EventEntry> = emptyList(),
    val exportedAtMillis: Long
)

data class LocalBackupStatus(
    val isBusy: Boolean = false,
    val lastBackupMillis: Long = 0L,
    val lastFileName: String? = null,
    val statusMessage: String? = null
)

object LocalBackupManager {
    private const val TAG = "LocalBackupManager"
    const val DEFAULT_BACKUP_FILE_NAME = "Family_Events_Backup.xlsx"
    const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val PREFS_NAME = "occasion_diary_local_backup"
    private const val KEY_LAST_BACKUP = "last_backup_millis"

    fun getLastBackupMillis(context: Context): Long {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_BACKUP, 0L)
    }

    private fun saveLastBackupMillis(context: Context, millis: Long) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BACKUP, millis)
            .apply()
    }

    suspend fun exportBackupToUri(
        context: Context,
        uri: Uri,
        categories: List<ActivityCategory>,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val nowMillis = System.currentTimeMillis()
            val xlsxBytes = serializeBackupXlsxToBytes(
                exportedAtMillis = nowMillis,
                categories = categories,
                families = families,
                activities = activities,
                events = events
            )
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(xlsxBytes)
                out.flush()
            } ?: error("Could not open Excel backup file for writing.")
            saveLastBackupMillis(context, nowMillis)
            Result.success(nowMillis)
        } catch (e: Exception) {
            Log.e(TAG, "Excel backup export failed", e)
            Result.failure(e)
        }
    }

    suspend fun importBackupFromUri(
        context: Context,
        uri: Uri
    ): Result<LocalBackupPayload> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: error("Could not read selected backup file.")
            require(bytes.isNotEmpty()) { "Selected backup file is empty." }
            Result.success(parseBackupAuto(bytes))
        } catch (e: Exception) {
            Log.e(TAG, "Excel backup import failed", e)
            Result.failure(e)
        }
    }

    fun parseBackupAuto(bytes: ByteArray): LocalBackupPayload {
        require(bytes.isNotEmpty()) { "Selected backup file is empty." }
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (isZip) {
            return parseBackupXlsxBytes(bytes)
        }
        val text = bytes.toString(Charsets.UTF_8).trimStart()
        if (text.startsWith("{")) {
            return parseBackupJson(text)
        }
        error("Unsupported backup file format. Please select a Family Events Excel (.xlsx) backup file.")
    }

    fun serializeBackupXlsxToBytes(
        @Suppress("UNUSED_PARAMETER") exportedAtMillis: Long,
        @Suppress("UNUSED_PARAMETER") categories: List<ActivityCategory>,
        @Suppress("UNUSED_PARAMETER") families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        @Suppress("UNUSED_PARAMETER") events: List<EventEntry>
    ): ByteArray {
        val dateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)

        val ledgerHeaders = listOf(
            "FN",
            "Name",
            "FatherName",
            "Address",
            "EventType",
            "Event Name",
            "EventDate",
            "Direction",
            "HomeItems",
            "CashAmount",
            "HaarAmount",
            "SalamiAmount",
            "NeondraAmount",
            "TotalAmount"
        )
        val ledgerRows = activities.map { act ->
            listOf(
                CellValue.Text(act.familyId),
                CellValue.Text(act.name),
                CellValue.Text(act.fatherName),
                CellValue.Text(act.address),
                CellValue.Text(act.eventType),
                CellValue.Text(act.eventDetail),
                CellValue.Text(dateFmt.format(Date(act.eventDateMillis))),
                CellValue.Text(act.direction),
                CellValue.Text(act.homeItems),
                CellValue.Number(act.cashAmount),
                CellValue.Number(act.haarAmount),
                CellValue.Number(act.salamiAmount),
                CellValue.Number(act.neondraAmount),
                CellValue.Number(act.totalAmount)
            )
        }

        val sheets = listOf(
            SheetSpec("Ledger_Entries", ledgerHeaders, ledgerRows)
        )

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            writeZipText(zip, "[Content_Types].xml", buildContentTypesXml(sheets.size))
            writeZipText(zip, "_rels/.rels", buildRootRelsXml())
            writeZipText(zip, "xl/workbook.xml", buildWorkbookXml(sheets))
            writeZipText(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheets.size))
            writeZipText(zip, "xl/styles.xml", buildStylesXml())
            sheets.forEachIndexed { idx, sheet ->
                writeZipText(
                    zip,
                    "xl/worksheets/sheet${idx + 1}.xml",
                    buildWorksheetXml(sheet.headers, sheet.rows)
                )
            }
        }
        return baos.toByteArray()
    }

    fun parseBackupXlsxBytes(bytes: ByteArray): LocalBackupPayload {
        val entries = unzipEntries(bytes)
        val sharedStrings = entries["xl/sharedstrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val sheetPathsByName = resolveSheetPaths(entries)

        val ledgerTable = (resolveSheetBytes(
            entries,
            sheetPathsByName,
            listOf("ledger_entries", "lifeline", "activities", "ledger", "sheet1"),
            1
        ) ?: sheetPathsByName.values.firstOrNull()?.let { entries[it] })
            ?.let { parseWorksheetRows(it, sharedStrings) }
            ?: emptyList()
        val familiesTable = resolveSheetBytes(entries, sheetPathsByName, listOf("families"), null)
            ?.let { parseWorksheetRows(it, sharedStrings) }
            ?: emptyList()
        val categoriesTable = resolveSheetBytes(entries, sheetPathsByName, listOf("event_types", "categories"), null)
            ?.let { parseWorksheetRows(it, sharedStrings) }
            ?: emptyList()
        val eventsTable = resolveSheetBytes(entries, sheetPathsByName, listOf("events"), null)
            ?.let { parseWorksheetRows(it, sharedStrings) }
            ?: emptyList()

        if (ledgerTable.isEmpty() && familiesTable.isEmpty() && categoriesTable.isEmpty() && eventsTable.isEmpty()) {
            error("No valid Family Events sheets found inside this Excel (.xlsx) file.")
        }

        val dateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        val nowMillis = System.currentTimeMillis()

        val activities = rowsToMaps(ledgerTable).mapNotNull { row ->
            val name = row.cell("name")
            if (name.isBlank()) return@mapNotNull null

            val eventMillis = resolveRowEventDateMillis(
                rawDate = row.cell("eventdate", "date"),
                rawMillis = row.cellLong("eventdatemillis", -1L),
                displayFmt = dateFmt,
                fallbackMillis = nowMillis
            )

            val rawDirection = row.cell("direction")
            val resolvedDirection = when (rawDirection.lowercase(Locale.ENGLISH)) {
                "in", "received", "آمدن" -> ActivityDirection.IN.wireValue
                "out", "given", "خرچ" -> ActivityDirection.OUT.wireValue
                else -> ActivityDirection.fromWire(rawDirection.ifBlank { "OUT" }).wireValue
            }

            val cashAmt = row.cellLongAny(listOf("cashamount", "cash"), 0L).coerceAtLeast(0L)
            val haarAmt = row.cellLongAny(listOf("haaramount", "haar"), 0L).coerceAtLeast(0L)
            val salamiAmt = row.cellLongAny(listOf("salamiamount", "salami"), 0L).coerceAtLeast(0L)
            val rawNeondra = row.cellLongAny(listOf("neondraamount", "neondra"), 0L).coerceAtLeast(0L)
            val totalAmt = row.cellLongAny(listOf("totalamount", "total"), 0L).coerceAtLeast(0L)
            val neondraAmt = if (cashAmt == 0L && haarAmt == 0L && salamiAmt == 0L && rawNeondra == 0L && totalAmt > 0L) {
                totalAmt
            } else {
                rawNeondra
            }

            ActivityRecord(
                id = "",
                familyDocId = null,
                familyId = row.cell("fn", "familyid", "familyno", "familynumber"),
                name = name,
                fatherName = row.cell("fathername", "father"),
                address = row.cell("address"),
                eventType = row.cell("eventtype", "occasion").ifBlank { "شادی" },
                eventDetail = row.cell("eventname", "eventdetail", "eventdetails"),
                eventDateMillis = eventMillis,
                direction = resolvedDirection,
                homeItems = row.cell("homeitems", "homegiftitems"),
                cashAmount = cashAmt,
                haarAmount = haarAmt,
                salamiAmount = salamiAmt,
                neondraAmount = neondraAmt,
                reminderEnabled = row.cellBool("reminderenabled", false),
                reminderAtMillis = row.cellLong("reminderatmillis", 0L),
                reminderMinutesBefore = row.cellLong("reminderminutesbefore", 0L),
                calendarSynced = row.cellBool("calendarsynced", false)
            )
        }

        val families = rowsToMaps(familiesTable).mapNotNull { row ->
            val name = row.cell("name")
            if (name.isBlank()) return@mapNotNull null
            FamilyProfile(
                id = "",
                familyId = row.cell("fn", "familyid"),
                name = name,
                fatherName = row.cell("fathername", "father"),
                address = row.cell("address"),
                phone = row.cell("phone").takeIf { it.isNotBlank() }
            )
        }

        val categories = rowsToMaps(categoriesTable).mapNotNull { row ->
            val name = row.cell("name")
            if (name.isBlank()) return@mapNotNull null
            ActivityCategory(
                id = "",
                name = name,
                colorHex = row.cell("colorhex").ifBlank { "#0F5C4D" },
                iconKey = row.cell("iconkey").ifBlank { "event" }
            )
        }

        val events = rowsToMaps(eventsTable).mapNotNull { row ->
            val title = row.cell("title")
            if (title.isBlank()) return@mapNotNull null
            val eventMillis = resolveRowEventDateMillis(
                rawDate = row.cell("eventdate", "date"),
                rawMillis = row.cellLong("eventdatemillis", -1L),
                displayFmt = dateFmt,
                fallbackMillis = nowMillis
            )
            EventEntry(
                id = "",
                title = title,
                description = row.cell("description"),
                eventDateMillis = eventMillis,
                category = row.cell("eventtype", "category").ifBlank { "شادی" },
                location = row.cell("location"),
                familyDocId = null,
                familyName = row.cell("familyname")
            )
        }

        return LocalBackupPayload(
            categories = categories,
            families = families,
            activities = activities,
            events = events,
            exportedAtMillis = nowMillis
        )
    }

    fun serializeBackupJson(
        exportedAtMillis: Long,
        categories: List<ActivityCategory>,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): String {
        val root = JSONObject()
        root.put("app", "LifeLine")
        root.put("version", 4)
        root.put("mode", "offline")
        root.put("exportedAtMillis", exportedAtMillis)

        val catsArray = JSONArray()
        categories.forEach { cat ->
            catsArray.put(
                JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("colorHex", cat.colorHex)
                    put("iconKey", cat.iconKey)
                }
            )
        }
        root.put("categories", catsArray)

        val famsArray = JSONArray()
        families.forEach { fam ->
            famsArray.put(
                JSONObject().apply {
                    put("id", fam.id)
                    put("familyId", fam.familyId)
                    put("name", fam.name)
                    put("fatherName", fam.fatherName)
                    put("address", fam.address)
                    if (!fam.phone.isNullOrBlank()) put("phone", fam.phone)
                }
            )
        }
        root.put("families", famsArray)

        val actsArray = JSONArray()
        activities.forEach { act ->
            actsArray.put(
                JSONObject().apply {
                    put("id", act.id)
                    if (!act.familyDocId.isNullOrBlank()) put("familyDocId", act.familyDocId)
                    put("familyId", act.familyId)
                    put("name", act.name)
                    put("fatherName", act.fatherName)
                    put("address", act.address)
                    put("eventType", act.eventType)
                    put("eventDetail", act.eventDetail)
                    put("eventDateMillis", act.eventDateMillis)
                    put("direction", act.direction)
                    put("homeItems", act.homeItems)
                    put("cashAmount", act.cashAmount)
                    put("haarAmount", act.haarAmount)
                    put("salamiAmount", act.salamiAmount)
                    put("neondraAmount", act.neondraAmount)
                    put("reminderEnabled", act.reminderEnabled)
                    put("reminderAtMillis", act.reminderAtMillis)
                    put("reminderMinutesBefore", act.reminderMinutesBefore)
                    put("calendarSynced", act.calendarSynced)
                }
            )
        }
        root.put("activities", actsArray)

        val eventsArray = JSONArray()
        events.forEach { evt ->
            eventsArray.put(
                JSONObject().apply {
                    put("id", evt.id)
                    put("title", evt.title)
                    put("description", evt.description)
                    put("eventDateMillis", evt.eventDateMillis)
                    put("category", evt.category)
                    put("location", evt.location)
                    if (!evt.familyDocId.isNullOrBlank()) put("familyDocId", evt.familyDocId)
                    put("familyName", evt.familyName)
                }
            )
        }
        root.put("events", eventsArray)

        return root.toString(2)
    }

    fun parseBackupJson(raw: String): LocalBackupPayload {
        val root = JSONObject(raw)
        val exportedAt = root.optLong("exportedAtMillis", System.currentTimeMillis())

        val categories = buildList {
            val arr = root.optJSONArray("categories") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    ActivityCategory(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        colorHex = obj.optString("colorHex", "#0F5C4D"),
                        iconKey = obj.optString("iconKey", "event")
                    )
                )
            }
        }

        val families = buildList {
            val arr = root.optJSONArray("families") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    FamilyProfile(
                        id = obj.optString("id"),
                        familyId = obj.optString("familyId"),
                        name = obj.optString("name"),
                        fatherName = obj.optString("fatherName"),
                        address = obj.optString("address"),
                        phone = obj.optString("phone").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        val activities = buildList {
            val arr = root.optJSONArray("activities") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    ActivityRecord(
                        id = obj.optString("id"),
                        familyDocId = obj.optString("familyDocId").takeIf { it.isNotBlank() },
                        familyId = obj.optString("familyId"),
                        name = obj.optString("name"),
                        fatherName = obj.optString("fatherName"),
                        address = obj.optString("address"),
                        eventType = obj.optString("eventType"),
                        eventDetail = obj.optString("eventDetail"),
                        eventDateMillis = obj.optLong("eventDateMillis", System.currentTimeMillis()),
                        direction = obj.optString("direction", "OUT"),
                        homeItems = obj.optString("homeItems"),
                        cashAmount = obj.optLong("cashAmount", 0L),
                        haarAmount = obj.optLong("haarAmount", 0L),
                        salamiAmount = obj.optLong("salamiAmount", 0L),
                        neondraAmount = obj.optLong("neondraAmount", 0L),
                        reminderEnabled = obj.optBoolean("reminderEnabled", false),
                        reminderAtMillis = obj.optLong("reminderAtMillis", 0L),
                        reminderMinutesBefore = obj.optLong("reminderMinutesBefore", 0L),
                        calendarSynced = obj.optBoolean("calendarSynced", false)
                    )
                )
            }
        }

        val events = buildList {
            val arr = root.optJSONArray("events") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    EventEntry(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        description = obj.optString("description"),
                        eventDateMillis = obj.optLong("eventDateMillis", System.currentTimeMillis()),
                        category = obj.optString("category", "شادی"),
                        location = obj.optString("location"),
                        familyDocId = obj.optString("familyDocId").takeIf { it.isNotBlank() },
                        familyName = obj.optString("familyName")
                    )
                )
            }
        }

        return LocalBackupPayload(
            categories = categories,
            families = families,
            activities = activities,
            events = events,
            exportedAtMillis = exportedAt
        )
    }

    // --- OpenXML (.xlsx) Builder & Parser Helpers ---

    private sealed interface CellValue {
        data class Text(val text: String) : CellValue
        data class Number(val value: Long) : CellValue
        data class Bool(val value: Boolean) : CellValue
    }

    private data class SheetSpec(
        val name: String,
        val headers: List<String>,
        val rows: List<List<CellValue>>
    )

    private fun writeZipText(zip: ZipOutputStream, entryName: String, content: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildContentTypesXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""</Types>""")
    }

    private fun buildRootRelsXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""")
        append("""</Relationships>""")
    }

    private fun buildWorkbookXml(sheets: List<SheetSpec>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheets>""")
        sheets.forEachIndexed { idx, sheet ->
            val id = idx + 1
            append("""<sheet name="${escapeXml(sheet.name)}" sheetId="$id" r:id="rId$id"/>""")
        }
        append("""</sheets>""")
        append("""</workbook>""")
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("""</Relationships>""")
    }

    private fun buildStylesXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""")
        append("""<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""")
        append("""<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""")
        append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
        append("""<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>""")
        append("""</styleSheet>""")
    }

    private fun buildWorksheetXml(
        headers: List<String>,
        rows: List<List<CellValue>>
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetData>""")

        append("""<row r="1">""")
        headers.forEachIndexed { colIdx, header ->
            val ref = "${colIndexToLetters(colIdx)}1"
            appendInlineStrCell(ref, header, styleIdx = 1)
        }
        append("""</row>""")

        rows.forEachIndexed { rowOffset, rowCells ->
            val rowNum = rowOffset + 2
            append("""<row r="$rowNum">""")
            rowCells.forEachIndexed { colIdx, cell ->
                val ref = "${colIndexToLetters(colIdx)}$rowNum"
                when (cell) {
                    is CellValue.Text -> appendInlineStrCell(ref, cell.text, styleIdx = 0)
                    is CellValue.Number -> append("""<c r="$ref" t="n"><v>${cell.value}</v></c>""")
                    is CellValue.Bool -> append("""<c r="$ref" t="b"><v>${if (cell.value) 1 else 0}</v></c>""")
                }
            }
            append("""</row>""")
        }

        append("""</sheetData>""")
        append("""</worksheet>""")
    }

    private fun StringBuilder.appendInlineStrCell(ref: String, rawText: String, styleIdx: Int) {
        val sanitized = sanitizeXmlChars(rawText)
        val escaped = escapeXml(sanitized)
        val styleAttr = if (styleIdx > 0) """ s="$styleIdx"""" else ""
        val preserve = if (sanitized.startsWith(" ") || sanitized.endsWith(" ")) """ xml:space="preserve"""" else ""
        append("""<c r="$ref"$styleAttr t="inlineStr"><is><t$preserve>$escaped</t></is></c>""")
    }

    private fun colIndexToLetters(colIdx: Int): String {
        var n = colIdx
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append(('A'.code + (n % 26)).toChar())
            n = (n / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun lettersToColIndex(cellRef: String): Int {
        var idx = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                idx = idx * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                idx = idx * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (idx - 1).coerceAtLeast(0)
    }

    private fun sanitizeXmlChars(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val code = ch.code
            if (code == 0x09 || code == 0x0A || code == 0x0D || code >= 0x20) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    val key = entry.name.trimStart('/').lowercase(Locale.ENGLISH)
                    result[key] = zis.readBytes()
                }
                zis.closeEntry()
            }
        }
        return result
    }

    private fun parseXmlWithSax(xmlBytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val parser = factory.newSAXParser()
        val inputSource = InputSource(ByteArrayInputStream(xmlBytes).reader(Charsets.UTF_8))
        parser.parse(inputSource, handler)
    }

    private fun Attributes.attr(name: String): String {
        getValue(name)?.let { return it }
        for (i in 0 until length) {
            val rawName = getQName(i).ifBlank { getLocalName(i) }
            if (localTagName(rawName).equals(name, ignoreCase = true)) {
                return getValue(i).orEmpty()
            }
        }
        return ""
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        var insideSi = false
        var insideT = false
        val currentSi = StringBuilder()

        parseXmlWithSax(
            xmlBytes,
            object : DefaultHandler() {
                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String?,
                    attributes: Attributes?
                ) {
                    val tag = localTagName(qName?.ifBlank { localName } ?: localName)
                    if (tag == "si") {
                        insideSi = true
                        currentSi.setLength(0)
                    } else if (insideSi && tag == "t") {
                        insideT = true
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (insideSi && insideT) {
                        currentSi.append(ch, start, length)
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val tag = localTagName(qName?.ifBlank { localName } ?: localName)
                    if (tag == "t") {
                        insideT = false
                    } else if (tag == "si") {
                        list.add(currentSi.toString())
                        insideSi = false
                    }
                }
            }
        )
        return list
    }

    private fun resolveSheetPaths(entries: Map<String, ByteArray>): Map<String, String> {
        val relTargets = mutableMapOf<String, String>()
        entries["xl/_rels/workbook.xml.rels"]?.let { relBytes ->
            parseXmlWithSax(
                relBytes,
                object : DefaultHandler() {
                    override fun startElement(
                        uri: String?,
                        localName: String?,
                        qName: String?,
                        attributes: Attributes?
                    ) {
                        val tag = localTagName(qName?.ifBlank { localName } ?: localName)
                        if (tag == "Relationship" && attributes != null) {
                            val id = attributes.attr("Id")
                            val target = attributes.attr("Target").trimStart('/')
                            if (id.isNotBlank() && target.isNotBlank()) {
                                val full = if (target.startsWith("xl/", ignoreCase = true)) target else "xl/$target"
                                relTargets[id] = full.lowercase(Locale.ENGLISH)
                            }
                        }
                    }
                }
            )
        }

        val map = mutableMapOf<String, String>()
        entries["xl/workbook.xml"]?.let { wbBytes ->
            var seq = 1
            parseXmlWithSax(
                wbBytes,
                object : DefaultHandler() {
                    override fun startElement(
                        uri: String?,
                        localName: String?,
                        qName: String?,
                        attributes: Attributes?
                    ) {
                        val tag = localTagName(qName?.ifBlank { localName } ?: localName)
                        if (tag == "sheet" && attributes != null) {
                            val name = attributes.attr("name").trim().lowercase(Locale.ENGLISH)
                            val rId = attributes.attr("id").takeIf { it.isNotBlank() }
                            val targetPath = rId?.let { relTargets[it] } ?: "xl/worksheets/sheet$seq.xml"
                            if (name.isNotBlank()) {
                                map[name] = targetPath
                            }
                            seq++
                        }
                    }
                }
            )
        }
        return map
    }

    private fun resolveSheetBytes(
        entries: Map<String, ByteArray>,
        sheetPathsByName: Map<String, String>,
        candidateNames: List<String>,
        fallbackSheetNumber: Int?
    ): ByteArray? {
        for (cand in candidateNames) {
            val path = sheetPathsByName[cand]
            if (path != null && entries[path] != null) return entries[path]
        }
        return fallbackSheetNumber?.let { entries["xl/worksheets/sheet$it.xml"] }
    }

    private fun parseWorksheetRows(
        xmlBytes: ByteArray,
        sharedStrings: List<String>
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()

        var currentRow: MutableList<String>? = null
        var nextColIdx = 0
        var activeColIdx = -1
        var activeCellType = ""
        var insideCell = false
        var insideValueTag = false
        var insideInlineTextTag = false
        val cellTextBuffer = StringBuilder()

        parseXmlWithSax(
            xmlBytes,
            object : DefaultHandler() {
                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String?,
                    attributes: Attributes?
                ) {
                    when (localTagName(qName?.ifBlank { localName } ?: localName)) {
                        "row" -> {
                            currentRow = mutableListOf()
                            nextColIdx = 0
                        }
                        "c" -> {
                            insideCell = true
                            val ref = attributes?.attr("r").orEmpty()
                            activeColIdx = if (ref.isNotBlank()) lettersToColIndex(ref) else nextColIdx
                            nextColIdx = activeColIdx + 1
                            activeCellType = attributes?.attr("t").orEmpty()
                            cellTextBuffer.setLength(0)
                            insideValueTag = false
                            insideInlineTextTag = false
                        }
                        "v" -> if (insideCell) insideValueTag = true
                        "t" -> if (insideCell) insideInlineTextTag = true
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (insideCell && (insideValueTag || insideInlineTextTag)) {
                        cellTextBuffer.append(ch, start, length)
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    when (localTagName(qName?.ifBlank { localName } ?: localName)) {
                        "v" -> insideValueTag = false
                        "t" -> insideInlineTextTag = false
                        "c" -> {
                            val row = currentRow
                            if (insideCell && row != null) {
                                val raw = cellTextBuffer.toString()
                                val resolved = when (activeCellType) {
                                    "s" -> raw.trim().toIntOrNull()?.let { idx ->
                                        sharedStrings.getOrElse(idx) { "" }
                                    }.orEmpty()
                                    "b" -> if (raw.trim() == "1" || raw.trim().equals("true", ignoreCase = true)) "true" else "false"
                                    else -> raw
                                }
                                while (row.size <= activeColIdx) {
                                    row.add("")
                                }
                                row[activeColIdx] = resolved
                            }
                            insideCell = false
                            insideValueTag = false
                            insideInlineTextTag = false
                        }
                        "row" -> {
                            val finished = currentRow
                            if (finished != null && finished.any { it.isNotBlank() }) {
                                rows.add(finished)
                            }
                            currentRow = null
                        }
                    }
                }
            }
        )
        return rows
    }

    private fun localTagName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val colon = raw.indexOf(':')
        return if (colon >= 0) raw.substring(colon + 1) else raw
    }

    private fun normalizeHeader(header: String): String {
        return header.trim().lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9]"), "")
    }

    private fun rowsToMaps(table: List<List<String>>): List<Map<String, String>> {
        if (table.size < 2) return emptyList()
        val headers = table.first().map(::normalizeHeader)
        return table.drop(1).map { row ->
            val map = linkedMapOf<String, String>()
            headers.forEachIndexed { idx, key ->
                if (key.isNotBlank() && idx < row.size) {
                    map[key] = row[idx]
                }
            }
            map
        }
    }

    private fun Map<String, String>.cell(vararg keys: String): String {
        for (k in keys) {
            val v = this[k]
            if (v != null) return v.trim()
        }
        return ""
    }

    private fun Map<String, String>.cellLong(key: String, default: Long): Long {
        val raw = this[key]?.trim()?.replace(",", "").orEmpty()
        if (raw.isEmpty()) return default
        return raw.toLongOrNull()
            ?: raw.toDoubleOrNull()?.toLong()
            ?: default
    }

    private fun Map<String, String>.cellLongAny(keys: List<String>, default: Long): Long {
        for (k in keys) {
            val raw = this[k]?.trim()?.replace(",", "").orEmpty()
            if (raw.isNotEmpty()) {
                val parsed = raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
                if (parsed != null) return parsed
            }
        }
        return default
    }

    private fun Map<String, String>.cellBool(key: String, default: Boolean): Boolean {
        val raw = this[key]?.trim().orEmpty()
        if (raw.isEmpty()) return default
        return when (raw.lowercase(Locale.ENGLISH)) {
            "1", "true", "yes" -> true
            "0", "false", "no" -> false
            else -> default
        }
    }

    private fun resolveRowEventDateMillis(
        rawDate: String,
        rawMillis: Long,
        displayFmt: SimpleDateFormat,
        fallbackMillis: Long
    ): Long {
        val parsedDateMillis = parseDateOrNull(rawDate)
        return when {
            rawMillis >= 0L && parsedDateMillis != null -> {
                val msDay = displayFmt.format(Date(rawMillis))
                val parsedDay = displayFmt.format(Date(parsedDateMillis))
                if (msDay == parsedDay) rawMillis else parsedDateMillis
            }
            rawMillis >= 0L -> rawMillis
            parsedDateMillis != null -> parsedDateMillis
            else -> fallbackMillis
        }
    }

    private fun parseDateOrNull(rawDate: String): Long? {
        val clean = rawDate.trim()
        if (clean.isEmpty()) return null

        // Handle Excel serial date numbers (e.g. 46352) when edited directly in Excel
        val asDouble = clean.toDoubleOrNull()
        if (asDouble != null && asDouble in 20_000.0..100_000.0) {
            val wholeDays = asDouble.toLong()
            val cal = java.util.Calendar.getInstance().apply {
                set(1899, java.util.Calendar.DECEMBER, 30, 12, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                add(java.util.Calendar.DAY_OF_YEAR, wholeDays.toInt())
            }
            return cal.timeInMillis
        }

        val patterns = listOf(
            "dd-MM-yyyy",
            "d-M-yyyy",
            "dd/MM/yyyy",
            "d/M/yyyy",
            "yyyy-MM-dd",
            "dd MMM yyyy"
        )
        for (pattern in patterns) {
            val fmt = SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                isLenient = false
            }
            val parsed = runCatching { fmt.parse(clean)?.time }.getOrNull()
            if (parsed != null) {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = parsed
                    set(java.util.Calendar.HOUR_OF_DAY, 12)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }
        }
        return null
    }
}
