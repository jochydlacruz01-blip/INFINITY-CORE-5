package com.freerdp.freerdpcore.presentation;

import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.freerdp.freerdpcore.R;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Offline-only FreeRDP help viewer.
 *
 * Help pages are loaded from packaged assets. JavaScript, content providers,
 * network loads, external navigation and access to arbitrary local files are disabled.
 */
public final class HelpActivity extends AppCompatActivity {
    private static final Pattern LOCALIZED_HELP_PATH =
            Pattern.compile("^/android_asset/[a-z]{2,3}_help_page/.*$");
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        webView = findViewById(R.id.activity_help_webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowContentAccess(false);
        // Android asset URLs require file access; WebViewClient below restricts it
        // to the packaged help directories and denies all other file URLs.
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowedHelpAsset(request.getUrl());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isAllowedHelpAsset(request.getUrl())) {
                    return null;
                }
                return forbiddenResponse();
            }
        });

        populate();
    }

    private static boolean isAllowedHelpAsset(Uri uri) {
        if (uri == null || !"file".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String path = uri.getPath();
        if (path == null || path.contains("..") || path.indexOf((char) 92) >= 0) {
            return false;
        }
        return path.startsWith("/android_asset/help_page/")
                || LOCALIZED_HELP_PATH.matcher(path).matches();
    }

    private static WebResourceResponse forbiddenResponse() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                403,
                "Forbidden",
                Collections.emptyMap(),
                new ByteArrayInputStream(new byte[0])
        );
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void populate() {
        String filename = (getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
                ? "gestures.html" : "gestures_phone.html";

        Locale locale = Locale.getDefault();
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        String directory = language + "_help_page/";
        String file = directory + filename;

        try (InputStream input = getAssets().open(file)) {
            // Opening the stream verifies the localized asset exists.
        } catch (IOException missingLocalizedAsset) {
            directory = "help_page/";
            file = directory + filename;
        }

        webView.loadUrl("file:///android_asset/" + file);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
