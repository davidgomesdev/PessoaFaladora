package me.davidgomesdev.ofingidor.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.davidgomesdev.ofingidor.shared.dto.Persona
import me.davidgomesdev.ofingidor.shared.dto.PersonaCategory
import me.davidgomesdev.ofingidor.ui.componentColumnBackgroundColor
import me.davidgomesdev.ofingidor.ui.devChipBorderColor
import me.davidgomesdev.ofingidor.ui.devChipColor
import me.davidgomesdev.ofingidor.ui.devChipTextColor
import me.davidgomesdev.ofingidor.ui.focusedIndicatorColor
import me.davidgomesdev.ofingidor.ui.model.PersonaPortrait
import me.davidgomesdev.ofingidor.ui.orthonymChipBorderColor
import me.davidgomesdev.ofingidor.ui.orthonymChipColor
import me.davidgomesdev.ofingidor.ui.orthonymChipTextColor
import me.davidgomesdev.ofingidor.ui.semiHeteronymChipBorderColor
import me.davidgomesdev.ofingidor.ui.semiHeteronymChipColor
import me.davidgomesdev.ofingidor.ui.semiHeteronymChipTextColor
import ofingidor.composeapp.generated.resources.Res
import ofingidor.composeapp.generated.resources.persona_alberto_caeiro
import ofingidor.composeapp.generated.resources.persona_alvaro_de_campos
import ofingidor.composeapp.generated.resources.persona_bernardo_soares
import ofingidor.composeapp.generated.resources.persona_fernando_pessoa
import ofingidor.composeapp.generated.resources.persona_o_fingidor
import ofingidor.composeapp.generated.resources.persona_ricardo_reis
import org.jetbrains.compose.resources.painterResource

private val portraits = mapOf(
    Persona.FERNANDO_PESSOA to PersonaPortrait(
        Res.drawable.persona_fernando_pessoa,
        "Retrato de Fernando Pessoa",
    ),
    Persona.ALBERTO_CAEIRO to PersonaPortrait(
        Res.drawable.persona_alberto_caeiro,
        "Retrato de Alberto Caeiro",
    ),
    Persona.ALVARO_DE_CAMPOS to PersonaPortrait(
        Res.drawable.persona_alvaro_de_campos,
        "Retrato de Álvaro de Campos",
    ),
    Persona.RICARDO_REIS to PersonaPortrait(
        Res.drawable.persona_ricardo_reis,
        "Retrato de Ricardo Reis",
    ),
    Persona.BERNARDO_SOARES to PersonaPortrait(
        Res.drawable.persona_bernardo_soares,
        "Retrato de Bernardo Soares",
    ),
    Persona.O_FINGIDOR to PersonaPortrait(
        Res.drawable.persona_o_fingidor,
        "Retrato de O Fingidor",
    ),
)

data class PortraitChipLayout(
    val label: String,
    val selected: Boolean,
    val backgroundColor: Color,
    val borderColor: Color,
    val labelColor: Color,
    val avatarContentDescription: String?,
    val showPortrait: Boolean = true,
)

data class PersonaIdentityChipModel(
    val persona: Persona,
    val layout: PortraitChipLayout,
)

data class ChatPortraitIdentity(
    val label: String,
    val compact: Boolean = true,
)

data class DebatePortraitIdentity(
    val label: String,
)

val defaultAvatarSize: Dp = 28.dp
private val compactChatAvatarSize: Dp = 24.dp

fun resolveChatAvatarSize(identity: ChatPortraitIdentity): Dp =
    if (identity.compact) compactChatAvatarSize else defaultAvatarSize

enum class AvatarContentDescriptionMode {
    MEANINGFUL,
    DECORATIVE,
}

fun resolveAvatarContentDescription(
    portrait: PersonaPortrait,
    requestedContentDescription: String?,
    mode: AvatarContentDescriptionMode,
): String? = when (mode) {
    AvatarContentDescriptionMode.MEANINGFUL -> requestedContentDescription ?: portrait.contentDescription
    AvatarContentDescriptionMode.DECORATIVE -> null
}

@Suppress("UNUSED_PARAMETER")
fun portraitChipLayout(persona: Persona, isCompact: Boolean, isSelected: Boolean): PortraitChipLayout {
    val category = persona.category
    val backgroundColor = when {
        isSelected && category == PersonaCategory.ORTONIMO -> orthonymChipColor
        isSelected && category == PersonaCategory.SEMI_HETERONIMO -> semiHeteronymChipColor
        isSelected && category == PersonaCategory.DEV -> devChipColor
        isSelected -> componentColumnBackgroundColor
        else -> Color.Transparent
    }
    val borderColor = when {
        isSelected && category == PersonaCategory.ORTONIMO -> orthonymChipBorderColor
        isSelected && category == PersonaCategory.SEMI_HETERONIMO -> semiHeteronymChipBorderColor
        isSelected && category == PersonaCategory.DEV -> devChipBorderColor
        isSelected -> focusedIndicatorColor
        else -> focusedIndicatorColor.copy(alpha = 0.3f)
    }
    val labelColor = when {
        isSelected && category == PersonaCategory.ORTONIMO -> orthonymChipTextColor
        isSelected && category == PersonaCategory.SEMI_HETERONIMO -> semiHeteronymChipTextColor
        isSelected && category == PersonaCategory.DEV -> devChipTextColor
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.4f)
    }

    return PortraitChipLayout(
        label = persona.displayName,
        selected = isSelected,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        labelColor = labelColor,
        avatarContentDescription = null,
        showPortrait = true,
    )
}

fun personaIdentityChipModel(persona: Persona, isCompact: Boolean, isSelected: Boolean): PersonaIdentityChipModel =
    PersonaIdentityChipModel(
        persona = persona,
        layout = portraitChipLayout(persona = persona, isCompact = isCompact, isSelected = isSelected),
    )

fun chatPortraitIdentity(persona: Persona): ChatPortraitIdentity =
    ChatPortraitIdentity(label = persona.displayName)

fun debatePortraitIdentity(persona: Persona): DebatePortraitIdentity =
    DebatePortraitIdentity(label = persona.displayName)

@Composable
fun PersonaPortraitThumbnail(persona: Persona, modifier: Modifier = Modifier) {
    val portrait = requireNotNull(personaPortrait(persona)) { "Missing portrait for $persona" }
    PersonaPortraitThumbnail(portrait = portrait, modifier = modifier)
}

@Composable
fun PersonaPortraitThumbnail(portrait: PersonaPortrait, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(portrait.resource),
        contentDescription = portrait.contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
    )
}

@Composable
fun PersonaAvatar(
    persona: Persona,
    modifier: Modifier = Modifier.size(defaultAvatarSize),
    contentDescription: String? = null,
    contentDescriptionMode: AvatarContentDescriptionMode = AvatarContentDescriptionMode.MEANINGFUL,
) {
    val portrait = requireNotNull(personaPortrait(persona)) { "Missing portrait for $persona" }
    Image(
        painter = painterResource(portrait.resource),
        contentDescription = resolveAvatarContentDescription(
            portrait = portrait,
            requestedContentDescription = contentDescription,
            mode = contentDescriptionMode,
        ),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
    )
}

@Composable
fun PersonaIdentityChip(
    model: PersonaIdentityChipModel,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(model.layout.backgroundColor)
            .border(1.dp, model.layout.borderColor, RoundedCornerShape(20.dp))
            .semantics { selected = model.layout.selected }
            .clickable(onClick = onSelected)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model.layout.showPortrait) {
            PersonaAvatar(
                persona = model.persona,
                contentDescription = model.layout.avatarContentDescription,
                contentDescriptionMode = AvatarContentDescriptionMode.DECORATIVE,
            )
        }
        Text(model.layout.label, color = model.layout.labelColor)
    }
}

fun personaPortrait(persona: Persona): PersonaPortrait? = portraits[persona]
