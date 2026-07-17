package ir.xilo.app.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val fieldErrors by viewModel.fieldErrors.collectAsState()
    val success by viewModel.success.collectAsState()
    val allowed by viewModel.allowed.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(allowed) {
        if (allowed == false) {
            onBackClick()
        }
    }

    LaunchedEffect(success) {
        if (success) {
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
                title = { Text("ایجاد پست جدید", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        XiloIcon(icon = XiloIcons.Close, contentDescription = "بستن")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.createPost(title, content) },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = XiloBlue),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("انتشار", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            if (isSubmitting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = XiloBlue)
                Spacer(modifier = Modifier.height(8.dp))
            }

            XiloTextField(
                value = title,
                onValueChange = {
                    title = it
                    viewModel.clearFieldError(PostField.Title)
                },
                placeholder = "عنوان پست",
                modifier = Modifier.fillMaxWidth(),
                isError = fieldErrors.containsKey(PostField.Title),
                errorText = fieldErrors[PostField.Title],
            )

            Spacer(modifier = Modifier.height(16.dp))

            XiloTextArea(
                value = content,
                onValueChange = {
                    content = it
                    viewModel.clearFieldError(PostField.Content)
                },
                placeholder = "چه خبر؟ بنویسید...",
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
