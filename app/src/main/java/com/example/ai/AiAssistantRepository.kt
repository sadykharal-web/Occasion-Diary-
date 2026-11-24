package com.example.ai

import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.EventEntry
import com.example.data.FamilyProfile
import com.example.ui.screens.localDayStartMillis
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
    val keyInsights: List<String>
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

/**
 * 100% offline smart search & family balance comparison engine operating directly
 * on the device's local SQLite records without requiring network or Firebase.
 */
class AiAssistantRepository {

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
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }

        val fmt = NumberFormat.getNumberInstance(Locale.US)
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        val todayStart = localDayStartMillis(System.currentTimeMillis())

        val familyStats = families.map { fam ->
            val famActs = activities.filter { act ->
                act.familyDocId == fam.id ||
                    (act.familyId.isNotBlank() && act.familyId == fam.familyId &&
                        act.name.equals(fam.name, ignoreCase = true))
            }
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
            "No offline records matched \"$cleanQuery\". Try searching a family name, FN ID, city/address, event category, or keyword like 'upcoming' or 'highest in'."
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
                keyInsights = insights
            )
        )
    }

    fun compareFamilyBalances(
        selectedFamilies: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<AiBalanceComparisonResult> {
        if (selectedFamilies.size < 2) {
            return Result.failure(IllegalArgumentException("Select at least 2 families to compare balances."))
        }

        val fmt = NumberFormat.getNumberInstance(Locale.US)
        val computedStats = selectedFamilies.map { fam ->
            val famActs = activities.filter { act ->
                act.familyDocId == fam.id ||
                    (act.familyId.isNotBlank() && act.familyId == fam.familyId &&
                        act.name.equals(fam.name, ignoreCase = true))
            }
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
            "Offline In vs Out Balance Comparison"
        }

        val summary = "Compared ${selectedFamilies.size} families offline: combined In is ${fmt.format(computedStats.sumOf { it.totalIn })} and combined Out is ${fmt.format(computedStats.sumOf { it.totalOut })}."

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

    private data class ComputedFamilyStat(
        val family: FamilyProfile,
        val activities: List<ActivityRecord>,
        val events: List<EventEntry>,
        val totalIn: Long,
        val totalOut: Long,
        val net: Long
    )
}
