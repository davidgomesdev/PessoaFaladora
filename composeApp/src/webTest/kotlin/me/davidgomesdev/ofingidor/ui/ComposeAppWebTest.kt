package me.davidgomesdev.ofingidor.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.davidgomesdev.ofingidor.shared.dto.ChatEvent
import me.davidgomesdev.ofingidor.shared.dto.DebateEvent
import me.davidgomesdev.ofingidor.shared.dto.Persona
import me.davidgomesdev.ofingidor.ui.model.DebatePair
import me.davidgomesdev.ofingidor.ui.model.DebateSide
import me.davidgomesdev.ofingidor.ui.model.DebateTurn
import me.davidgomesdev.ofingidor.ui.model.OngoingConversationTurn
import me.davidgomesdev.ofingidor.ui.model.Source
import me.davidgomesdev.ofingidor.ui.service.ThinkAPI
import me.davidgomesdev.ofingidor.ui.widget.AvatarContentDescriptionMode
import me.davidgomesdev.ofingidor.ui.widget.appHeaderIdentity
import me.davidgomesdev.ofingidor.ui.widget.chatPortraitIdentity
import me.davidgomesdev.ofingidor.ui.widget.debatePortraitIdentity
import me.davidgomesdev.ofingidor.ui.widget.defaultAvatarSize
import me.davidgomesdev.ofingidor.ui.widget.personaIdentityChipModel
import me.davidgomesdev.ofingidor.ui.widget.personaPortrait
import me.davidgomesdev.ofingidor.ui.widget.portraitChipLayout
import me.davidgomesdev.ofingidor.ui.widget.resolveAvatarContentDescription
import me.davidgomesdev.ofingidor.ui.widget.resolveChatAvatarSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeAppWebTest {

    private val sampleSource = Source(1L, "Mensagem", "Fernando Pessoa", "Poesia", 92)

    @Test
    fun personaPortraits_defineAssetForEveryPersona() {
        val missing = Persona.entries.filter { personaPortrait(it) == null }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun appHeaderIdentity_usesSelectedPersonaPortrait() {
        val model = appHeaderIdentity(Persona.RICARDO_REIS)
        assertEquals("Retrato de Ricardo Reis", model.portrait.contentDescription)
        assertEquals("RICARDO REIS", model.personaLabel)
    }

    @Test
    fun chatPortraitIdentity_keepsExistingFeedRhythm() {
        val identity = chatPortraitIdentity(Persona.FERNANDO_PESSOA)
        assertEquals("Fernando Pessoa", identity.label)
        assertTrue(identity.compact)
    }

    @Test
    fun chatPortraitIdentity_compactAvatarResolvesTo24dp() {
        val identity = chatPortraitIdentity(Persona.FERNANDO_PESSOA)
        val avatarSize = resolveChatAvatarSize(identity)
        assertEquals(24.dp, avatarSize)
        assertNotEquals(defaultAvatarSize, avatarSize)
    }

    @Test
    fun debatePortraitIdentity_keepsFullNameAndAvatarScale() {
        val identity = debatePortraitIdentity(Persona.ALBERTO_CAEIRO)
        assertEquals("Alberto Caeiro", identity.label)
    }

    @Test
    fun personaIdentityChipModel_preservesFullNameForLongPersona() {
        val model = personaIdentityChipModel(Persona.BERNARDO_SOARES, isCompact = true, isSelected = true)
        assertEquals("Bernardo Soares", model.layout.label)
        assertEquals("Bernardo Soares", model.persona.displayName)
    }

    @Test
    fun personaIdentityChipModel_usesDecorativeAvatarSemantics() {
        val model = personaIdentityChipModel(Persona.ALVARO_DE_CAMPOS, isCompact = false, isSelected = false)
        assertEquals(null, model.layout.avatarContentDescription)
    }

    @Test
    fun chipAvatarContentDescription_resolutionKeepsAvatarDecorative() {
        val model = personaIdentityChipModel(Persona.ALVARO_DE_CAMPOS, isCompact = false, isSelected = false)
        val portrait = requireNotNull(personaPortrait(model.persona))
        val resolvedContentDescription = resolveAvatarContentDescription(
            portrait = portrait,
            requestedContentDescription = model.layout.avatarContentDescription,
            mode = AvatarContentDescriptionMode.DECORATIVE,
        )
        assertNull(resolvedContentDescription)
    }

    @Test
    fun avatarContentDescription_resolutionUsesPortraitDescriptionForMeaningfulAvatars() {
        val portrait = requireNotNull(personaPortrait(Persona.RICARDO_REIS))
        val resolvedContentDescription = resolveAvatarContentDescription(
            portrait = portrait,
            requestedContentDescription = null,
            mode = AvatarContentDescriptionMode.MEANINGFUL,
        )
        assertEquals("Retrato de Ricardo Reis", resolvedContentDescription)
    }

    @Test
    fun personaPortraitChipLayout_keepsFullNameOnCompactScreens() {
        val model = portraitChipLayout(persona = Persona.BERNARDO_SOARES, isCompact = true, isSelected = false)
        assertEquals("Bernardo Soares", model.label)
        assertTrue(model.showPortrait)
    }

    @Test
    fun personaPortraitChipLayout_selectedStateChangesPresentation() {
        val selected = portraitChipLayout(persona = Persona.ALBERTO_CAEIRO, isCompact = false, isSelected = true)
        val unselected = portraitChipLayout(persona = Persona.ALBERTO_CAEIRO, isCompact = false, isSelected = false)

        assertTrue(selected.selected)
        assertEquals(false, unselected.selected)
        assertNotEquals(selected.backgroundColor, unselected.backgroundColor)
        assertNotEquals(selected.borderColor, unselected.borderColor)
        assertNotEquals(selected.labelColor, unselected.labelColor)
    }

    @Test
    fun personaPortraitChipLayout_selectedOrthonymUsesActivePalette() {
        val selected = portraitChipLayout(persona = Persona.FERNANDO_PESSOA, isCompact = false, isSelected = true)

        assertTrue(selected.selected)
        assertEquals(orthonymChipColor, selected.backgroundColor)
        assertEquals(orthonymChipBorderColor, selected.borderColor)
        assertEquals(orthonymChipTextColor, selected.labelColor)
    }

    @Test
    fun personaIdentityChipModel_preservesDebatePickerPersonaLabels() {
        val leftModel = personaIdentityChipModel(Persona.ALVARO_DE_CAMPOS, isCompact = false, isSelected = true)
        val rightModel = personaIdentityChipModel(Persona.BERNARDO_SOARES, isCompact = false, isSelected = false)
        assertEquals("Álvaro de Campos", leftModel.layout.label)
        assertEquals("Bernardo Soares", rightModel.layout.label)
    }

    @Test
    fun ongoingTurn_defaultsToEmptyMessageAndSources() {
        val turn = OngoingConversationTurn(question = "Quem és?", persona = Persona.FERNANDO_PESSOA)
        assertEquals("", turn.message)
        assertTrue(turn.sources.isEmpty())
        assertEquals("", turn.traceId)
    }

    @Test
    fun ongoingTurn_toConversationTurn_preservesSelectedPersona() {
        val ongoing = OngoingConversationTurn(
            question = "O que é o amor?",
            message = "O amor é...",
            sources = listOf(sampleSource),
            traceId = "abc123",
            persona = Persona.ALVARO_DE_CAMPOS,
        )
        val completed = ongoing.toConversationTurn()
        assertEquals(ongoing.question, completed.question)
        assertEquals(ongoing.message, completed.message)
        assertEquals(ongoing.sources, completed.sources)
        assertEquals(ongoing.traceId, completed.traceId)
        assertEquals(ongoing.persona, completed.persona)
    }

    @Test
    fun ongoingTurn_appendsToken() {
        val turn = OngoingConversationTurn(
            question = "Quem és?",
            message = "Eu sou ",
            persona = Persona.FERNANDO_PESSOA,
        )
        val updated = turn.copy(message = turn.message + "Fernando.")
        assertEquals("Eu sou Fernando.", updated.message)
    }

    @Test
    fun processSubmit_preservesPersonaWithoutDisplayNameRemapping() = runTest {
        var ongoingTurn: OngoingConversationTurn? = null
        var traceId = ""

        val result = processSubmit(
            question = "Quem és?",
            selectedPersona = Persona.ALVARO_DE_CAMPOS,
            thinkAPI = ThinkAPI(),
            getOngoingTurn = { ongoingTurn },
            setOngoingTurn = { ongoingTurn = it },
            onTraceId = { traceId = it },
            collectEvents = { _, _, _, getCurrentTurn, onNewEvent, _, onTrace ->
                val current = getCurrentTurn() ?: error("ongoing turn should exist")
                onTrace("trace-123")
                onNewEvent(current.copy(message = "Sou Álvaro de Campos.", traceId = "trace-123"))
            },
        )

        assertEquals("trace-123", traceId)
        assertNull(ongoingTurn)
        assertEquals(Persona.ALVARO_DE_CAMPOS, result.getOrNull()?.persona)
    }

    @Test
    fun debatePair_sideForMapsLeftAndRightSpeakers() {
        val pair = DebatePair(Persona.FERNANDO_PESSOA, Persona.ALBERTO_CAEIRO)

        assertEquals(DebateSide.LEFT, pair.sideFor(Persona.FERNANDO_PESSOA))
        assertEquals(DebateSide.RIGHT, pair.sideFor(Persona.ALBERTO_CAEIRO))
    }

    @Test
    fun debatePair_sideForRejectsUnknownSpeaker() {
        val pair = DebatePair(Persona.FERNANDO_PESSOA, Persona.ALBERTO_CAEIRO)

        assertFailsWith<IllegalStateException> {
            pair.sideFor(Persona.RICARDO_REIS)
        }
    }

    @Test
    fun disablingDevMode_normalizesOFingidorDebatePairAndRequiresConversationReset() {
        val result = devModeDisabledState(
            selectedPersona = Persona.FERNANDO_PESSOA,
            debatePair = DebatePair(Persona.O_FINGIDOR, Persona.ALBERTO_CAEIRO),
        )

        assertEquals(Persona.FERNANDO_PESSOA, result.selectedPersona)
        assertEquals(DebatePair(Persona.FERNANDO_PESSOA, Persona.ALBERTO_CAEIRO), result.debatePair)
        assertTrue(result.shouldResetConversation)
    }

    @Test
    fun disablingDevMode_normalizesOFingidorSelectedPersonaAndRequiresConversationReset() {
        val result = devModeDisabledState(
            selectedPersona = Persona.O_FINGIDOR,
            debatePair = DebatePair(Persona.FERNANDO_PESSOA, Persona.ALBERTO_CAEIRO),
        )

        assertEquals(Persona.FERNANDO_PESSOA, result.selectedPersona)
        assertEquals(DebatePair(Persona.FERNANDO_PESSOA, Persona.ALBERTO_CAEIRO), result.debatePair)
        assertTrue(result.shouldResetConversation)
    }

    @Test
    fun debateFeedItemCount_countsQuestionTurnAndErrorExplicitly() {
        val count = debateFeedItemCount(
            questionCount = 2,
            debateTurnCount = 3,
            hasOngoingQuestion = true,
            hasOngoingTurn = true,
            hasDebateError = true,
        )

        assertEquals(8, count)
    }

    @Test
    fun debateTurn_defaultsToEmptyMessageAndSources() {
        val turn = DebateTurn(turnIndex = 1, speaker = Persona.RICARDO_REIS)

        assertEquals("", turn.message)
        assertTrue(turn.sources.isEmpty())
        assertEquals(false, turn.isComplete)
    }

    @Test
    fun debateBubblePalette_usesPersonaSpecificColors() {
        val palette = debateBubblePalette(Persona.ALVARO_DE_CAMPOS)

        assertEquals(Color(0xFF1F1713), palette.background)
        assertEquals(Color(0xFF9A5A3A), palette.border)
        assertEquals(Color(0xFFF2C5AE), palette.label)
    }

    @Test
    fun processDebateEvents_returnsFailureAndCleansUpWhenEventHandlerThrows() = runTest {
        var cleanupCalls = 0
        var failures = 0

        val result = processDebateEvents(
            events = flowOf(Result.success(DebateEvent.Start("trace-123", "fernando_pessoa", "alberto_caeiro"))),
            onEvent = { throw IllegalStateException("bad debate event") },
            onCleanup = { cleanupCalls += 1 },
            onFailure = { failures += 1 },
        )

        assertEquals("bad debate event", result.exceptionOrNull()?.message)
        assertEquals(1, cleanupCalls)
        assertEquals(1, failures)
    }

    @Test
    fun performDebateSubmit_resetsClientConversationWhenDebateRequestFails() = runTest {
        val thinkAPI = ThinkAPI()

        thinkAPI.restoreConversation(
            sessionToken = "session-token",
            traceparent = "00-traceparent",
        )

        val result = performDebateSubmit(
            question = "Debatam isto",
            pair = DebatePair(Persona.FERNANDO_PESSOA, Persona.ALBERTO_CAEIRO),
            thinkAPI = thinkAPI,
            turns = mutableStateListOf(),
            getOngoingTurn = { null },
            setOngoingTurn = {},
            onTraceId = {},
            submitDebate = { _: String, _: DebatePair, _: ThinkAPI, _: SnapshotStateList<DebateTurn>, _: () -> DebateTurn?, _: (DebateTurn?) -> Unit, _: (String) -> Unit, onFailure: (Throwable) -> Unit ->
                val failure = IllegalStateException("debate request failed")
                onFailure(failure)
                Result.failure(failure)
            },
        )

        assertEquals("debate request failed", result.exceptionOrNull()?.message)
        assertEquals(
            ThinkAPI.ConversationState(sessionToken = null, traceparent = null),
            thinkAPI.conversationState(),
        )
    }

    @Test
    fun handleDebateEvent_buildsAndCompletesSpeakerTurn() {
        val turns = mutableStateListOf<DebateTurn>()
        var ongoing: DebateTurn? = null
        var traceId = ""
        val pair = DebatePair(Persona.FERNANDO_PESSOA, Persona.ALBERTO_CAEIRO)
        val eventSource = ChatEvent.Sources.Source(
            id = 1L,
            title = "Poema",
            author = "Alberto Caeiro",
            category = "Poesia",
            score = 97,
        )

        handleDebateEvent(
            event = DebateEvent.Start(
                traceId = "trace-123",
                personaA = Persona.FERNANDO_PESSOA.codeName,
                personaB = Persona.ALBERTO_CAEIRO.codeName,
            ),
            pair = pair,
            turns = turns,
            getOngoingTurn = { ongoing },
            setOngoingTurn = { ongoing = it },
            onTraceId = { traceId = it },
        )

        handleDebateEvent(
            event = DebateEvent.TurnStart(turnIndex = 0, speaker = Persona.ALBERTO_CAEIRO.codeName),
            pair = pair,
            turns = turns,
            getOngoingTurn = { ongoing },
            setOngoingTurn = { ongoing = it },
            onTraceId = { traceId = it },
        )
        handleDebateEvent(
            event = DebateEvent.Token(
                turnIndex = 0,
                speaker = Persona.ALBERTO_CAEIRO.codeName,
                value = "Sou ",
            ),
            pair = pair,
            turns = turns,
            getOngoingTurn = { ongoing },
            setOngoingTurn = { ongoing = it },
            onTraceId = { traceId = it },
        )
        handleDebateEvent(
            event = DebateEvent.Token(
                turnIndex = 0,
                speaker = Persona.ALBERTO_CAEIRO.codeName,
                value = "eu.",
            ),
            pair = pair,
            turns = turns,
            getOngoingTurn = { ongoing },
            setOngoingTurn = { ongoing = it },
            onTraceId = { traceId = it },
        )
        handleDebateEvent(
            event = DebateEvent.Sources(
                turnIndex = 0,
                speaker = Persona.ALBERTO_CAEIRO.codeName,
                sources = listOf(eventSource),
            ),
            pair = pair,
            turns = turns,
            getOngoingTurn = { ongoing },
            setOngoingTurn = { ongoing = it },
            onTraceId = { traceId = it },
        )
        handleDebateEvent(
            event = DebateEvent.TurnDone(
                turnIndex = 0,
                speaker = Persona.ALBERTO_CAEIRO.codeName,
                tokensUsed = 12,
                timeTaken = "0.4s",
            ),
            pair = pair,
            turns = turns,
            getOngoingTurn = { ongoing },
            setOngoingTurn = { ongoing = it },
            onTraceId = { traceId = it },
        )

        assertEquals("trace-123", traceId)
        assertNull(ongoing)
        assertEquals(1, turns.size)
        assertEquals(Persona.ALBERTO_CAEIRO, turns.single().speaker)
        assertEquals("Sou eu.", turns.single().message)
        assertEquals(listOf(Source.from(eventSource)), turns.single().sources)
        assertTrue(turns.single().isComplete)
    }
}
