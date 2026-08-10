package dev.opengdrive

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.opengdrive.auth.DriveAuthorization
import dev.opengdrive.ui.OpenGDriveApp
import dev.opengdrive.ui.OpenGDriveViewModel
import dev.opengdrive.ui.theme.OpenGDriveTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<OpenGDriveViewModel>()
    private lateinit var authorization: DriveAuthorization

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authorization.consumeResolution(
                result.data,
                viewModel::onAuthorized,
                viewModel::onAuthorizationError,
            )
        } else {
            viewModel.onAuthorizationError(IllegalStateException("Google authorization was cancelled"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authorization = DriveAuthorization(this)
        enableEdgeToEdge()
        setContent {
            OpenGDriveTheme {
                OpenGDriveApp(
                    viewModel = viewModel,
                    onAuthorize = {
                        authorization.authorize(
                            authorizationLauncher,
                            viewModel::onAuthorized,
                            viewModel::onAuthorizationError,
                        )
                    },
                )
            }
        }
    }
}
