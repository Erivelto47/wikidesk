package wikidesk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wikidesk.git.GitCloneState
import wikidesk.git.GitCredentials
import wikidesk.git.repoNameFromUrl
import wikidesk.platform.pickWorkspaceDirectory
import wikidesk.ui.theme.AppTypography
import wikidesk.ui.theme.LocalAppColors
import java.io.File

private enum class AddSourceTab { LOCAL, GIT }

/**
 * Modal "Adicionar fonte": permite abrir uma nova pasta local (via seletor
 * nativo, delegado ao chamador através de [onPickLocalFolder]) ou clonar um
 * repositório Git — HTTPS com usuário/token opcional, ou SSH usando as
 * chaves já configuradas no sistema (`~/.ssh`).
 *
 * O envio da aba Git é assíncrono: [cloneState] reflete o progresso vindo de
 * `AppState.gitCloneState`, e o modal se fecha sozinho assim que o clone
 * termina com sucesso (transição InProgress → Idle). Em caso de erro, a
 * mensagem fica visível para o usuário tentar de novo sem perder o que já
 * preencheu.
 */
@Composable
fun AddSourceModal(
    cloneState: GitCloneState,
    onDismiss: () -> Unit,
    onPickLocalFolder: () -> Unit,
    onSubmitGit: (remoteUrl: String, branch: String?, destination: File?, credentials: GitCredentials?) -> Unit
) {
    val colors = LocalAppColors.current
    var selectedTab by remember { mutableStateOf(AddSourceTab.LOCAL) }
    var gitUrl by remember { mutableStateOf("") }
    var gitBranch by remember { mutableStateOf("") }
    var gitUsername by remember { mutableStateOf("") }
    var gitToken by remember { mutableStateOf("") }
    var customDestination by remember { mutableStateOf<String?>(null) }
    var wasInProgress by remember { mutableStateOf(false) }

    val isSsh = gitUrl.startsWith("git@") || gitUrl.startsWith("ssh://")
    val isInProgress = cloneState is GitCloneState.InProgress

    // Fecha o modal sozinho quando um clone em andamento termina com sucesso
    // (o estado volta para Idle sem passar por Error).
    LaunchedEffect(cloneState) {
        if (cloneState is GitCloneState.InProgress) wasInProgress = true
        if (wasInProgress && cloneState is GitCloneState.Idle) onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.overlay)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isInProgress) onDismiss() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.modalBackground)
                .border(width = 1.dp, color = colors.borderStrong, shape = RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // absorve cliques para não fechar o modal
                )
                .padding(20.dp)
        ) {
            Text(
                text = "Adicionar fonte",
                style = AppTypography.heading3,
                color = colors.text,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Abra uma pasta local com documentação Markdown ou clone um repositório Git.",
                style = AppTypography.caption,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.searchBarBackground)
                    .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(6.dp))
                    .padding(3.dp)
            ) {
                SourceTabButton(
                    text = "Pasta local",
                    selected = selectedTab == AddSourceTab.LOCAL,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = AddSourceTab.LOCAL }
                )
                SourceTabButton(
                    text = "Repositório Git",
                    selected = selectedTab == AddSourceTab.GIT,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = AddSourceTab.GIT }
                )
            }

            Column(modifier = Modifier.padding(top = 16.dp)) {
                when (selectedTab) {
                    AddSourceTab.LOCAL -> {
                        Text(
                            text = "Escolha uma pasta no seu computador. Todos os arquivos .md dentro dela (e de suas subpastas) serão indexados.",
                            style = AppTypography.caption,
                            color = colors.textMuted,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                        PrimaryButton(text = "Selecionar pasta…", onClick = onPickLocalFolder)
                    }

                    AddSourceTab.GIT -> {
                        ModalTextField(
                            label = "URL do repositório",
                            value = gitUrl,
                            placeholder = "https://github.com/org/repo.git",
                            enabled = !isInProgress,
                            onValueChange = { gitUrl = it }
                        )
                        Box(modifier = Modifier.padding(top = 10.dp)) {
                            ModalTextField(
                                label = "Branch (opcional)",
                                value = gitBranch,
                                placeholder = "main",
                                enabled = !isInProgress,
                                onValueChange = { gitBranch = it }
                            )
                        }

                        if (gitUrl.isNotBlank() && isSsh) {
                            Text(
                                text = "Repositório via SSH: será usada a chave já configurada em ~/.ssh no seu sistema.",
                                style = AppTypography.caption,
                                color = colors.textFaint,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        } else if (gitUrl.isNotBlank()) {
                            Box(modifier = Modifier.padding(top = 10.dp)) {
                                ModalTextField(
                                    label = "Usuário (opcional)",
                                    value = gitUsername,
                                    placeholder = "seu usuário",
                                    enabled = !isInProgress,
                                    onValueChange = { gitUsername = it }
                                )
                            }
                            Box(modifier = Modifier.padding(top = 10.dp)) {
                                ModalTextField(
                                    label = "Token de acesso (deixe em branco se o repositório é público)",
                                    value = gitToken,
                                    placeholder = "ghp_…",
                                    isPassword = true,
                                    enabled = !isInProgress,
                                    onValueChange = { gitToken = it }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Destino:",
                                style = AppTypography.caption,
                                color = colors.textMuted
                            )
                            val repoFolderName = repoNameFromUrl(gitUrl).ifBlank { "repositório" }
                            val destinationPreview = customDestination?.let { "$it/$repoFolderName" }
                                ?: "pasta de dados do app / $repoFolderName"
                            Text(
                                text = destinationPreview,
                                style = AppTypography.caption,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(5.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = !isInProgress,
                                        onClick = { pickWorkspaceDirectory()?.let { customDestination = it } }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Escolher…", style = AppTypography.hint, color = colors.textMuted)
                            }
                        }

                        Text(
                            text = "Uma pasta nova com o nome do repositório é criada automaticamente dentro do destino escolhido.",
                            style = AppTypography.hint,
                            color = colors.textFaint,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        if (cloneState is GitCloneState.Error) {
                            Text(
                                text = cloneState.message,
                                style = AppTypography.caption,
                                color = colors.accent,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }

                        Box(modifier = Modifier.padding(top = 14.dp)) {
                            PrimaryButton(
                                text = if (isInProgress) "Clonando…" else "Clonar",
                                enabled = gitUrl.isNotBlank() && !isInProgress,
                                onClick = {
                                    val credentials = if (!isSsh && gitToken.isNotBlank()) {
                                        GitCredentials(gitUsername, gitToken)
                                    } else {
                                        null
                                    }
                                    onSubmitGit(
                                        gitUrl.trim(),
                                        gitBranch.trim().ifBlank { null },
                                        customDestination?.let { File(it) },
                                        credentials
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceTabButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) colors.background else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AppTypography.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) colors.text else colors.textMuted
        )
    }
}

@Composable
private fun ModalTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    isPassword: Boolean = false
) {
    val colors = LocalAppColors.current
    Column {
        Text(
            text = label,
            style = AppTypography.caption,
            color = colors.textMuted,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.searchBarBackground)
                .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(placeholder, style = AppTypography.caption, color = colors.textFaint)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = enabled,
                visualTransformation = if (isPassword) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                textStyle = TextStyle(fontSize = AppTypography.caption.fontSize, color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) colors.accent else colors.border)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AppTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) Color.White else colors.textFaint
        )
    }
}
