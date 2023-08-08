package com.test.googleapistest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Scope
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.Credentials
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.UserCredentials
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.PhotosLibrarySettings
import com.squareup.okhttp.Callback
import com.squareup.okhttp.FormEncodingBuilder
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import com.test.googleapistest.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener {

    private val RC_SIGN_IN = 9001
    private lateinit var mGoogleApiClient: GoogleApiClient
    private lateinit var binding: ActivityMainBinding
    private val SCOPES = "https://www.googleapis.com/auth/photoslibrary.readonly"
    private lateinit var photosLibraryClient: PhotosLibraryClient

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FirebaseAnalytics.getInstance(this)
        startAuthentication()
    }

    private fun startAuthentication() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestServerAuthCode(
                getString(R.string.client_id), false
            )
            .requestScopes(Scope(SCOPES))
            .build()
        mGoogleApiClient = GoogleApiClient.Builder(this)
            .enableAutoManage(
                this /* FragmentActivity */,
                this /* OnConnectionFailedListener */
            )
            .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
            .build()
        // Call this method when you want to start the OAuth2 authentication process
        startSignIn()
    }

    private fun startSignIn() {
        val signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient)
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                getAccessToken(account)
            } catch (e: ApiException) {
                Log.d(TAG, "Exception handled while signing in  :$=" + e.message)
            }
        }
    }

    fun getAccessToken(googleSignInAccount: GoogleSignInAccount?) {
        if (googleSignInAccount == null) return
        lifecycleScope.launch(context = Dispatchers.IO) {
            val client = OkHttpClient()
            val requestBody = FormEncodingBuilder()
                .add("grant_type", "authorization_code")
                .add("client_id", getString(R.string.client_id))
                .add("client_secret", getString(R.string.client_secret))
                .add("redirect_uri", "your_redirect_uri")
                .add("code", googleSignInAccount.serverAuthCode)
                .build()
            val request = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v4/token")
                .post(requestBody)
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(request: Request, e: IOException) {
                    Log.d(TAG, "getAccessToken : onFailure ===" + e.message)
                }

                @Throws(IOException::class)
                override fun onResponse(response: Response) {
                    try {
                        val jsonObject = JSONObject(response.body().string())
                        val token = jsonObject.optString("access_token")
                        initializePhotoLibraryClient(token)
                    } catch (e: JSONException) {
                        Log.d(TAG, "getAccessToken : exception handled ===" + e.message)
                        e.printStackTrace()
                    }
                }
            })
        }
    }

    fun initializePhotoLibraryClient(token: String) {
        val settings = PhotosLibrarySettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(getUserCredentials(token)))
            .build()
        photosLibraryClient = PhotosLibraryClient.initialize(settings)
        fetchPhotos()
    }

    private fun getUserCredentials(token: String): Credentials {
        val accessToken = AccessToken(token, null)
        return UserCredentials.newBuilder()
            .setClientId(getString(R.string.client_id))
            .setClientSecret(getString(R.string.client_secret))
            .setAccessToken(accessToken)
            .build()
    }

    fun fetchPhotos() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = photosLibraryClient.listMediaItems()
                val mediaItems = response.iterateAll().toList()
                for (mediaItem in mediaItems) {
                    // Access media item properties
                    val id = mediaItem.id
                    val baseUrl = mediaItem.baseUrl
                    Log.d(TAG, "fetchPhotos: each item : ${mediaItem}" )
                }
            } catch (e: Exception) {
                Log.d(TAG, "fetchPhotos: exception handled ====" + e)
            }
        }
    }

    override fun onConnectionFailed(p0: ConnectionResult) {

    }
}