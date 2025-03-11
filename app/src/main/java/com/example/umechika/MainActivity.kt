package com.example.umechika

import com.example.umechika.ui.screen.ShowMap
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.NavigationBar

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.umechika.ui.theme.UmechikaTheme
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp


class MainActivity : ComponentActivity() {

    // 位置情報取得の権限を確認する
    private lateinit var permissionsManager: PermissionsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup {
                NavigationOptions.Builder(this)
                    .accessToken(getString( R.string.mapbox_access_token) ) // 🚀 APIトークンをセット
                    .build()
            }.attachAllActivities(this.application)
        }
        // 位置情報の権限を保持しているかを確認
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            setContent {
                ShowMap()
            }
        } else {

            // 要求する権限とそれに対する動作の定義
            permissionsManager = PermissionsManager(object : PermissionsListener {
                // 権限取得のメッセージ
                override fun onExplanationNeeded(permissionsToExplain: List<String>) {}

                // 権限要求後の動作
                override fun onPermissionResult(granted: Boolean) {
                    if (granted) {  // 許可されたら地図を表示
                        setContent {
                            ShowMap()
                            NavigationBar {  }
                        }
                    } else {        // 設定画面への遷移を促すダイアログを表示
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("権限が必要です")
                            .setMessage("位置情報の権限が必要です。設定画面に移動しますか？")
                            .setPositiveButton("はい") { _, _ ->
                                val intent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                val uri = Uri.fromParts("package", packageName, null)
                                intent.setData(uri)
                                startActivity(intent)
                            }
                            .setNegativeButton("いいえ", null)
                            .show()
                    }
                }
            })
            // 位置情報権限の要求
            permissionsManager.requestLocationPermissions(this)
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    UmechikaTheme {
        Greeting("Android")
    }
}