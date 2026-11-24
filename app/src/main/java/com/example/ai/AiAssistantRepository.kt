package com.example.ai

import android.util.Log
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.EventEntry
import com.example.data.FamilyProfile
import com.example.ui.screens.localDayStartMillis
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiSearchResult(
    val query: String,
    val answerSummary: String,
    val matchedFamilyIds: List<String>,
    val matchedActivityIds: List<String>,
    val matchedEventIds: List<String>,
    val keyInsights: List<String>,
    val isOnlineAi: Boolean = false
)

data class AiFamilyBalanceInsight(
    val familyDocId: String,
    val familyId: String,
    val familyName: String,
    val totalIn: Long,
    val totalOut: Long,
    val netBalance: Long,
    val entriesCount: Int,
    val eventsCount: Int,
    val insight: String
)

data class AiBalanceComparisonResult(
    val headline: String,
    val summary: String,
    val higherNetFamilyName: String,
    val familyBreakdowns: List<AiFamilyBalanceInsight>,
    val recommendations: List<String>
)

data class CategoryInOutBreakdown(
    val categoryName: String,
    val totalIn: Long,
    val totalOut: Long,
    val inCount: Int,
    val outCount: Int
) {
    val netBalance: Long get() = totalIn - totalOut
}

data class SingleFamilyInOutComparison(
    val family: FamilyProfile,
    val totalIn: Long,
    val totalOut: Long,
    val netBalance: Long,
    val inEntries: List<ActivityRecord>,
    val outEntries: List<ActivityRecord>,
    val linkedEvents: List<EventEntry>,
    val categoryBreakdowns: List<CategoryInOutBreakdown>,
    val headline: String,
    val summary: String,
    val insights: List<String>,
    val usedOnlineAi: Boolean = false
)

/**
 * Combines online Gemini AI search & single-family In/Out analysis (via Firebase AI Logic
 * reading the device's offline Room SQLite snapshot) with deterministic local calculations.
 */
class AiAssistantRepository {

    private val searchSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "answerSummary" to Schema.string(
                    description = "Clear direct answer to the user's question based strictly on the provided offline Life Line diary records."
                ),
                "matchedFamilyIds" to Schema.array(
                    items = Schema.string(),
                    description = "Exact IDs of matching families from the offline snapshot."
                ),
                "matchedActivityIds" to Schema.array(
                    items = Schema.string(),
                    description = "Exact IDs of matching ledger entries (In/Out activities) from the offline snapshot."
                ),
                "matchedEventIds" to Schema.array(
                    items = Schema.string(),
                    description = "Exact IDs of matching saved occasions/events from the offline snapshot."
                ),
                "keyInsights" to Schema.array(
                    items = Schema.string(),
                    description = "Up to 4 concise bullet insights with exact numbers, names, or dates."
                )
            )
        )
    }

    private val singleFamilySchema by lazy {
        Schema.obj(
            properties = mapOf(
                "headline" to Schema.string(
                    description = "Short headline comparing In (Received) vs Out (Given) entries for this single family."
                ),
                "summary" to Schema.string(
                    description = "Concise 2-3 sentence summary comparing the family's In entries against their Out entries, categories, and net reciprocity."
                ),
                "insights" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 4 specific insights comparing the In and Out entries, amounts, occasions, and next steps."
                )
            )
        )
    }

    private val searchModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = searchSchema
                temperature = 0.2f
            }
        )
    }

    private val singleFamilyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = singleFamilySchema
                temperature = 0.25f
            }
        )
    }

    suspend fun searchFamiliesAndEventsOnline(
        query: String,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<AiSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter a search query."))
        }

        if (!isFirebaseConfigured()) {
            return Result.failure(
                IllegalStateException(MISSING_FIREBASE_CONFIG_MESSAGE)
            )
        }

        return try {
            val prompt = buildOnlineSearchPrompt(cleanQuery, families, activities, events)
            val response = withTimeout(AI_TIMEOUT_MILLIS) {
                searchModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned an empty response.")

            val json = JSONObject(rawJson)
            val validFamilyIds = families.map { it.id }.toSet()
            val validActivityIds = activities.map { it.id }.toSet()
            val validEventIds = events.map { it.id }.toSet()

            val matchedFams = json.optStringList("matchedFamilyIds").filter { it in validFamilyIds }
            val matchedActs = json.optStringList("matchedActivityIds").filter { it in validActivityIds }
            val matchedEvts = json.optStringList("matchedEventIds").filter { it in validEventIds }
            val insights = json.optStringList("keyInsights").take(5)
            val answer = json.optString("answerSummary").ifBlank {
                "Online AI analyzed ${families.size} families, ${events.size} occasions, and ${activities.size} ledger entries."
            }

            Result.success(
                AiSearchResult(
                    query = cleanQuery,
                    answerSummary = answer,
                    matchedFamilyIds = matchedFams,
                    matchedActivityIds = matchedActs,
                    matchedEventIds = matchedEvts,
                    keyInsights = insights,
                    isOnlineAi = true
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Online AI search timed out", e)
            Result.failure(
                IllegalStateException("Online AI search timed out after 60 seconds. Check your internet connection and retry.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Online AI search failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Online AI search failed. Please verify internet connectivity.")
            )
        }
    }

    fun compareWithinSingleFamilyLocal(
        family: FamilyProfile,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): SingleFamilyInOutComparison {
        val fmt = NumberFormat.getNumberInstance(Locale.US)
        val famActs = activitiesForFamily(family, activities).sortedByDescending { it.eventDateMillis }
        val famEvents = events.filter { evt ->
            evt.familyDocId == family.id || evt.familyName.equals(family.name, ignoreCase = true)
        }.sortedBy { it.eventDateMillis }

        val inEntries = famActs.filter { it.parsedDirection == ActivityDirection.IN }
        val outEntries = famActs.filter { it.parsedDirection == ActivityDirection.OUT }

        val totalIn = inEntries.sumOf { it.neondraAmount }
        val totalOut = outEntries.sumOf { it.neondraAmount }
        val netBalance = totalIn - totalOut

        val categoryBreakdowns = famActs
            .groupBy { it.eventType.trim().ifEmpty { "General" } }
            .map { (catName, records) ->
                val catIn = records.filter { it.parsedDirection == ActivityDirection.IN }
                val catOut = records.filter { it.parsedDirection == ActivityDirection.OUT }
                CategoryInOutBreakdown(
                    categoryName = catName,
                    totalIn = catIn.sumOf { it.neondraAmount },
                    totalOut = catOut.sumOf { it.neondraAmount },
                    inCount = catIn.size,
                    outCount = catOut.size
                )
            }
            .sortedByDescending { it.totalIn + it.totalOut }

        val headline = when {
            famActs.isEmpty() -> "${family.name}: No In or Out entries yet"
            netBalance > 0L -> "${family.name}: In exceeds Out by +${fmt.format(netBalance)}"
            netBalance < 0L -> "${family.name}: Out exceeds In by ${fmt.format(-netBalance)}"
            else -> "${family.name}: In & Out balanced (${fmt.format(totalIn)})"
        }

        val summary = if (famActs.isEmpty()) {
            "No ledger entries have been saved offline for ${family.name} yet."
        } else {
            "${family.name} has ${inEntries.size} In (Received) entries totaling ${fmt.format(totalIn)} and ${outEntries.size} Out (Given) entries totaling ${fmt.format(totalOut)}, giving a net balance of ${if (netBalance >= 0) "+" else ""}${fmt.format(netBalance)}."
        }

        val insights = buildList {
            if (inEntries.isNotEmpty()) {
                val topIn = inEntries.maxByOrNull { it.neondraAmount }!!
                add("Largest In entry: ${fmt.format(topIn.neondraAmount)} for ${topIn.eventType}${if (topIn.eventDetail.isNotBlank()) " (${topIn.eventDetail})" else ""}.")
            } else {
                add("No In (Received) entries recorded for ${family.name} yet.")
            }
            if (outEntries.isNotEmpty()) {
                val topOut = outEntries.maxByOrNull { it.neondraAmount }!!
                add("Largest Out entry: ${fmt.format(topOut.neondraAmount)} for ${topOut.eventType}${if (topOut.eventDetail.isNotBlank()) " (${topOut.eventDetail})" else ""}.")
            } else {
                add("No Out (Given) entries recorded for ${family.name} yet.")
            }
            if (categoryBreakdowns.isNotEmpty()) {
                val topCat = categoryBreakdowns.first()
                add("Top occasion category: ${topCat.categoryName} (In: ${fmt.format(topCat.totalIn)} vs Out: ${fmt.format(topCat.totalOut)}).")
            }
        }

        return SingleFamilyInOutComparison(
            family = family,
            totalIn = totalIn,
            totalOut = totalOut,
            netBalance = netBalance,
            inEntries = inEntries,
            outEntries = outEntries,
            linkedEvents = famEvents,
            categoryBreakdowns = categoryBreakdowns,
            headline = headline,
            summary = summary,
            insights = insights,
            usedOnlineAi = false
        )
    }

    suspend fun analyzeWithinSingleFamilyOnline(
        family: FamilyProfile,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<SingleFamilyInOutComparison> {
        val base = compareWithinSingleFamilyLocal(family, activities, events)
        if (!isFirebaseConfigured()) {
            return Result.failure(
                IllegalStateException(MISSING_FIREBASE_CONFIG_MESSAGE)
            )
        }
        return try {
            val prompt = buildSingleFamilyPrompt(base)
            val response = withTimeout(AI_TIMEOUT_MILLIS) {
                singleFamilyModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned an empty response.")

            val json = JSONObject(rawJson)
            val headline = json.optString("headline").ifBlank { base.headline }
            val summary = json.optString("summary").ifBlank { base.summary }
            val insights = json.optStringList("insights").ifEmpty { base.insights }

            Result.success(
                base.copy(
                    headline = headline,
                    summary = summary,
                    insights = insights,
                    usedOnlineAi = true
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Online AI single-family analysis timed out", e)
            Result.failure(
                IllegalStateException("Online AI analysis timed out after 60 seconds. Check your internet connection and retry.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Online AI single-family analysis failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Online AI analysis failed. Please verify internet connectivity.")
            )
        }
    }

    /**
     * Fast local offline filter & keyword search across Room SQLite records.
     */
    fun searchFamiliesAndEvents(
        query: String,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<AiSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter a search query."))
        }

        val lowerQuery = cleanQuery.lowercase(Locale.ENGLISH)
        val stopWords = setOf(
            "the", "and", "with", "who", "what", "which", "has", "have", "show",
            "find", "all", "for", "from", "are", "their", "list", "entries", "records"
        )
        val tokens = lowerQuery
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }

        val fmt = NumberFormat.getNumberInstance(Locale.US)
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        val todayStart = localDayStartMillis(System.currentTimeMillis())

        val familyStats = families.map { fam ->
            val famActs = activitiesForFamily(fam, activities)
            val inTotal = famActs.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.neondraAmount }
            val outTotal = famActs.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.neondraAmount }
            Triple(fam, Triple(inTotal, outTotal, inTotal - outTotal), famActs.size)
        }

        val wantsHighestIn = "highest" in lowerQuery && "in" in lowerQuery
        val wantsNegative = "negative" in lowerQuery || "owe" in lowerQuery || "deficit" in lowerQuery
        val wantsPositive = "positive" in lowerQuery || "surplus" in lowerQuery
        val wantsUpcoming = "upcoming" in lowerQuery || "future" in lowerQuery || "next" in lowerQuery

        val matchedFamilies = when {
            wantsHighestIn -> familyStats
                .filter { it.second.first > 0L }
                .sortedByDescending { it.second.first }
                .take(5)
                .map { it.first }

            wantsNegative -> familyStats
                .filter { it.second.third < 0L }
                .sortedBy { it.second.third }
                .map { it.first }

            wantsPositive -> familyStats
                .filter { it.second.third > 0L }
                .sortedByDescending { it.second.third }
                .map { it.first }

            else -> families.filter { fam ->
                val haystack = "${fam.familyId} ${fam.name} ${fam.fatherName} ${fam.address}".lowercase(Locale.ENGLISH)
                tokens.any { it in haystack } || lowerQuery in haystack
            }
        }

        val matchedEvents = if (wantsUpcoming && tokens.none { it !in setOf("upcoming", "events", "event", "occasions", "occasion") }) {
            events.filter { it.eventDateMillis >= todayStart }.sortedBy { it.eventDateMillis }
        } else {
            events.filter { evt ->
                val haystack = "${evt.title} ${evt.description} ${evt.category} ${evt.location} ${evt.familyName}".lowercase(Locale.ENGLISH)
                val matchesText = tokens.any { it in haystack } || lowerQuery in haystack
                val matchesUpcoming = !wantsUpcoming || evt.eventDateMillis >= todayStart
                matchesText && matchesUpcoming
            }
        }

        val wantsIn = "in" in tokens || "received" in tokens
        val wantsOut = "out" in tokens || "given" in tokens || "gifts" in tokens || "cash" in tokens
        val matchedActivities = activities.filter { act ->
            val haystack = "${act.name} fn${act.familyId} ${act.fatherName} ${act.address} ${act.eventType} ${act.eventDetail} ${act.homeItems} ${act.direction}".lowercase(Locale.ENGLISH)
            val textHit = tokens.any { it in haystack } || lowerQuery in haystack
            val dirHit = (wantsIn && act.parsedDirection == ActivityDirection.IN) ||
                (wantsOut && act.parsedDirection == ActivityDirection.OUT)
            val upcomingHit = !wantsUpcoming || act.eventDateMillis >= todayStart
            (textHit || dirHit) && upcomingHit
        }.sortedByDescending { it.eventDateMillis }.take(25)

        val insights = buildList {
            if (matchedFamilies.isNotEmpty()) {
                val top = familyStats.firstOrNull { it.first.id == matchedFamilies.first().id }
                if (top != null) {
                    val net = top.second.third
                    val sign = if (net >= 0) "+" else ""
                    add("${top.first.name} (FN ${top.first.familyId}): In ${fmt.format(top.second.first)}, Out ${fmt.format(top.second.second)}, Net $sign${fmt.format(net)}.")
                }
            }
            if (matchedEvents.isNotEmpty()) {
                val nextEvt = matchedEvents.minByOrNull { it.eventDateMillis }!!
                add("Next matched occasion: ${nextEvt.title} on ${dateFmt.format(Date(nextEvt.eventDateMillis))}.")
            }
            if (matchedActivities.isNotEmpty()) {
                val inSum = matchedActivities.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.neondraAmount }
                val outSum = matchedActivities.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.neondraAmount }
                add("Matched ${matchedActivities.size} ledger entries (In: ${fmt.format(inSum)} | Out: ${fmt.format(outSum)}).")
            }
        }

        val totalMatches = matchedFamilies.size + matchedEvents.size + matchedActivities.size
        val summary = if (totalMatches == 0) {
            "No offline records matched \"$cleanQuery\". Try Online AI Search or refine your keywords."
        } else {
            "Found ${matchedFamilies.size} families, ${matchedEvents.size} occasions, and ${matchedActivities.size} ledger entries matching \"$cleanQuery\" in local device storage."
        }

        return Result.success(
            AiSearchResult(
                query = cleanQuery,
                answerSummary = summary,
                matchedFamilyIds = matchedFamilies.map { it.id },
                matchedActivityIds = matchedActivities.map { it.id },
                matchedEventIds = matchedEvents.map { it.id },
                keyInsights = insights,
                isOnlineAi = false
            )
        )
    }

    fun compareFamilyBalances(
        selectedFamilies: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<AiBalanceComparisonResult> {
        if (selectedFamilies.size < 2) {
            return Result.failure(IllegalArgumentException("Select at least 2 families to compare across families."))
        }

        val fmt = NumberFormat.getNumberInstance(Locale.US)
        val computedStats = selectedFamilies.map { fam ->
            val famActs = activitiesForFamily(fam, activities)
            val famEvents = events.filter { evt ->
                evt.familyDocId == fam.id || evt.familyName.equals(fam.name, ignoreCase = true)
            }
            val totalIn = famActs.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.neondraAmount }
            val totalOut = famActs.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.neondraAmount }
            val net = totalIn - totalOut
            ComputedFamilyStat(fam, famActs, famEvents, totalIn, totalOut, net)
        }

        val topNet = computedStats.maxByOrNull { it.net }
        val lowestNet = computedStats.minByOrNull { it.net }

        val breakdowns = computedStats.map { stat ->
            val insight = when {
                stat.activities.isEmpty() ->
                    "No ledger entries recorded for ${stat.family.name} yet (${stat.events.size} linked occasions)."
                stat.net > 0 ->
                    "Net surplus of +${fmt.format(stat.net)} (received ${fmt.format(stat.totalIn)} vs given ${fmt.format(stat.totalOut)} across ${stat.activities.size} entries)."
                stat.net < 0 ->
                    "Net given exceeds received by ${fmt.format(-stat.net)} (In: ${fmt.format(stat.totalIn)}, Out: ${fmt.format(stat.totalOut)})."
                else ->
                    "In and Out amounts are evenly balanced at ${fmt.format(stat.totalIn)} across ${stat.activities.size} entries."
            }

            AiFamilyBalanceInsight(
                familyDocId = stat.family.id,
                familyId = stat.family.familyId,
                familyName = stat.family.name,
                totalIn = stat.totalIn,
                totalOut = stat.totalOut,
                netBalance = stat.net,
                entriesCount = stat.activities.size,
                eventsCount = stat.events.size,
                insight = insight
            )
        }

        val recommendations = buildList {
            if (topNet != null && lowestNet != null && topNet.family.id != lowestNet.family.id) {
                add("${topNet.family.name} leads with a net balance of ${if (topNet.net >= 0) "+" else ""}${fmt.format(topNet.net)}, compared to ${lowestNet.family.name} (${if (lowestNet.net >= 0) "+" else ""}${fmt.format(lowestNet.net)}).")
            }
            computedStats.filter { it.net > 0 }.forEach { surplus ->
                add("Consider reciprocating at the next occasion for ${surplus.family.name} (net received +${fmt.format(surplus.net)}).")
            }
            if (isEmpty()) {
                add("All compared families have balanced or zeroed ledgers.")
            }
        }.take(3)

        val headline = if (topNet != null) {
            "${topNet.family.name} leads Net Balance (${if (topNet.net >= 0) "+" else ""}${fmt.format(topNet.net)})"
        } else {
            "In vs Out Balance Comparison"
        }

        val summary = "Compared ${selectedFamilies.size} families: combined In is ${fmt.format(computedStats.sumOf { it.totalIn })} and combined Out is ${fmt.format(computedStats.sumOf { it.totalOut })}."

        return Result.success(
            AiBalanceComparisonResult(
                headline = headline,
                summary = summary,
                higherNetFamilyName = topNet?.family?.name.orEmpty(),
                familyBreakdowns = breakdowns,
                recommendations = recommendations
            )
        )
    }

    private fun activitiesForFamily(
        family: FamilyProfile,
        activities: List<ActivityRecord>
    ): List<ActivityRecord> {
        return activities.filter { act ->
            act.familyDocId == family.id ||
                (act.familyId.isNotBlank() && family.familyId.isNotBlank() &&
                    act.familyId == family.familyId &&
                    act.name.equals(family.name, ignoreCase = true)) ||
                (act.familyDocId.isNullOrBlank() && act.name.equals(family.name, ignoreCase = true) &&
                    (family.fatherName.isBlank() || act.fatherName.equals(family.fatherName, ignoreCase = true)))
        }
    }

    private fun buildOnlineSearchPrompt(
        query: String,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val familiesArr = JSONArray().apply {
            families.take(120).forEach { fam ->
                val famActs = activitiesForFamily(fam, activities)
                val inSum = famActs.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.neondraAmount }
                val outSum = famActs.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.neondraAmount }
                put(
                    JSONObject().apply {
                        put("id", fam.id)
                        put("fn", fam.familyId)
                        put("name", fam.name)
                        put("fatherName", fam.fatherName)
                        put("address", fam.address)
                        put("totalIn", inSum)
                        put("totalOut", outSum)
                        put("netBalance", inSum - outSum)
                        put("entriesCount", famActs.size)
                    }
                )
            }
        }

        val eventsArr = JSONArray().apply {
            events.take(120).forEach { evt ->
                put(
                    JSONObject().apply {
                        put("id", evt.id)
                        put("title", evt.title)
                        put("date", dateFmt.format(Date(evt.eventDateMillis)))
                        put("category", evt.category)
                        put("location", evt.location)
                        put("familyName", evt.familyName)
                        put("description", evt.description)
                    }
                )
            }
        }

        val activitiesArr = JSONArray().apply {
            activities.sortedByDescending { it.eventDateMillis }.take(160).forEach { act ->
                put(
                    JSONObject().apply {
                        put("id", act.id)
                        put("familyDocId", act.familyDocId ?: "")
                        put("fn", act.familyId)
                        put("name", act.name)
                        put("address", act.address)
                        put("category", act.eventType)
                        put("detail", act.eventDetail)
                        put("date", dateFmt.format(Date(act.eventDateMillis)))
                        put("direction", act.direction)
                        put("homeItems", act.homeItems)
                        put("amount", act.neondraAmount)
                    }
                )
            }
        }

        return """
            You are the AI assistant inside "Life Line", an offline-first family occasion and gift/neondra ledger app.
            Today's date is ${dateFmt.format(Date(System.currentTimeMillis()))}.
            Answer the user's question accurately using ONLY the user's offline Room database records below.
            Return exact matching document IDs (`id` fields) in matchedFamilyIds, matchedActivityIds, and matchedEventIds.

            USER QUESTION:
            $query

            OFFLINE FAMILIES (${families.size} total):
            $familiesArr

            OFFLINE OCCASIONS / EVENTS (${events.size} total):
            $eventsArr

            OFFLINE LEDGER ENTRIES (${activities.size} total):
            $activitiesArr
        """.trimIndent()
    }

    private fun buildSingleFamilyPrompt(base: SingleFamilyInOutComparison): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val inArr = JSONArray().apply {
            base.inEntries.take(80).forEach { act ->
                put(
                    JSONObject().apply {
                        put("date", dateFmt.format(Date(act.eventDateMillis)))
                        put("category", act.eventType)
                        put("detail", act.eventDetail)
                        put("homeItems", act.homeItems)
                        put("amount", act.neondraAmount)
                    }
                )
            }
        }
        val outArr = JSONArray().apply {
            base.outEntries.take(80).forEach { act ->
                put(
                    JSONObject().apply {
                        put("date", dateFmt.format(Date(act.eventDateMillis)))
                        put("category", act.eventType)
                        put("detail", act.eventDetail)
                        put("homeItems", act.homeItems)
                        put("amount", act.neondraAmount)
                    }
                )
            }
        }

        return """
            You are the AI analyst inside "Life Line", a family occasion and gift ledger app.
            Compare the In (Received) entries vs Out (Given) entries WITHIN THIS SINGLE FAMILY using their offline ledger records.
            Explain the exact reciprocity balance, which categories or occasions have more In vs Out, any notable gifts/items, and practical next steps.

            FAMILY:
            Name: ${base.family.name} (FN ${base.family.familyId}), Address: ${base.family.address}
            Total In (Received): ${base.totalIn} across ${base.inEntries.size} entries
            Total Out (Given): ${base.totalOut} across ${base.outEntries.size} entries
            Net Balance (In - Out): ${base.netBalance}

            IN ENTRIES (RECEIVED):
            $inArr

            OUT ENTRIES (GIVEN):
            $outArr
        """.trimIndent()
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotEmpty()) add(item)
            }
        }
    }

    private data class ComputedFamilyStat(
        val family: FamilyProfile,
        val activities: List<ActivityRecord>,
        val events: List<EventEntry>,
        val totalIn: Long,
        val totalOut: Long,
        val net: Long
    )

    private fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getInstance()
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    private companion object {
        const val TAG = "AiAssistantRepository"
        const val MODEL_NAME = "gemini-3.8-flash"
        const val AI_TIMEOUT_MILLIS = 60_000L
        const val MISSING_FIREBASE_CONFIG_MESSAGE =
            "Online AI requires google-services.json. Download the APK directly from AI Studio's Settings menu, or add GOOGLE_SERVICES_JSON as a GitHub Repository Secret. Offline Quick Filter and local Family Compare continue to work offline."
    }
}
