package com.freerdp.freerdpcore.services;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.freerdp.freerdpcore.domain.ConnectionReference;
import com.freerdp.freerdpcore.presentation.BookmarkActivity;
import com.freerdp.freerdpcore.presentation.SessionActivity;

/** Safely routes validated FreeRDP intents to the session or bookmark UI. */
public final class SessionRequestHandlerActivity extends AppCompatActivity {
    private final ActivityResultLauncher<Intent> launcher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                setResult(result.getResultCode());
                finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void startSessionWithConnectionReference(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString(SessionActivity.PARAM_CONNECTION_REFERENCE, reference);
        Intent sessionIntent = new Intent(this, SessionActivity.class);
        sessionIntent.putExtras(bundle);
        launcher.launch(sessionIntent);
    }

    private void editBookmarkWithConnectionReference(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString(BookmarkActivity.PARAM_CONNECTION_REFERENCE, reference);
        Intent bookmarkIntent = new Intent(getApplicationContext(), BookmarkActivity.class);
        bookmarkIntent.putExtras(bundle);
        launcher.launch(bookmarkIntent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_SEARCH.equals(action)) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            if (query != null && !query.trim().isEmpty()) {
                startSessionWithConnectionReference(
                        ConnectionReference.getHostnameReference(query.trim()));
            }
        } else if (Intent.ACTION_VIEW.equals(action)) {
            startSessionWithConnectionReference(intent.getDataString());
        } else if (Intent.ACTION_EDIT.equals(action)) {
            editBookmarkWithConnectionReference(intent.getDataString());
        }
    }
}
