package ir.xilo.app.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.ui.components.PostField
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTextArea
import ir.xilo.app.ui.components.XiloTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    onBackClick: () -> Unit,
    onPostCreated: () -> Unit,
    editPostId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(
        key = "create-post-${editPostId.orEmpty()}",
    ),
) {
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val isLoadingEdit by viewModel.isLoadingEdit.collectAsState()
    val error by viewModel.error.collectAsState()
    val fieldErrors by viewModel.fieldErrors.collectAsState()
    val success by viewModel.success.collectAsState()
    val allowed by viewModel.allowed.collectAsState()
    val tagSuggestions by viewModel.tagSuggestions.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = !editPostId.isNullOrBlank()

    LaunchedEffect(editPostId) {
        viewModel.prepare(editPostId)
    }

    LaunchedEffect(allowed) {
        if (allowed == false) {
            onBackClick()
        }
    }

    LaunchedEffect(success) {
        if (success) {
            viewModel.consumeSuccess()
            onPostCreated()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrors()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isEditing) R.string.post_edit_title else R.string.post_create_title
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        XiloIcon(icon = XiloIcons.Close, contentDescription = stringResource(R.string.common_close))
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.submit() },
                        enabled = !isSubmitting && !isLoadingEdit,
                        colors = ButtonDefaults.buttonColors(containerColor = XiloBlue),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (isEditing) R.string.post_edit_save else R.string.post_create_publish
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (isSubmitting || isLoadingEdit) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = XiloBlue)
                Spacer(modifier = Modifier.height(8.dp))
            }

            XiloTextField(
                value = title,
                onValueChange = viewModel::updateTitle,
                placeholder = stringResource(R.string.post_title_placeholder),
                modifier = Modifier.fillMaxWidth(),
                isError = fieldErrors.containsKey(PostField.Title),
                errorText = fieldErrors[PostField.Title],
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (tagSuggestions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    items(tagSuggestions, key = { it.tag }) { item ->
                        AssistChip(
                            onClick = { viewModel.applyTagSuggestion(item.tag) },
                            label = {
                                Text(
                                    text = "#${item.tag}",
                                    color = XiloBlue,
                                )
                            },
                        )
                    }
                }
            }

            XiloTextArea(
                value = content,
                onValueChange = viewModel::updateContent,
                placeholder = stringResource(R.string.post_body_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 6,
                isError = fieldErrors.containsKey(PostField.Content),
                errorText = fieldErrors[PostField.Content],
                transparentBorder = fieldErrors[PostField.Content] == null,
            )
        }
    }
}
