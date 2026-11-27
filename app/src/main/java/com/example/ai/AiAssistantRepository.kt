package com.example.ai

import android.util.Log
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.EventEntry
import com.example.data.FamilyIdentityMatcher
import com.example.data.FamilyProfile
import com.example.ui.screens.localDayStartMillis
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
    val recommendations: List<String>,
    val usedOnlineAi: Boolean = false
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

data class AiFollowUpChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val isOnlineAi: Boolean = true,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Combines online Gemini AI search, In/Out balance analysis, and follow-up comparison chat
 * (via Firebase AI Logic over the device's offline Room SQLite snapshot, always responding in Urdu)
 * with deterministic local Urdu calculations.
 */
class AiAssistantRepository {

    private val searchSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "answerSummary" to Schema.string(
                    description = "Clear direct answer strictly in natural Urdu (اردو) based on the provided offline Life Line diary records."
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
                    description = "Up to 4 concise bullet insights strictly in natural Urdu (اردو) with exact numbers, names, or dates."
                )
            )
        )
    }

    private val singleFamilySchema by lazy {
        Schema.obj(
            properties = mapOf(
                "headline" to Schema.string(
                    description = "Short headline strictly in natural Urdu (اردو) comparing In (آمدن/وصول) vs Out (خرچ/دیے گئے) entries for this single family."
                ),
                "summary" to Schema.string(
                    description = "Concise 2-3 sentence summary strictly in natural Urdu (اردو) comparing the family's In entries against their Out entries, categories, and net reciprocity."
                ),
                "insights" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 4 specific insights strictly in natural Urdu (اردو) comparing In and Out entries, amounts, occasions, and next steps."
                )
            )
        )
    }

    private val multiFamilySchema by lazy {
        Schema.obj(
            properties = mapOf(
                "headline" to Schema.string(
                    description = "Short headline strictly in natural Urdu (اردو) summarizing the In vs Out balance comparison across the selected families."
                ),
                "summary" to Schema.string(
                    description = "Concise 2-3 sentence summary strictly in natural Urdu (اردو) comparing total In, total Out, and net balances across the selected families."
                ),
                "familyInsights" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "familyDocId" to Schema.string(
                                description = "Exact familyDocId from the provided selected families list."
                            ),
                            "insight" to Schema.string(
                                description = "One concise Urdu (اردو) insight sentence for this specific family's In vs Out balance."
                            )
                        )
                    ),
                    description = "One insight entry per selected family."
                ),
                "recommendations" to Schema.array(
                    items = Schema.string(),
                    description = "2 to 3 practical recommendations strictly in natural Urdu (اردو) regarding reciprocity and upcoming occasions."
                )
            )
        )
    }

    private val searchModel by lazy { createJsonModel(PRIMARY_MODEL_NAME, searchSchema, useLowThinking = true) }
    private val searchFallbackModel by lazy { createJsonModel(FAST_FALLBACK_MODEL_NAME, searchSchema, useLowThinking = false) }

    private val singleFamilyModel by lazy { createJsonModel(PRIMARY_MODEL_NAME, singleFamilySchema, useLowThinking = true) }
    private val singleFamilyFallbackModel by lazy { createJsonModel(FAST_FALLBACK_MODEL_NAME, singleFamilySchema, useLowThinking = false) }

    private val multiFamilyModel by lazy { createJsonModel(PRIMARY_MODEL_NAME, multiFamilySchema, useLowThinking = true) }
    private val multiFamilyFallbackModel by lazy { createJsonModel(FAST_FALLBACK_MODEL_NAME, multiFamilySchema, useLowThinking = false) }

    private val followUpChatModel by lazy { createChatModel(PRIMARY_MODEL_NAME, useLowThinking = true) }
    private val followUpChatFallbackModel by lazy { createChatModel(FAST_FALLBACK_MODEL_NAME, useLowThinking = false) }

    private fun createJsonModel(
        modelName: String,
        schema: Schema,
        useLowThinking: Boolean
    ): GenerativeModel {
        return Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                maxOutputTokens = 1536
                if (useLowThinking) {
                    thinkingConfig = thinkingConfig {
                        thinkingLevel = ThinkingLevel.LOW
                    }
                }
            }
        )
    }

    private fun createChatModel(
        modelName: String,
        useLowThinking: Boolean
    ): GenerativeModel {
        return Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                maxOutputTokens = 1536
                if (useLowThinking) {
                    thinkingConfig = thinkingConfig {
                        thinkingLevel = ThinkingLevel.LOW
                    }
                }
            }
        )
    }

    private suspend fun collectStreamTextWithFallback(
        primaryModel: GenerativeModel,
        fallbackModel: GenerativeModel,
        prompt: String,
        operationLabel: String
    ): String {
        return try {
            withTimeout(PRIMARY_TIMEOUT_MILLIS) {
                val sb = StringBuilder()
                primaryModel.generateContentStream(prompt).collect { chunk ->
                    chunk.text?.let { sb.append(it) }
                }
                sb.toString()
            }
        } catch (primaryTimeout: TimeoutCancellationException) {
            Log.w(TAG, "$operationLabel primary model timed out after ${PRIMARY_TIMEOUT_MILLIS}ms; retrying with $FAST_FALLBACK_MODEL_NAME", primaryTimeout)
            withTimeout(FALLBACK_TIMEOUT_MILLIS) {
                val sb = StringBuilder()
                fallbackModel.generateContentStream(prompt).collect { chunk ->
                    chunk.text?.let { sb.append(it) }
                }
                sb.toString()
            }
        }
    }

    private suspend fun collectChatReplyWithFallback(
        history: List<Content>,
        question: String
    ): String {
        return try {
            withTimeout(PRIMARY_TIMEOUT_MILLIS) {
                val chat = followUpChatModel.startChat(history = history)
                val sb = StringBuilder()
                chat.sendMessageStream(question).collect { chunk ->
                    chunk.text?.let { sb.append(it) }
                }
                sb.toString()
            }
        } catch (primaryTimeout: TimeoutCancellationException) {
            Log.w(TAG, "Follow-up chat primary model timed out; retrying with $FAST_FALLBACK_MODEL_NAME", primaryTimeout)
            withTimeout(FALLBACK_TIMEOUT_MILLIS) {
                val chat = followUpChatFallbackModel.startChat(history = history)
                val sb = StringBuilder()
                chat.sendMessageStream(question).collect { chunk ->
                    chunk.text?.let { sb.append(it) }
                }
                sb.toString()
            }
        }
    }

    suspend fun searchFamiliesAndEventsOnline(
        query: String,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<AiSearchResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a question or search query."))
        }

        if (!isFirebaseConfigured()) {
            return@withContext Result.failure(
                IllegalStateException(MISSING_FIREBASE_CONFIG_MESSAGE)
            )
        }

        try {
            val prompt = buildOnlineSearchPrompt(cleanQuery, families, activities, events)
            val rawJson = collectStreamTextWithFallback(
                primaryModel = searchModel,
                fallbackModel = searchFallbackModel,
                prompt = prompt,
                operationLabel = "Online AI search"
            ).takeIf { it.isNotBlank() }
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
                "آن لائن اے آئی نے ${families.size} خاندانوں، ${events.size} تقریبات، اور ${activities.size} کھاتہ اندراجات کا تجزیہ کیا۔"
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
                IllegalStateException("Online AI search timed out. Please check your internet connection or use Offline Quick Filter.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Online AI search failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Online AI search failed. Please check your internet connection.")
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

        val totalIn = inEntries.sumOf { it.totalAmount }
        val totalOut = outEntries.sumOf { it.totalAmount }
        val netBalance = totalIn - totalOut

        val categoryBreakdowns = famActs
            .groupBy { it.eventType.trim().ifEmpty { "General" } }
            .map { (catName, records) ->
                val catIn = records.filter { it.parsedDirection == ActivityDirection.IN }
                val catOut = records.filter { it.parsedDirection == ActivityDirection.OUT }
                CategoryInOutBreakdown(
                    categoryName = catName,
                    totalIn = catIn.sumOf { it.totalAmount },
                    totalOut = catOut.sumOf { it.totalAmount },
                    inCount = catIn.size,
                    outCount = catOut.size
                )
            }
            .sortedByDescending { it.totalIn + it.totalOut }

        val headline = when {
            famActs.isEmpty() -> "${family.name}: ابھی تک آمدن یا خرچ کا کوئی اندراج موجود نہیں"
            netBalance > 0L -> "${family.name}: آمدن خرچ سے +${fmt.format(netBalance)} روپے زیادہ ہے"
            netBalance < 0L -> "${family.name}: خرچ آمدن سے ${fmt.format(-netBalance)} روپے زیادہ ہے"
            else -> "${family.name}: آمدن اور خرچ برابر ہیں (${fmt.format(totalIn)} روپے)"
        }

        val summary = if (famActs.isEmpty()) {
            "${family.name} کے لیے موبائل میں ابھی کوئی آف لائن اندراج محفوظ نہیں ہے۔"
        } else {
            "${family.name} کی کل ${inEntries.size} آمدن (وصول) اندراجات میں ${fmt.format(totalIn)} روپے اور ${outEntries.size} خرچ (دیے گئے) اندراجات میں ${fmt.format(totalOut)} روپے درج ہیں، اور خالص بیلنس ${if (netBalance >= 0) "+" else ""}${fmt.format(netBalance)} روپے ہے۔"
        }

        val insights = emptyList<String>()

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
    ): Result<SingleFamilyInOutComparison> = withContext(Dispatchers.IO) {
        val base = compareWithinSingleFamilyLocal(family, activities, events)
        if (!isFirebaseConfigured()) {
            return@withContext Result.failure(
                IllegalStateException(MISSING_FIREBASE_CONFIG_MESSAGE)
            )
        }
        try {
            val prompt = buildSingleFamilyPrompt(base)
            val rawJson = collectStreamTextWithFallback(
                primaryModel = singleFamilyModel,
                fallbackModel = singleFamilyFallbackModel,
                prompt = prompt,
                operationLabel = "Online AI single-family analysis"
            ).takeIf { it.isNotBlank() }
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
                IllegalStateException("Online AI comparison timed out. Please check your internet connection and retry.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Online AI single-family analysis failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Online AI comparison failed. Please check your connection.")
            )
        }
    }

    /**
     * Fast local offline filter & keyword search across Room SQLite records.
     * Supports both English and Urdu queries and always outputs Urdu summaries/insights.
     */
    fun searchFamiliesAndEvents(
        query: String,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<AiSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter a question or search query."))
        }

        val lowerQuery = cleanQuery.lowercase(Locale.ENGLISH)
        val stopWords = setOf(
            "the", "and", "with", "who", "what", "which", "has", "have", "show",
            "find", "all", "for", "from", "are", "their", "list", "entries", "records",
            "اور", "کے", "کی", "کا", "کو", "میں", "سے", "پر", "ہیں", "ہے", "سب", "دکھائیں", "کون", "سا", "والے", "والی"
        )
        val baseTokens = lowerQuery
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.trim() }
            .filter { (it.length >= 2 || it.all { ch -> ch.isDigit() }) && it !in stopWords }

        val bilingualSynonyms = mapOf(
            "shadi" to listOf("شادی", "wedding"),
            "wedding" to listOf("شادی", "shadi"),
            "شادی" to listOf("shadi", "wedding"),
            "nikah" to listOf("نکاح"),
            "نکاح" to listOf("nikah"),
            "aqiqah" to listOf("عقیقہ"),
            "عقیقہ" to listOf("aqiqah"),
            "birthday" to listOf("سالگرہ"),
            "سالگرہ" to listOf("birthday"),
            "cash" to listOf("نقد"),
            "نقد" to listOf("cash"),
            "lady" to listOf("زنانہ"),
            "ladies" to listOf("زنانہ"),
            "زنانہ" to listOf("lady", "ladies"),
            "gents" to listOf("مردانہ"),
            "مردانہ" to listOf("gents"),
            "kids" to listOf("بچگانہ"),
            "child" to listOf("بچگانہ"),
            "bachgana" to listOf("بچگانہ"),
            "بچگانہ" to listOf("kids", "child"),
            "suit" to listOf("سوٹ"),
            "suits" to listOf("سوٹ"),
            "سوٹ" to listOf("suit", "suits"),
            "fruit" to listOf("پھل", "آم", "سیب", "کیلا", "مالٹا", "انگور", "انار", "امرود"),
            "fruits" to listOf("پھل", "آم", "سیب", "کیلا", "مالٹا", "انگور", "انار", "امرود"),
            "پھل" to listOf("fruits", "fruit"),
            "mango" to listOf("آم"),
            "آم" to listOf("mango"),
            "apple" to listOf("سیب"),
            "سیب" to listOf("apple"),
            "banana" to listOf("کیلا"),
            "کیلا" to listOf("banana"),
            "orange" to listOf("مالٹا", "کینو"),
            "مالٹا" to listOf("orange"),
            "grapes" to listOf("انگور"),
            "انگور" to listOf("grapes"),
            "sweet" to listOf("مٹھائی"),
            "sweets" to listOf("مٹھائی"),
            "mithai" to listOf("مٹھائی"),
            "مٹھائی" to listOf("sweets", "sweet"),
            "kg" to listOf("کلوگرام", "kilogram"),
            "kilogram" to listOf("کلوگرام", "kg"),
            "کلوگرام" to listOf("kg", "kilogram"),
            "neondra" to listOf("نیوندرا"),
            "نیوندرا" to listOf("neondra")
        )
        val tokens = (baseTokens + baseTokens.flatMap { bilingualSynonyms[it].orEmpty() }).distinct()

        val fmt = NumberFormat.getNumberInstance(Locale.US)
        val dateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        val todayStart = localDayStartMillis(System.currentTimeMillis())

        val familyStats = families.map { fam ->
            val famActs = activitiesForFamily(fam, activities)
            val inTotal = famActs.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.totalAmount }
            val outTotal = famActs.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.totalAmount }
            Triple(fam, Triple(inTotal, outTotal, inTotal - outTotal), famActs.size)
        }

        val wantsHighestIn = ("highest" in lowerQuery && "in" in lowerQuery) ||
            ("سب سے زیادہ" in cleanQuery && ("آمدن" in cleanQuery || "وصول" in cleanQuery || "بیلنس" in cleanQuery)) ||
            ("زیادہ" in cleanQuery && "آمدن" in cleanQuery)
        val wantsNegative = "negative" in lowerQuery || "owe" in lowerQuery || "deficit" in lowerQuery ||
            "منفی" in cleanQuery || "کم بیلنس" in cleanQuery || "واجب" in cleanQuery
        val wantsPositive = "positive" in lowerQuery || "surplus" in lowerQuery || "مثبت" in cleanQuery
        val wantsUpcoming = "upcoming" in lowerQuery || "future" in lowerQuery || "next" in lowerQuery ||
            "آنے والی" in cleanQuery || "آئندہ" in cleanQuery

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
                val haystack = "${fam.familyId} fn${fam.familyId} ${fam.name} ${fam.fatherName} ${fam.address}".lowercase(Locale.ENGLISH)
                tokens.any { it in haystack } || lowerQuery in haystack
            }
        }

        val genericUpcomingWords = setOf(
            "upcoming", "events", "event", "occasions", "occasion",
            "آنے", "آئندہ", "تقریب", "تقریبات", "ایونٹس"
        )
        val matchedEvents = if (wantsUpcoming && tokens.none { it !in genericUpcomingWords }) {
            events.filter { it.eventDateMillis >= todayStart }.sortedBy { it.eventDateMillis }
        } else {
            events.filter { evt ->
                val haystack = "${evt.title} ${evt.description} ${evt.category} ${evt.location} ${evt.familyName}".lowercase(Locale.ENGLISH)
                val matchesText = tokens.any { it in haystack } || lowerQuery in haystack
                val matchesUpcoming = !wantsUpcoming || evt.eventDateMillis >= todayStart
                matchesText && matchesUpcoming
            }
        }

        val wantsIn = "in" in tokens || "received" in tokens || "آمدن" in cleanQuery || "وصول" in cleanQuery
        val wantsOut = "out" in tokens || "given" in tokens || "gifts" in tokens ||
            "خرچ" in cleanQuery || "دیے" in cleanQuery || "دیا" in cleanQuery || "تحفے" in cleanQuery
        val rawMatchedActivities = activities.filter { act ->
            val haystack = "${act.name} ${act.familyId} fn${act.familyId} ${act.fatherName} ${act.address} ${act.eventType} ${act.eventDetail} ${act.homeItems} ${act.direction}".lowercase(Locale.ENGLISH)
            val textHit = tokens.any { it in haystack } || lowerQuery in haystack
            val dirHit = (wantsIn && act.parsedDirection == ActivityDirection.IN) ||
                (wantsOut && act.parsedDirection == ActivityDirection.OUT)
            val upcomingHit = !wantsUpcoming || act.eventDateMillis >= todayStart
            (textHit || dirHit) && upcomingHit
        }.sortedByDescending { it.eventDateMillis }.take(50)

        val matchedActivities = if (matchedFamilies.size == 1 && !wantsIn && !wantsOut && !wantsUpcoming) {
            val singleFam = matchedFamilies.first()
            val singleFamHaystack = "${singleFam.familyId} fn${singleFam.familyId} ${singleFam.name} ${singleFam.fatherName} ${singleFam.address}".lowercase(Locale.ENGLISH)
            val nonFamilyTokens = tokens.filter { it !in singleFamHaystack }
            if (nonFamilyTokens.isEmpty()) {
                activitiesForFamily(singleFam, activities).sortedByDescending { it.eventDateMillis }
            } else {
                rawMatchedActivities
            }
        } else {
            rawMatchedActivities
        }

        val insights = buildList {
            if (matchedFamilies.isNotEmpty()) {
                val top = familyStats.firstOrNull { it.first.id == matchedFamilies.first().id }
                if (top != null) {
                    val net = top.second.third
                    val sign = if (net >= 0) "+" else ""
                    add("${top.first.name} (FN ${top.first.familyId}): آمدن ${fmt.format(top.second.first)}، خرچ ${fmt.format(top.second.second)}، خالص بیلنس $sign${fmt.format(net)} روپے۔")
                }
            }
            if (matchedEvents.isNotEmpty()) {
                val nextEvt = matchedEvents.minByOrNull { it.eventDateMillis }!!
                add("اگلی متعلقہ تقریب: ${nextEvt.title} (${dateFmt.format(Date(nextEvt.eventDateMillis))})۔")
            }
            if (matchedActivities.isNotEmpty()) {
                val inSum = matchedActivities.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.totalAmount }
                val outSum = matchedActivities.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.totalAmount }
                add("${matchedActivities.size} متعلقہ اندراجات ملے (آمدن: ${fmt.format(inSum)} | خرچ: ${fmt.format(outSum)} روپے)۔")
            }
        }

        val totalMatches = matchedFamilies.size + matchedEvents.size + matchedActivities.size
        val summary = if (totalMatches == 0) {
            "\"$cleanQuery\" کے لیے موبائل کے آف لائن ریکارڈ میں کوئی نتیجہ نہیں ملا۔ الفاظ بدل کر دوبارہ تلاش کریں۔"
        } else {
            "موبائل کے آف لائن ریکارڈ میں \"$cleanQuery\" کے مطابق ${matchedFamilies.size} خاندان، ${matchedEvents.size} تقریبات، اور ${matchedActivities.size} کھاتہ اندراجات ملے۔"
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
            return Result.failure(IllegalArgumentException("Please select at least 2 families to compare."))
        }

        val fmt = NumberFormat.getNumberInstance(Locale.US)
        val computedStats = selectedFamilies.map { fam ->
            val famActs = activitiesForFamily(fam, activities)
            val famEvents = events.filter { evt ->
                evt.familyDocId == fam.id || evt.familyName.equals(fam.name, ignoreCase = true)
            }
            val totalIn = famActs.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.totalAmount }
            val totalOut = famActs.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.totalAmount }
            val net = totalIn - totalOut
            ComputedFamilyStat(fam, famActs, famEvents, totalIn, totalOut, net)
        }

        val topNet = computedStats.maxByOrNull { it.net }
        val lowestNet = computedStats.minByOrNull { it.net }

        val breakdowns = computedStats.map { stat ->
            val insight = when {
                stat.activities.isEmpty() ->
                    "${stat.family.name} کے لیے ابھی کوئی کھاتہ اندراج موجود نہیں (${stat.events.size} منسلک تقریبات)۔"
                stat.net > 0 ->
                    "خالص بچت/وصولی +${fmt.format(stat.net)} روپے ہے (${stat.activities.size} اندراجات میں آمدن ${fmt.format(stat.totalIn)} اور خرچ ${fmt.format(stat.totalOut)} روپے)۔"
                stat.net < 0 ->
                    "دیے گئے اخراجات آمدن سے ${fmt.format(-stat.net)} روپے زیادہ ہیں (آمدن: ${fmt.format(stat.totalIn)}، خرچ: ${fmt.format(stat.totalOut)} روپے)۔"
                else ->
                    "${stat.activities.size} اندراجات میں آمدن اور خرچ ${fmt.format(stat.totalIn)} روپے پر برابر ہیں۔"
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
                add("${topNet.family.name} کا خالص بیلنس سب سے زیادہ (${if (topNet.net >= 0) "+" else ""}${fmt.format(topNet.net)} روپے) ہے، جبکہ ${lowestNet.family.name} کا بیلنس ${if (lowestNet.net >= 0) "+" else ""}${fmt.format(lowestNet.net)} روپے ہے۔")
            }
            computedStats.filter { it.net > 0 }.forEach { surplus ->
                add("${surplus.family.name} کی آنے والی تقریب میں نیوندرا دینے کا خیال رکھیں (خالص وصولی +${fmt.format(surplus.net)} روپے)۔")
            }
            if (isEmpty()) {
                add("تمام منتخب خاندانوں کا حساب برابر یا صفر ہے۔")
            }
        }.take(3)

        val headline = if (topNet != null) {
            "${topNet.family.name} خالص بیلنس میں سرفہرست (${if (topNet.net >= 0) "+" else ""}${fmt.format(topNet.net)} روپے)"
        } else {
            "آمدن بمقابلہ خرچ بیلنس کا موازنہ"
        }

        val summary = "${selectedFamilies.size} خاندانوں کا موازنہ: مجموعی آمدن ${fmt.format(computedStats.sumOf { it.totalIn })} روپے اور مجموعی خرچ ${fmt.format(computedStats.sumOf { it.totalOut })} روپے ہے۔"

        return Result.success(
            AiBalanceComparisonResult(
                headline = headline,
                summary = summary,
                higherNetFamilyName = topNet?.family?.name.orEmpty(),
                familyBreakdowns = breakdowns,
                recommendations = recommendations,
                usedOnlineAi = false
            )
        )
    }

    suspend fun analyzeMultiFamilyOnline(
        selectedFamilies: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): Result<AiBalanceComparisonResult> = withContext(Dispatchers.IO) {
        val baseResult = compareFamilyBalances(selectedFamilies, activities, events)
        val base = baseResult.getOrElse { return@withContext Result.failure(it) }
        if (!isFirebaseConfigured()) {
            return@withContext Result.failure(
                IllegalStateException(MISSING_FIREBASE_CONFIG_MESSAGE)
            )
        }
        try {
            val prompt = buildMultiFamilyPrompt(base, activities)
            val rawJson = collectStreamTextWithFallback(
                primaryModel = multiFamilyModel,
                fallbackModel = multiFamilyFallbackModel,
                prompt = prompt,
                operationLabel = "Online AI multi-family comparison"
            ).takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned an empty response.")

            val json = JSONObject(rawJson)
            val headline = json.optString("headline").ifBlank { base.headline }
            val summary = json.optString("summary").ifBlank { base.summary }
            val recommendations = json.optStringList("recommendations").ifEmpty { base.recommendations }

            val insightById = mutableMapOf<String, String>()
            val insightsArr = json.optJSONArray("familyInsights")
            if (insightsArr != null) {
                for (i in 0 until insightsArr.length()) {
                    val obj = insightsArr.optJSONObject(i) ?: continue
                    val famId = obj.optString("familyDocId").trim()
                    val text = obj.optString("insight").trim()
                    if (famId.isNotEmpty() && text.isNotEmpty()) {
                        insightById[famId] = text
                    }
                }
            }

            val updatedBreakdowns = base.familyBreakdowns.map { item ->
                item.copy(insight = insightById[item.familyDocId] ?: item.insight)
            }

            Result.success(
                base.copy(
                    headline = headline,
                    summary = summary,
                    familyBreakdowns = updatedBreakdowns,
                    recommendations = recommendations,
                    usedOnlineAi = true
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Online AI multi-family comparison timed out", e)
            Result.failure(
                IllegalStateException("Online AI comparison timed out. Please check your internet connection and retry.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Online AI multi-family comparison failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Online AI comparison failed. Please check your connection.")
            )
        }
    }

    /**
     * Multi-turn follow-up chat with Gemini AI after Single-Family or Multi-Family comparison.
     * Answers strictly in natural Urdu (اردو) grounded in the compared family/families' offline data.
     */
    suspend fun askComparisonFollowUpOnline(
        question: String,
        comparisonContext: String,
        history: List<AiFollowUpChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a follow-up question."))
        }
        if (!isFirebaseConfigured()) {
            return@withContext Result.failure(IllegalStateException(MISSING_FIREBASE_CONFIG_MESSAGE))
        }

        try {
            val chatHistory = buildList {
                add(
                    content(role = "user") {
                        text(
                            """
                            You are the follow-up AI assistant inside "Life Line", an offline-first family occasion and Neondra ledger app.
                            CRITICAL LANGUAGE RULE: Regardless of whether the user writes in English, Roman Urdu, or Urdu, ALWAYS reply strictly in clear, natural URDU (اردو).
                            Answer accurately using ONLY the compared family/families' offline data below. Keep answers helpful, concise, and quote exact amounts, names, or FN numbers where helpful.

                            COMPARED DATA SNAPSHOT:
                            $comparisonContext
                            """.trimIndent()
                        )
                    }
                )
                add(
                    content(role = "model") {
                        text("جی بالکل، میں نے موازنے کا پورا ریکارڈ پڑھ لیا ہے۔ آپ جو بھی مزید سوال پوچھیں گے میں اس کا جواب اردو میں دوں گا۔")
                    }
                )
                history.takeLast(12).forEach { msg ->
                    add(
                        content(role = if (msg.isUser) "user" else "model") {
                            text(msg.text)
                        }
                    )
                }
            }

            val answer = collectChatReplyWithFallback(
                history = chatHistory,
                question = cleanQuestion
            ).trim().takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("AI returned an empty chat response.")
            Result.success(answer)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Follow-up AI chat timed out", e)
            Result.failure(
                IllegalStateException("AI chat request timed out. Please retry or use Offline.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Follow-up AI chat failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Online AI chat failed. Please check your connection.")
            )
        }
    }

    fun answerSingleFamilyFollowUpOffline(
        question: String,
        comparison: SingleFamilyInOutComparison
    ): String {
        val cleanQ = question.trim().lowercase(Locale.ENGLISH)
        val fmt = NumberFormat.getNumberInstance(Locale.US)
        val famName = comparison.family.name
        val fnSuffix = if (comparison.family.familyId.isNotBlank()) " (FN ${comparison.family.familyId})" else ""

        return when {
            "in" in cleanQ || "received" in cleanQ || "آمدن" in question || "وصول" in question -> {
                val topIn = comparison.inEntries.maxByOrNull { it.totalAmount }
                if (topIn == null) {
                    "$famName$fnSuffix سے ابھی تک کوئی آمدن (وصول) درج نہیں ہے۔"
                } else {
                    "$famName$fnSuffix کی کل آمدن ${comparison.inEntries.size} اندراجات میں ${fmt.format(comparison.totalIn)} روپے ہے۔ سب سے زیادہ رقم ${fmt.format(topIn.totalAmount)} روپے (${topIn.eventType}) پر وصول ہوئی۔"
                }
            }

            "out" in cleanQ || "given" in cleanQ || "spent" in cleanQ || "خرچ" in question || "دیا" in question || "دیے" in question -> {
                val topOut = comparison.outEntries.maxByOrNull { it.totalAmount }
                if (topOut == null) {
                    "$famName$fnSuffix کو ابھی تک کوئی خرچ یا نیوندرا نہیں دیا گیا۔"
                } else {
                    "$famName$fnSuffix کو کل ${comparison.outEntries.size} اندراجات میں ${fmt.format(comparison.totalOut)} روپے دیے گئے۔ سب سے بڑی رقم ${fmt.format(topOut.totalAmount)} روپے (${topOut.eventType}) تھی۔"
                }
            }

            "next" in cleanQ || "suggest" in cleanQ || "recommend" in cleanQ || "آئندہ" in question || "مشورہ" in question || "کتنا" in question -> {
                when {
                    comparison.netBalance > 0L ->
                        "$famName$fnSuffix کا خالص بیلنس +${fmt.format(comparison.netBalance)} روپے آپ کی طرف واجب ہے، یعنی انہوں نے زیادہ دیا ہے؛ آئندہ تقریب پر کم از کم ${fmt.format(comparison.netBalance)} روپے یا مناسب نیوندرا دینا بہتر رہے گا۔"
                    comparison.netBalance < 0L ->
                        "آپ نے $famName$fnSuffix کو آمدن کے مقابلے میں ${fmt.format(-comparison.netBalance)} روپے زیادہ دیے ہیں۔ آئندہ تقریب پر معمول کے مطابق مناسب رقم رکھی جا سکتی ہے۔"
                    else ->
                        "$famName$fnSuffix کے ساتھ آمدن اور خرچ ${fmt.format(comparison.totalIn)} روپے پر بالکل برابر ہیں۔"
                }
            }

            "category" in cleanQ || "event" in cleanQ || "wedding" in cleanQ || "قسم" in question || "تقریب" in question -> {
                val topCat = comparison.categoryBreakdowns.firstOrNull()
                if (topCat == null) {
                    "$famName$fnSuffix کے لیے ابھی کوئی تقریب یا قسم محفوظ نہیں ہے۔"
                } else {
                    "سب سے زیادہ لین دین \"${topCat.categoryName}\" میں ہوا (آمدن: ${fmt.format(topCat.totalIn)} روپے، خرچ: ${fmt.format(topCat.totalOut)} روپے)۔ کل ${comparison.categoryBreakdowns.size} اقسام موجود ہیں۔"
                }
            }

            else -> {
                val netPrefix = if (comparison.netBalance >= 0) "+" else ""
                "$famName$fnSuffix کا خلاصہ: کل آمدن ${fmt.format(comparison.totalIn)} روپے (${comparison.inEntries.size} اندراجات)، کل خرچ ${fmt.format(comparison.totalOut)} روپے (${comparison.outEntries.size} اندراجات)، اور خالص بیلنس $netPrefix${fmt.format(comparison.netBalance)} روپے ہے۔"
            }
        }
    }

    fun answerMultiFamilyFollowUpOffline(
        question: String,
        comparison: AiBalanceComparisonResult
    ): String {
        val cleanQ = question.trim().lowercase(Locale.ENGLISH)
        val fmt = NumberFormat.getNumberInstance(Locale.US)
        val topInFam = comparison.familyBreakdowns.maxByOrNull { it.totalIn }
        val topOutFam = comparison.familyBreakdowns.maxByOrNull { it.totalOut }
        val topNetFam = comparison.familyBreakdowns.maxByOrNull { it.netBalance }

        return when {
            "in" in cleanQ || "received" in cleanQ || "آمدن" in question || "وصول" in question || "زیادہ" in question -> {
                if (topInFam == null) "کوئی خاندان منتخب نہیں ہے۔"
                else "منتخب خاندانوں میں سب سے زیادہ آمدن (وصول) ${topInFam.familyName} (FN ${topInFam.familyId}) کی ہے: ${fmt.format(topInFam.totalIn)} روپے، جبکہ خالص بیلنس ${if (topInFam.netBalance >= 0) "+" else ""}${fmt.format(topInFam.netBalance)} روپے ہے۔"
            }

            "out" in cleanQ || "given" in cleanQ || "spent" in cleanQ || "خرچ" in question || "دیا" in question -> {
                if (topOutFam == null) "کوئی خاندان منتخب نہیں ہے۔"
                else "سب سے زیادہ خرچ (دیے گئے) ${topOutFam.familyName} (FN ${topOutFam.familyId}) کے لیے درج ہے: ${fmt.format(topOutFam.totalOut)} روپے۔"
            }

            else -> {
                val lead = if (topNetFam != null) {
                    "خالص بیلنس میں ${topNetFam.familyName} (FN ${topNetFam.familyId}) سرفہرست ہے (${if (topNetFam.netBalance >= 0) "+" else ""}${fmt.format(topNetFam.netBalance)} روپے)۔"
                } else ""
                "${comparison.summary} $lead".trim()
            }
        }
    }

    fun buildSingleFamilyChatContext(comp: SingleFamilyInOutComparison): String {
        val dateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        val obj = JSONObject().apply {
            put("familyDocId", comp.family.id)
            put("fn", comp.family.familyId)
            put("name", comp.family.name)
            put("fatherName", comp.family.fatherName)
            put("address", comp.family.address)
            put("totalIn", comp.totalIn)
            put("totalOut", comp.totalOut)
            put("netBalance", comp.netBalance)
            put(
                "categories",
                JSONArray().apply {
                    comp.categoryBreakdowns.forEach { cat ->
                        put(
                            JSONObject().apply {
                                put("category", cat.categoryName)
                                put("in", cat.totalIn)
                                put("out", cat.totalOut)
                                put("net", cat.netBalance)
                            }
                        )
                    }
                }
            )
            put(
                "inEntries",
                JSONArray().apply {
                    comp.inEntries.take(40).forEach { act ->
                        put(
                            JSONObject().apply {
                                put("date", dateFmt.format(Date(act.eventDateMillis)))
                                put("category", act.eventType)
                                put("detail", act.eventDetail)
                                put("amount", act.totalAmount)
                                put("items", act.homeItems)
                            }
                        )
                    }
                }
            )
            put(
                "outEntries",
                JSONArray().apply {
                    comp.outEntries.take(40).forEach { act ->
                        put(
                            JSONObject().apply {
                                put("date", dateFmt.format(Date(act.eventDateMillis)))
                                put("category", act.eventType)
                                put("detail", act.eventDetail)
                                put("amount", act.totalAmount)
                                put("items", act.homeItems)
                            }
                        )
                    }
                }
            )
        }
        return obj.toString()
    }

    fun buildMultiFamilyChatContext(
        comp: AiBalanceComparisonResult,
        activities: List<ActivityRecord>
    ): String {
        val dateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        val familiesArr = JSONArray().apply {
            comp.familyBreakdowns.forEach { item ->
                val recent = activities.filter { act ->
                    act.familyDocId == item.familyDocId ||
                        (act.familyId.isNotBlank() && act.familyId == item.familyId && act.name.equals(item.familyName, ignoreCase = true))
                }.sortedByDescending { it.eventDateMillis }.take(15)
                put(
                    JSONObject().apply {
                        put("fn", item.familyId)
                        put("name", item.familyName)
                        put("totalIn", item.totalIn)
                        put("totalOut", item.totalOut)
                        put("netBalance", item.netBalance)
                        put("entriesCount", item.entriesCount)
                        put("insight", item.insight)
                        put(
                            "recentEntries",
                            JSONArray().apply {
                                recent.forEach { act ->
                                    put(
                                        JSONObject().apply {
                                            put("date", dateFmt.format(Date(act.eventDateMillis)))
                                            put("category", act.eventType)
                                            put("direction", act.direction)
                                            put("amount", act.totalAmount)
                                            put("items", act.homeItems)
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            }
        }
        return JSONObject().apply {
            put("headline", comp.headline)
            put("summary", comp.summary)
            put("families", familiesArr)
        }.toString()
    }

    private fun activitiesForFamily(
        family: FamilyProfile,
        activities: List<ActivityRecord>
    ): List<ActivityRecord> {
        return activities.filter { act ->
            FamilyIdentityMatcher.activityBelongsToFamily(act, family)
        }
    }

    private fun buildOnlineSearchPrompt(
        query: String,
        families: List<FamilyProfile>,
        activities: List<ActivityRecord>,
        events: List<EventEntry>
    ): String {
        val dateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        val familiesArr = JSONArray().apply {
            families.take(60).forEach { fam ->
                val famActs = activitiesForFamily(fam, activities)
                val inSum = famActs.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.totalAmount }
                val outSum = famActs.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.totalAmount }
                put(
                    JSONObject().apply {
                        put("id", fam.id)
                        put("fn", fam.familyId)
                        put("name", fam.name)
                        if (fam.fatherName.isNotBlank()) put("father", fam.fatherName)
                        if (fam.address.isNotBlank()) put("addr", fam.address)
                        put("in", inSum)
                        put("out", outSum)
                        put("net", inSum - outSum)
                        put("count", famActs.size)
                    }
                )
            }
        }

        val eventsArr = JSONArray().apply {
            events.take(40).forEach { evt ->
                put(
                    JSONObject().apply {
                        put("id", evt.id)
                        put("title", evt.title)
                        put("date", dateFmt.format(Date(evt.eventDateMillis)))
                        if (evt.category.isNotBlank()) put("cat", evt.category)
                        if (evt.familyName.isNotBlank()) put("family", evt.familyName)
                    }
                )
            }
        }

        val activitiesArr = JSONArray().apply {
            activities.sortedByDescending { it.eventDateMillis }.take(80).forEach { act ->
                put(
                    JSONObject().apply {
                        put("id", act.id)
                        put("fn", act.familyId)
                        put("name", act.name)
                        if (act.eventType.isNotBlank()) put("type", act.eventType)
                        if (act.eventDetail.isNotBlank()) put("detail", act.eventDetail)
                        put("date", dateFmt.format(Date(act.eventDateMillis)))
                        put("dir", act.direction)
                        if (act.homeItems.isNotBlank()) put("items", act.homeItems)
                        put("amt", act.totalAmount)
                    }
                )
            }
        }

        return """
            You are the AI assistant inside "Life Line", an offline-first family occasion and Neondra ledger app.
            Today's date is ${dateFmt.format(Date(System.currentTimeMillis()))}.
            CRITICAL LANGUAGE REQUIREMENT: Even if the user asks in English or Roman Urdu, write ALL human-readable response fields (`answerSummary` and every item in `keyInsights`) strictly in clear, natural URDU (اردو). Keep IDs exact.
            Answer concisely using ONLY the offline database records below. Return exact matching document IDs (`id`) in matchedFamilyIds, matchedActivityIds, and matchedEventIds.

            USER QUESTION:
            $query

            OFFLINE FAMILIES (${families.size} total):
            $familiesArr

            OFFLINE EVENTS (${events.size} total):
            $eventsArr

            OFFLINE LEDGER ENTRIES (${activities.size} total):
            $activitiesArr
        """.trimIndent()
    }

    private fun buildSingleFamilyPrompt(base: SingleFamilyInOutComparison): String {
        val dateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        val catArr = JSONArray().apply {
            base.categoryBreakdowns.forEach { cat ->
                put(
                    JSONObject().apply {
                        put("type", cat.categoryName)
                        put("in", cat.totalIn)
                        put("inCount", cat.inCount)
                        put("out", cat.totalOut)
                        put("outCount", cat.outCount)
                    }
                )
            }
        }
        val inArr = JSONArray().apply {
            base.inEntries.take(25).forEach { act ->
                put(
                    JSONObject().apply {
                        put("date", dateFmt.format(Date(act.eventDateMillis)))
                        put("type", act.eventType)
                        if (act.eventDetail.isNotBlank()) put("detail", act.eventDetail)
                        if (act.homeItems.isNotBlank()) put("items", act.homeItems)
                        put("amt", act.totalAmount)
                    }
                )
            }
        }
        val outArr = JSONArray().apply {
            base.outEntries.take(25).forEach { act ->
                put(
                    JSONObject().apply {
                        put("date", dateFmt.format(Date(act.eventDateMillis)))
                        put("type", act.eventType)
                        if (act.eventDetail.isNotBlank()) put("detail", act.eventDetail)
                        if (act.homeItems.isNotBlank()) put("items", act.homeItems)
                        put("amt", act.totalAmount)
                    }
                )
            }
        }

        return """
            You are the AI analyst inside "Life Line", a family occasion and Neondra ledger app.
            CRITICAL LANGUAGE REQUIREMENT: Write `headline`, `summary`, and EVERY item in `insights` strictly in clear, concise, natural URDU (اردو).
            Compare the In (آمدن / وصول) vs Out (خرچ / دیے گئے) entries WITHIN THIS SINGLE FAMILY using the totals and entries below. Mention exact reciprocity balance, key event types, and practical next steps.

            FAMILY: ${base.family.name} (FN ${base.family.familyId}), Father: ${base.family.fatherName}, Address: ${base.family.address}
            Total In: ${base.totalIn} (${base.inEntries.size} entries) | Total Out: ${base.totalOut} (${base.outEntries.size} entries) | Net: ${base.netBalance}
            EVENT TYPE TOTALS: $catArr
            RECENT IN ENTRIES: $inArr
            RECENT OUT ENTRIES: $outArr
        """.trimIndent()
    }

    private fun buildMultiFamilyPrompt(
        base: AiBalanceComparisonResult,
        activities: List<ActivityRecord>
    ): String {
        val dateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        val famArr = JSONArray().apply {
            base.familyBreakdowns.forEach { fam ->
                val famActs = activities.filter { act ->
                    act.familyDocId == fam.familyDocId ||
                        (act.familyId.isNotBlank() && act.familyId == fam.familyId &&
                            act.name.equals(fam.familyName, ignoreCase = true))
                }.sortedByDescending { it.eventDateMillis }.take(12)

                val recentArr = JSONArray().apply {
                    famActs.forEach { act ->
                        put(
                            JSONObject().apply {
                                put("date", dateFmt.format(Date(act.eventDateMillis)))
                                put("type", act.eventType)
                                put("dir", act.direction)
                                put("amt", act.totalAmount)
                                if (act.homeItems.isNotBlank()) put("items", act.homeItems)
                            }
                        )
                    }
                }

                put(
                    JSONObject().apply {
                        put("familyDocId", fam.familyDocId)
                        put("fn", fam.familyId)
                        put("name", fam.familyName)
                        put("totalIn", fam.totalIn)
                        put("totalOut", fam.totalOut)
                        put("netBalance", fam.netBalance)
                        put("entriesCount", fam.entriesCount)
                        put("recentEntries", recentArr)
                    }
                )
            }
        }

        return """
            You are the AI analyst inside "Life Line", a family occasion and Neondra ledger app.
            CRITICAL LANGUAGE REQUIREMENT: Write `headline`, `summary`, every `familyInsights[].insight`, and every item in `recommendations` strictly in clear, concise, natural URDU (اردو). Keep `familyDocId` values exact.
            Compare the In (آمدن / وصول) and Out (خرچ / دیے گئے) balances across these selected families based on the offline data below.

            SELECTED FAMILIES:
            $famArr
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
        const val PRIMARY_MODEL_NAME = "gemini-3.8-flash"
        const val FAST_FALLBACK_MODEL_NAME = "gemini-3.5-flash-lite"
        const val PRIMARY_TIMEOUT_MILLIS = 35_000L
        const val FALLBACK_TIMEOUT_MILLIS = 30_000L
        const val MISSING_FIREBASE_CONFIG_MESSAGE =
            "Online AI requires google-services.json. You can continue using Offline Quick Filter and Family Comparisons anytime."
    }
}
