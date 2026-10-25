package ec.edu.espe.billeterax


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import ec.edu.espe.billeterax.ui.WalletApp
import ec.edu.espe.billeterax.ui.WalletViewModel
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { val model: WalletViewModel = viewModel(); WalletApp(model) }
    }
}
