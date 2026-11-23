package com.example.ai

import android.util.Log
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.EventEntry
import com.example.data.FamilyProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
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

class AiAssistantRepository {

    private val searchSchema = Schema.obj(
        properties = mapOf(
            "answerSummary" to Schema.string(
                description = "Helpful natural-language English summary directly answering the search query based on the provided families, ledger activities, and events."
            ),
            "matchedFamilyIds" to Schema.array(
                items = Schema.string(),
                description = "Exact document IDs (id field) of matching families from the input data."
            ),
            "matchedActivityIds" to Schema.array(
                items = Schema.string(),
                description = "Exact document IDs (id field) of matching ledger activities from the input data."
            ),
            "matchedEventIds" to Schema.array(
                items = Schema.string(),
                description = "Exact document IDs (id field) of matching events from the input data."
            ),
            "keyInsights" to Schema.array(
                items = Schema.string(),
                description = "Up to 4 concise bullet-point highlights with totals, dates, or notable patterns found."
            )
        )
    )

    private val compareSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(
                description = "Short headline summarizing the comparison between the selected families' In and Out balances."
            ),
            "summary" to Schema.string(
                description = "Detailed English comparison explaining In vs Out balances, reciprocity, net surplus/deficit, and linked events."
            ),
            "higherNetFamilyName" to Schema.string(
                description = "Name of the family with the highest net balance (In minus Out)."
            ),
            "familyNotes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "familyDocId" to Schema.string(description = "Exact family document id from input."),
                        "insight" to Schema.string(description = "1-2 sentence balance & gift reciprocity observation for this family.")
                    )
                ),
                description = "One insight object per compared family."
            ),
            "recommendations" to Schema.array(
                items = Schema.string(),
                description = "Up to 3 practical suggestions for settling balances or upcoming obligations."
            )
        )
    )

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

    private val compareModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = compareSchema
                temperature = 0.25f
            }
        )
    }

    suspend fun searchFamiliesAndEvents(
        query: String,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<AiSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter a search query."))
        }

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val familyContext = families.take(80).joinToString("\n") { fam ->
            val famActs = activities.filter {
                it.familyDocId == fam.id ||
                    (it.familyId.isNotBlank() && it.familyId == fam.familyId && it.name.equals(fam.name, ignoreCase = true))
            }
            val inTotal = famActs.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.neondraAmount }
            val outTotal = famActs.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.neondraAmount }
            "- id=${fam.id} | FN=${fam.familyId} | name=${fam.name} | father=${fam.fatherName} | address=${fam.address} | IN=$inTotal | OUT=$outTotal | NET=${inTotal - outTotal} | entries=${famActs.size}"
        }.ifEmpty { "None" }

        val eventContext = events.take(80).joinToString("\n") { evt ->
            "- id=${evt.id} | title=${evt.title} | date=${dateFmt.format(Date(evt.eventDateMillis))} | category=${evt.category} | location=${evt.location} | family=${evt.familyName} | description=${evt.description.take(160)}"
        }.ifEmpty { "None" }

        val activityContext = activities.take(100).joinToString("\n") { act ->
            "- id=${act.id} | name=${act.name} (FN ${act.familyId}) | type=${act.eventType} | detail=${act.eventDetail} | date=${dateFmt.format(Date(act.eventDateMillis))} | dir=${act.direction} | amount=${act.neondraAmount} | items=${act.homeItems.take(80)} | address=${act.address}"
        }.ifEmpty { "None" }

        val prompt = """
You are the smart AI assistant inside the Occasion Diary app.
Search the user's actual records below and answer their query accurately in English.
Only return IDs that literally exist in the input lists below. If nothing matches, explain clearly and return empty ID arrays.

USER QUERY:
"$cleanQuery"

FAMILIES:
$familyContext

EVENTS (New Event entries):
$eventContext

LEDGER ACTIVITIES (In/Out transactions & gifts):
$activityContext
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                searchModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("AI model returned an empty response."))
            val json = JSONObject(rawJson)
            val validFamilyIds = families.map { it.id }.toSet()
            val validActivityIds = activities.map { it.id }.toSet()
            val validEventIds = events.map { it.id }.toSet()

            val matchedFam = json.optStringArray("matchedFamilyIds").filter { it in validFamilyIds }
            val matchedAct = json.optStringArray("matchedActivityIds").filter { it in validActivityIds }
            val matchedEvt = json.optStringArray("matchedEventIds").filter { it in validEventIds }

            Result.success(
                AiSearchResult(
                    query = cleanQuery,
                    answerSummary = json.optString("answerSummary").ifBlank { "Search complete." },
                    matchedFamilyIds = matchedFam,
                    matchedActivityIds = matchedAct,
                    matchedEventIds = matchedEvt,
                    keyInsights = json.optStringArray("keyInsights")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI search timed out", e)
            Result.failure(IllegalStateException("AI search timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI search failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "AI search failed."))
        }
    }

    suspend fun compareFamilyBalances(
        selectedFamilies: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<AiBalanceComparisonResult> {
        if (selectedFamilies.size < 2) {
            return Result.failure(IllegalArgumentException("Select at least 2 families to compare balances."))
        }

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

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

        val contextBlock = computedStats.joinToString("\n\n") { stat ->
            val recentLines = stat.activities.take(15).joinToString("\n") { act ->
                "  * ${dateFmt.format(Date(act.eventDateMillis))} | ${act.eventType} | ${act.direction} ${act.neondraAmount} | ${act.homeItems}"
            }.ifEmpty { "  * No ledger entries yet" }
            val evtLines = stat.events.take(8).joinToString("\n") { evt ->
                "  * Event: ${evt.title} (${dateFmt.format(Date(evt.eventDateMillis))})"
            }.ifEmpty { "  * No standalone events" }
            """
Family Doc ID: ${stat.family.id}
Name: ${stat.family.name} (FN ${stat.family.familyId}, Address: ${stat.family.address})
Total IN (Received): ${stat.totalIn}
Total OUT (Given): ${stat.totalOut}
Net Balance (IN - OUT): ${stat.net}
Entries Count: ${stat.activities.size}, Linked Events: ${stat.events.size}
Recent Ledger Activity:
$recentLines
Linked Events:
$evtLines
            """.trimIndent()
        }

        val prompt = """
You are the financial & family occasion analyst in Occasion Diary.
Compare the In (Received) and Out (Given) balances, net differences, gift patterns, and linked events across these selected families.
Be precise with numbers—use the exact Total IN, Total OUT, and Net Balance provided.

SELECTED FAMILIES DATA:
$contextBlock
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                compareModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("AI model returned an empty comparison."))
            val json = JSONObject(rawJson)

            val noteMap = buildMap<String, String> {
                val arr = json.optJSONArray("familyNotes")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val docId = obj.optString("familyDocId")
                        val insight = obj.optString("insight")
                        if (docId.isNotBlank() && insight.isNotBlank()) {
                            put(docId, insight)
                        }
                    }
                }
            }

            val breakdowns = computedStats.map { stat ->
                AiFamilyBalanceInsight(
                    familyDocId = stat.family.id,
                    familyId = stat.family.familyId,
                    familyName = stat.family.name,
                    totalIn = stat.totalIn,
                    totalOut = stat.totalOut,
                    netBalance = stat.net,
                    entriesCount = stat.activities.size,
                    eventsCount = stat.events.size,
                    insight = noteMap[stat.family.id]
                        ?: "In: ${stat.totalIn}, Out: ${stat.totalOut}, Net: ${stat.net}."
                )
            }

            val topNetFamily = computedStats.maxByOrNull { it.net }?.family?.name.orEmpty()

            Result.success(
                AiBalanceComparisonResult(
                    headline = json.optString("headline").ifBlank { "Family In vs Out Balance Comparison" },
                    summary = json.optString("summary").ifBlank { "Comparison generated across ${selectedFamilies.size} families." },
                    higherNetFamilyName = json.optString("higherNetFamilyName").ifBlank { topNetFamily },
                    familyBreakdowns = breakdowns,
                    recommendations = json.optStringArray("recommendations")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI balance comparison timed out", e)
            Result.failure(IllegalStateException("Balance comparison timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI balance comparison failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "AI comparison failed."))
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

    private fun JSONObject.optStringArray(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val value = arr.optString(i).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private companion object {
        const val TAG = "AiAssistantRepo"
        const val MODEL_NAME = "gemini-3.8-flash"
    }
}
