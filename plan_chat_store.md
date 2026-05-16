# UI Conversation History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist completed UI conversations locally, show them in a history sidebar, and support opening, resuming,
deleting, and pinning conversations without persisting in-progress drafts.

**Architecture:** Keep `App.kt` focused on screen state and event wiring, move persistence into a
`ConversationRepository` in `commonMain`, and hide platform storage behind `expect/actual` adapters. Persist only
completed conversations after successful responses, while continuing to show the current streaming exchange from
in-memory state only.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlinx.serialization JSON, browser `localStorage`, JVM
`Preferences`, existing `:composeApp:jsBrowserTest`

---

## File Structure

- **Create:** `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/model/StoredConversation.kt`
    - Serializable persistence models: archive root, stored conversation, summary, persisted backend session metadata,
      title helper.
- **Modify:** `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/model/ConversationTurn.kt`
    - Mark stored turn models serializable and add any small mapping helpers required by persistence.
- **Modify:** `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/model/Source.kt`
    - Mark source metadata serializable so stored turns can round-trip through JSON.
- **Create:** `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationStore.kt`
    - Storage boundary plus cross-platform helpers like `newConversationId()` and `currentTimeMillis()`.
- **Create:** `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationRepository.kt`
    - Read/write archive JSON, sort pinned summaries first, load active conversation, save/delete/pin/select
      conversations, discard invalid payloads safely.
- **Create:** `composeApp/src/jsMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationStore.kt`
    - JS `localStorage` implementation.
- **Create:** `composeApp/src/jvmMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationStore.kt`
    - JVM `Preferences` implementation for the desktop target.
- **Modify:** `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ThinkAPI.kt`
    - Expose a restorable conversation session snapshot so reopening a saved conversation can reuse the last backend
      session token/traceparent.
- **Create:** `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/widget/ConversationSidebar.kt`
    - Sidebar/list UI for persisted conversations, including pin and delete actions.
- **Modify:** `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/App.kt`
    - Load persisted history on startup, wire sidebar actions, keep drafts memory-only, persist only after successful
      responses, and restore saved conversation context.
- **Create:** `composeApp/src/webTest/kotlin/me/davidgomesdev/ofingidor/ui/model/StoredConversationTest.kt`
    - Tests for title generation, serialization, and summary mapping.
- **Create:** `composeApp/src/webTest/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationRepositoryTest.kt`
    - Repository behavior tests using an in-memory fake store.

## Notes and Assumptions

- Pinned conversations sort before unpinned conversations; within each group, newest `updatedAtEpochMillis` sorts first.
- Sidebar shows only persisted conversations. The current streaming exchange remains visible in the main panel only.
- Reopening a conversation restores the saved backend session token/traceparent on a best-effort basis, matching the
  current “ignore expiration for now” requirement.
- On compact layouts, render the history list as a full-width card above the conversation feed. On wider layouts, render
  it as a left sidebar.
- This plan intentionally omits git commit steps because this workspace must not create commits.

### Task 1: Add serializable persistence models and restorable session metadata

**Files:**

- Create: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/model/StoredConversation.kt`
- Modify: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/model/ConversationTurn.kt`
- Modify: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/model/Source.kt`
- Modify: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ThinkAPI.kt`
- Test: `composeApp/src/webTest/kotlin/me/davidgomesdev/ofingidor/ui/model/StoredConversationTest.kt`

- [ ] **Step 1: Write the failing tests for storage models and title generation**

```kotlin
package me.davidgomesdev.ofingidor.ui.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredConversationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deriveTitle_trimsWhitespaceAndCapsLength() {
        val title = deriveConversationTitle("   Quem e o Fernando Pessoa por tras da mascara?   ")
        assertEquals("Quem e o Fernando Pessoa por tras da mascara?", title)
    }

    @Test
    fun storedConversation_roundTripsThroughJson() {
        val original = StoredConversation(
            id = "conv-1",
            title = "Quem es?",
            personaCode = Persona.FERNANDO_PESSOA.name,
            updatedAtEpochMillis = 1234L,
            isPinned = true,
            turns = listOf(
                ConversationTurn(
                    question = "Quem es?",
                    message = "Sou fragmento.",
                    sources = listOf(Source(1L, "Mensagem", "Fernando Pessoa", "Poesia", 92)),
                    traceId = "trace-1",
                    personaName = "Fernando Pessoa",
                )
            ),
            session = ConversationSession(sessionToken = "token-1", traceparent = "traceparent-1"),
        )

        val encoded = json.encodeToString(StoredConversation.serializer(), original)
        val decoded = json.decodeFromString(StoredConversation.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun sessionSnapshot_isNullWhenNothingWasStored() {
        assertNull(ConversationSession.emptyOrNull(sessionToken = null, traceparent = null))
    }
}
```

- [ ] **Step 2: Run the JS tests to confirm the new types are missing**

Run: `./gradlew :composeApp:jsBrowserTest`

Expected: FAIL with unresolved references for `StoredConversation`, `ConversationSession`, `deriveConversationTitle`, or
missing serializers on `ConversationTurn` / `Source`.

- [ ] **Step 3: Add the minimal persistence model and ThinkAPI session hooks**

```kotlin
package me.davidgomesdev.ofingidor.ui.model

import kotlinx.serialization.Serializable

private const val MAX_TITLE_LENGTH = 60

@Serializable
data class ConversationSession(
    val sessionToken: String? = null,
    val traceparent: String? = null,
) {
    companion object {
        fun emptyOrNull(sessionToken: String?, traceparent: String?): ConversationSession? =
            if (sessionToken == null && traceparent == null) null else ConversationSession(sessionToken, traceparent)
    }
}

@Serializable
data class StoredConversation(
    val id: String,
    val title: String,
    val personaCode: String,
    val updatedAtEpochMillis: Long,
    val isPinned: Boolean = false,
    val turns: List<ConversationTurn>,
    val session: ConversationSession? = null,
)

@Serializable
data class StoredConversationSummary(
    val id: String,
    val title: String,
    val personaCode: String,
    val updatedAtEpochMillis: Long,
    val isPinned: Boolean,
)

@Serializable
data class ConversationArchive(
    val storageVersion: Int = 1,
    val activeConversationId: String? = null,
    val conversations: List<StoredConversation> = emptyList(),
)

fun StoredConversation.toSummary() = StoredConversationSummary(
    id = id,
    title = title,
    personaCode = personaCode,
    updatedAtEpochMillis = updatedAtEpochMillis,
    isPinned = isPinned,
)

fun deriveConversationTitle(question: String): String =
    question
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MAX_TITLE_LENGTH)
        .ifBlank { "Nova conversa" }
```

```kotlin
package me.davidgomesdev.ofingidor.ui.service

import me.davidgomesdev.ofingidor.ui.model.ConversationSession

class ThinkAPI {
    fun currentSession(): ConversationSession? =
        ConversationSession.emptyOrNull(sessionToken = sessionToken, traceparent = traceparent)

    fun restoreConversation(session: ConversationSession?) {
        sessionToken = session?.sessionToken
        traceparent = session?.traceparent
    }
}
```

```kotlin
@Serializable
data class ConversationTurn(
    val question: String,
    val message: String,
    val sources: List<Source>,
    val traceId: String,
    val personaName: String,
)

@Serializable
data class Source(
    val id: Long,
    val title: String,
    val author: String,
    val category: String,
    val score: Int,
)
```

- [ ] **Step 4: Run the JS tests to confirm the model layer passes**

Run: `./gradlew :composeApp:jsBrowserTest`

Expected: PASS for `StoredConversationTest`; any remaining failures should now come only from repository or UI work not
yet implemented.

### Task 2: Add the platform storage boundary and repository behavior

**Files:**

- Create: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationStore.kt`
- Create: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationRepository.kt`
- Create: `composeApp/src/jsMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationStore.kt`
- Create: `composeApp/src/jvmMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationStore.kt`
- Test: `composeApp/src/webTest/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationRepositoryTest.kt`

- [ ] **Step 1: Write failing repository tests for save/load/order/pin/delete/select and corrupt payload fallback**

```kotlin
package me.davidgomesdev.ofingidor.ui.service

import me.davidgomesdev.ofingidor.ui.model.ConversationArchive
import me.davidgomesdev.ofingidor.ui.model.StoredConversation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationRepositoryTest {

    @Test
    fun saveAndListSummaries_sortsPinnedFirstThenNewest() {
        val store = InMemoryConversationStore()
        val repository = ConversationRepository(store, now = MutableNow(1_000L)::next)

        repository.save(completedConversation(id = "older"))
        repository.save(completedConversation(id = "pinned"), isPinned = true)
        repository.save(completedConversation(id = "newer"))

        val summaries = repository.listSummaries()

        assertEquals(listOf("pinned", "newer", "older"), summaries.map { it.id })
    }

    @Test
    fun delete_removesConversationAndClearsActiveWhenNeeded() {
        val store = InMemoryConversationStore()
        val repository = ConversationRepository(store)

        repository.save(completedConversation(id = "conv-1"))
        repository.setActiveConversation("conv-1")
        repository.delete("conv-1")

        assertTrue(repository.listSummaries().isEmpty())
        assertNull(repository.activeConversationId())
    }

    @Test
    fun loadArchive_returnsEmptyArchiveWhenStoredJsonIsInvalid() {
        val store = InMemoryConversationStore(rawValue = "{broken")
        val repository = ConversationRepository(store)

        assertEquals(ConversationArchive(), repository.loadArchive())
    }
}
```

- [ ] **Step 2: Run the JS tests to verify repository symbols are missing**

Run: `./gradlew :composeApp:jsBrowserTest`

Expected: FAIL with unresolved references for `ConversationRepository`, `ConversationStore`,`InMemoryConversationStore`,
or repository methods.

- [ ] **Step 3: Implement the storage boundary and repository**

```kotlin
package me.davidgomesdev.ofingidor.ui.service

interface ConversationStore {
    fun readArchive(): String?
    fun writeArchive(payload: String)
    fun clearArchive()
}

expect fun platformConversationStore(): ConversationStore
expect fun currentTimeMillis(): Long
expect fun newConversationId(): String
```

```kotlin
package me.davidgomesdev.ofingidor.ui.service

import kotlinx.serialization.json.Json
import me.davidgomesdev.ofingidor.ui.model.ConversationArchive
import me.davidgomesdev.ofingidor.ui.model.StoredConversation
import me.davidgomesdev.ofingidor.ui.model.StoredConversationSummary
import me.davidgomesdev.ofingidor.ui.model.toSummary

class ConversationRepository(
    private val store: ConversationStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val now: () -> Long = ::currentTimeMillis,
) {
    fun loadArchive(): ConversationArchive =
        runCatching {
            store.readArchive()
                ?.let { json.decodeFromString(ConversationArchive.serializer(), it) }
                ?.takeIf { it.storageVersion == 1 }
                ?: ConversationArchive()
        }.getOrDefault(ConversationArchive())

    fun listSummaries(): List<StoredConversationSummary> =
        loadArchive()
            .conversations
            .map(StoredConversation::toSummary)
            .sortedWith(compareByDescending<StoredConversationSummary> { it.isPinned }.thenByDescending { it.updatedAtEpochMillis })

    fun load(id: String): StoredConversation? =
        loadArchive().conversations.firstOrNull { it.id == id }

    fun save(conversation: StoredConversation, isPinned: Boolean = conversation.isPinned) {
        val archive = loadArchive()
        val existing = archive.conversations.firstOrNull { it.id == conversation.id }
        val updatedConversation = conversation.copy(
            isPinned = existing?.isPinned ?: isPinned,
            updatedAtEpochMillis = now(),
        )
        val updated = archive.conversations.filterNot { it.id == conversation.id } + updatedConversation
        persist(archive.copy(activeConversationId = updatedConversation.id, conversations = updated))
    }

    fun delete(id: String) {
        val archive = loadArchive()
        val updated = archive.conversations.filterNot { it.id == id }
        val nextActive = archive.activeConversationId.takeUnless { it == id }
        persist(archive.copy(activeConversationId = nextActive, conversations = updated))
    }

    fun setPinned(id: String, pinned: Boolean) {
        val archive = loadArchive()
        persist(
            archive.copy(
                conversations = archive.conversations.map { conversation ->
                    if (conversation.id == id) conversation.copy(isPinned = pinned) else conversation
                }
            )
        )
    }

    fun setActiveConversation(id: String?) {
        persist(loadArchive().copy(activeConversationId = id))
    }

    fun activeConversationId(): String? = loadArchive().activeConversationId

    private fun persist(archive: ConversationArchive) {
        if (archive.conversations.isEmpty() && archive.activeConversationId == null) {
            store.clearArchive()
            return
        }
        store.writeArchive(json.encodeToString(ConversationArchive.serializer(), archive))
    }
}
```

```kotlin
package me.davidgomesdev.ofingidor.ui.service

import kotlin.js.Date

private const val archiveKey = "ofingidor.conversation-archive.v1"

private class BrowserConversationStore : ConversationStore {
    override fun readArchive(): String? = js("window.localStorage.getItem(archiveKey)") as String?
    override fun writeArchive(payload: String) {
        js("window.localStorage.setItem(archiveKey, payload)")
    }
    override fun clearArchive() {
        js("window.localStorage.removeItem(archiveKey)")
    }
}

actual fun platformConversationStore(): ConversationStore = BrowserConversationStore()
actual fun currentTimeMillis(): Long = Date.now().toLong()
actual fun newConversationId(): String = js("crypto.randomUUID()") as String
```

```kotlin
package me.davidgomesdev.ofingidor.ui.service

import java.util.UUID
import java.util.prefs.Preferences

private const val archiveKey = "ofingidor.conversation-archive.v1"
private val preferences: Preferences = Preferences.userRoot().node("me.davidgomesdev.ofingidor.ui")

private class DesktopConversationStore : ConversationStore {
    override fun readArchive(): String? = preferences.get(archiveKey, null)
    override fun writeArchive(payload: String) {
        preferences.put(archiveKey, payload)
    }
    override fun clearArchive() {
        preferences.remove(archiveKey)
    }
}

actual fun platformConversationStore(): ConversationStore = DesktopConversationStore()
actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual fun newConversationId(): String = UUID.randomUUID().toString()
```

- [ ] **Step 4: Add the in-memory fake store used by the repository tests**

```kotlin
private class InMemoryConversationStore(
    private var rawValue: String? = null,
) : ConversationStore {
    override fun readArchive(): String? = rawValue
    override fun writeArchive(payload: String) {
        rawValue = payload
    }
    override fun clearArchive() {
        rawValue = null
    }
}

private class MutableNow(start: Long) {
    private var current = start
    fun next(): Long = current++
}

private fun completedConversation(id: String) = StoredConversation(
    id = id,
    title = id,
    personaCode = "FERNANDO_PESSOA",
    updatedAtEpochMillis = 0L,
    turns = emptyList(),
    session = null,
)
```

- [ ] **Step 5: Run the JS tests to confirm repository behavior passes**

Run: `./gradlew :composeApp:jsBrowserTest`

Expected: PASS for `ConversationRepositoryTest` and `StoredConversationTest`.

### Task 3: Integrate repository-backed conversation state into `App.kt`

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/service/ThinkAPI.kt`
- Modify: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/model/StoredConversation.kt`
- Test: `composeApp/src/webTest/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationRepositoryTest.kt`

- [ ] **Step 1: Add a failing repository test for preserving pin state when updating an existing conversation**

```kotlin
@Test
fun save_existingConversationKeepsPinnedFlagAndReplacesTurns() {
    val store = InMemoryConversationStore()
    val repository = ConversationRepository(store)

    repository.save(
        completedConversation(id = "conv-1").copy(
            turns = listOf(
                ConversationTurn(
                    question = "Pergunta 1",
                    message = "Resposta 1",
                    sources = emptyList(),
                    traceId = "trace-1",
                    personaName = "Fernando Pessoa",
                )
            )
        ),
        isPinned = true,
    )
    repository.save(
        completedConversation(id = "conv-1").copy(
            turns = listOf(
                ConversationTurn(
                    question = "Pergunta 1",
                    message = "Resposta 1",
                    sources = emptyList(),
                    traceId = "trace-1",
                    personaName = "Fernando Pessoa",
                ),
                ConversationTurn(
                    question = "Pergunta 2",
                    message = "Resposta 2",
                    sources = emptyList(),
                    traceId = "trace-2",
                    personaName = "Fernando Pessoa",
                )
            )
        )
    )

    val stored = repository.load("conv-1")

    assertEquals(true, stored?.isPinned)
    assertEquals(2, stored?.turns?.size)
}
```

- [ ] **Step 2: Run the JS tests to confirm the update path is not covered yet**

Run: `./gradlew :composeApp:jsBrowserTest`

Expected: FAIL if `save()` drops pin state or fails to replace the previous conversation body.

- [ ] **Step 3: Wire startup loading, successful persistence, and reopen/delete/pin handlers into `App.kt`**

```kotlin
val repository = remember { ConversationRepository(platformConversationStore()) }
var conversationSummaries by remember { mutableStateOf(emptyList<StoredConversationSummary>()) }
var activeConversationId by remember { mutableStateOf<String?>(null) }

LaunchedEffect(Unit) {
    conversationSummaries = repository.listSummaries()
    val restoredId = repository.activeConversationId()
    if (restoredId != null) {
        repository.load(restoredId)?.let { stored ->
            turns.clear()
            turns.addAll(stored.turns)
            activeConversationId = stored.id
            selectedPersona = Persona.valueOf(stored.personaCode)
            thinkAPI.restoreConversation(stored.session)
        }
    }
}

fun openConversation(id: String) {
    repository.load(id)?.let { stored ->
        turns.clear()
        turns.addAll(stored.turns)
        ongoingTurn = null
        ongoingTurnError = null
        conversationTraceId = stored.turns.lastOrNull()?.traceId.orEmpty()
        selectedPersona = Persona.valueOf(stored.personaCode)
        activeConversationId = stored.id
        repository.setActiveConversation(id)
        thinkAPI.restoreConversation(stored.session)
    }
}

fun persistCompletedTurn(question: String, completedTurn: ConversationTurn) {
    val conversationId = activeConversationId ?: newConversationId()
    val existing = activeConversationId?.let(repository::load)
    val storedConversation = StoredConversation(
        id = conversationId,
        title = existing?.title ?: deriveConversationTitle(question),
        personaCode = selectedPersona.name,
        updatedAtEpochMillis = 0L,
        isPinned = existing?.isPinned ?: false,
        turns = (existing?.turns ?: emptyList()) + completedTurn,
        session = thinkAPI.currentSession(),
    )
    repository.save(storedConversation)
    activeConversationId = conversationId
    conversationSummaries = repository.listSummaries()
}
```

- [ ] **Step 4: Keep draft exchanges memory-only and delete nothing on failed responses**

```kotlin
val result = processSubmit(
    question = question,
    selectedPersona = selectedPersona,
    thinkAPI = thinkAPI,
    getOngoingTurn = { ongoingTurn },
    setOngoingTurn = { ongoingTurn = it },
    onTraceId = { if (conversationTraceId.isBlank()) conversationTraceId = it },
)
result.fold(
    onSuccess = { turn ->
        turn?.let {
            turns.add(it)
            persistCompletedTurn(question = question, completedTurn = it)
        }
    },
    onFailure = {
        ongoingTurnError = it
        inputText = question
    },
)
```

This step matters because the persisted list must stay unchanged until the response succeeds.

- [ ] **Step 5: Run the JS tests to confirm repository updates still pass after the App wiring**

Run: `./gradlew :composeApp:jsBrowserTest`

Expected: PASS, including the new update-path test.

### Task 4: Add the conversation history sidebar and responsive layout

**Files:**

- Create: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/widget/ConversationSidebar.kt`
- Modify: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/Colors.kt` (only if existing palette variables
  are insufficient)

- [ ] **Step 1: Create the sidebar composable with select, pin, and delete callbacks**

```kotlin
@Composable
fun ConversationSidebar(
    conversations: List<StoredConversationSummary>,
    activeConversationId: String?,
    onConversationSelected: (String) -> Unit,
    onTogglePinned: (String, Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(componentColumnBackgroundColor)
            .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Conversas", color = Color.White, fontSize = 13.sp)
        if (conversations.isEmpty()) {
            Text("Ainda nao ha conversas guardadas.", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        }
        conversations.forEach { summary ->
            ConversationSidebarItem(
                summary = summary,
                isActive = summary.id == activeConversationId,
                onOpen = { onConversationSelected(summary.id) },
                onTogglePinned = { onTogglePinned(summary.id, !summary.isPinned) },
                onDelete = { onDeleteConversation(summary.id) },
            )
        }
    }
}

@Composable
private fun ConversationSidebarItem(
    summary: StoredConversationSummary,
    isActive: Boolean,
    onOpen: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) inputCardBackgroundColor else Color.Transparent)
            .border(1.dp, if (isActive) focusedIndicatorColor else cardBorderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(summary.title, color = Color.White, fontSize = 12.sp)
        Text(
            Persona.valueOf(summary.personaCode).displayName,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (summary.isPinned) "Desafixar" else "Fixar",
                color = focusedIndicatorColor,
                fontSize = 10.sp,
                modifier = Modifier.clickable(onClick = onTogglePinned)
            )
            Text(
                "Apagar",
                color = errorBubbleTextColor,
                fontSize = 10.sp,
                modifier = Modifier.clickable(onClick = onDelete)
            )
        }
    }
}
```

- [ ] **Step 2: Update `App.kt` layout so wide screens show a left sidebar and compact screens show a stacked history
  card**

```kotlin
val sidebarModifier = Modifier.fillMaxWidth().widthIn(max = 260.dp)

if (isCompact) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConversationSidebar(
            conversations = conversationSummaries,
            activeConversationId = activeConversationId,
            onConversationSelected = ::openConversation,
            onTogglePinned = { id, pinned ->
                repository.setPinned(id, pinned)
                conversationSummaries = repository.listSummaries()
            },
            onDeleteConversation = ::deleteConversation,
            modifier = sidebarModifier,
        )
        ConversationFeed(
            turns = turns,
            ongoingTurn = ongoingTurn,
            ongoingTurnError = ongoingTurnError,
            isDevMode = isDevMode,
            conversationTraceId = conversationTraceId,
            hasConversationStarted = hasConversationStarted,
        )
        ThinkInputCard(
            text = inputText,
            onTextChange = { inputText = it },
            isLoading = ongoingTurn != null,
            onSubmit = onSubmit,
            onQuerySelected = { query -> inputText = query },
            hasConversationStarted = hasConversationStarted,
            isCompact = isCompact,
        )
    }
} else {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConversationSidebar(
            conversations = conversationSummaries,
            activeConversationId = activeConversationId,
            onConversationSelected = ::openConversation,
            onTogglePinned = { id, pinned ->
                repository.setPinned(id, pinned)
                conversationSummaries = repository.listSummaries()
            },
            onDeleteConversation = ::deleteConversation,
            modifier = Modifier.width(260.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConversationFeed(
                turns = turns,
                ongoingTurn = ongoingTurn,
                ongoingTurnError = ongoingTurnError,
                isDevMode = isDevMode,
                conversationTraceId = conversationTraceId,
                hasConversationStarted = hasConversationStarted,
            )
            ThinkInputCard(
                text = inputText,
                onTextChange = { inputText = it },
                isLoading = ongoingTurn != null,
                onSubmit = onSubmit,
                onQuerySelected = { query -> inputText = query },
                hasConversationStarted = hasConversationStarted,
                isCompact = isCompact,
            )
        }
    }
}
```

- [ ] **Step 3: Ensure deleting the active conversation opens the next available saved conversation or resets to empty
  state**

```kotlin
fun deleteConversation(id: String) {
    repository.delete(id)
    conversationSummaries = repository.listSummaries()

    if (activeConversationId != id) return

    val nextConversationId = conversationSummaries.firstOrNull()?.id
    if (nextConversationId == null) {
        activeConversationId = null
        turns.clear()
        ongoingTurn = null
        ongoingTurnError = null
        conversationTraceId = ""
        thinkAPI.resetConversation()
        return
    }

    openConversation(nextConversationId)
}
```

- [ ] **Step 4: Run the JS tests and then do a manual browser check of the sidebar flows**

Run: `./gradlew :composeApp:jsBrowserTest`

Expected: PASS.

Run: `./gradlew :composeApp:jsBrowserDevelopmentRun`

Expected manual result:

- A completed reply appears in the sidebar after the stream finishes.
- Reloading the browser restores the last active saved conversation.
- Pinning moves a conversation above unpinned items.
- Deleting the active conversation opens the next saved conversation or clears the screen if none remain.
- Refreshing during an in-progress draft loses that draft and does not create a sidebar item.

### Task 5: Final verification and cleanup

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/me/davidgomesdev/ofingidor/ui/widget/ConversationSidebar.kt`
- Test: `composeApp/src/webTest/kotlin/me/davidgomesdev/ofingidor/ui/model/StoredConversationTest.kt`
- Test: `composeApp/src/webTest/kotlin/me/davidgomesdev/ofingidor/ui/service/ConversationRepositoryTest.kt`

- [ ] **Step 1: Add a regression test that loading a saved conversation restores the selected persona code cleanly**

```kotlin
@Test
fun load_savedConversationKeepsPersonaCodeNeededForResume() {
    val store = InMemoryConversationStore()
    val repository = ConversationRepository(store)

    repository.save(completedConversation(id = "conv-1").copy(personaCode = "ALBERTO_CAEIRO"))

    assertEquals("ALBERTO_CAEIRO", repository.load("conv-1")?.personaCode)
}
```

- [ ] **Step 2: Run the JS tests to confirm the final regression suite passes**

Run: `./gradlew :composeApp:jsBrowserTest`

Expected: PASS.

- [ ] **Step 3: Run the existing backend/UI verification commands affected by the change**

Run: `./gradlew test :composeApp:jsBrowserTest`

Expected:

- Backend tests stay green.
- JS browser tests stay green.

- [ ] **Step 4: Review the saved payload shape manually before finishing**

Browser expectation:

- `window.localStorage.getItem("ofingidor.conversation-archive.v1")` contains `storageVersion`, `activeConversationId`,
  `conversations`, pinned flags, persona code, and persisted `sessionToken` / `traceparent`.

Desktop expectation:

- The same JSON blob is visible under the `Preferences` node `me.davidgomesdev.ofingidor.ui`.

## Self-Review

- **Spec coverage:** The plan covers local-only persistence, sidebar listing, open/resume/delete/pin, active
  conversation restore, memory-only drafts, and future-proofed repository boundaries for later backend sync.
- **Placeholder scan:** No `TODO`, `TBD`, or “implement later” placeholders remain.
- **Type consistency:** The plan consistently uses `StoredConversation`, `StoredConversationSummary`,
  `ConversationSession`, `ConversationRepository`, `ConversationStore`, `personaCode`, and `updatedAtEpochMillis`.
