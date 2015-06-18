package com.lnikkila.oidcsample;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;

import com.github.kevinsawicki.http.HttpRequest;
import com.lnikkila.oidc.authenticator.Authenticator;

import java.io.IOException;
import java.util.Map;

/**
 * Initiates the login procedures and contains all UI stuff related to the main activity.
 *
 * @author Leo Nikkilä
 * @author Camilo Montes
 */
public class HomeActivity extends Activity {

    private Button loginButton;
    private Button requestButton;
    private Button logoutButton;
    private ProgressBar progressBar;

    private AccountManager accountManager;
    private Account availableAccounts[];
    private int selectedAccountIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        loginButton = (Button) findViewById(R.id.loginButton);
        requestButton = (Button) findViewById(R.id.requestButton);
        logoutButton = (Button) findViewById(R.id.logoutButton);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);

        accountManager = AccountManager.get(this);

        //TODO: place this in the app start
        android.webkit.CookieSyncManager.createInstance(this.getApplicationContext());
        // unrelated, just make sure cookies are generally allowed
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);

        // magic starts here
        WebkitCookieManagerProxy coreCookieManager = new WebkitCookieManagerProxy(null, java.net.CookiePolicy.ACCEPT_ALL);
        java.net.CookieHandler.setDefault(coreCookieManager);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Grab all our accounts
        String accountType = getString(R.string.ACCOUNT_TYPE);
        availableAccounts = accountManager.getAccountsByType(accountType);

        if (availableAccounts.length > 0) {
            requestButton.setVisibility(View.VISIBLE);
            logoutButton.setVisibility(View.VISIBLE);
        } else {
            requestButton.setVisibility(View.INVISIBLE);
            requestButton.setText(R.string.requestButton);
            logoutButton.setVisibility(View.INVISIBLE);
            logoutButton.setText(R.string.loginButtonText);
        }
    }

    /**
     * Called when the user taps the big yellow button.
     */
    public void doLogin(final View view) {
        String accountType = getString(R.string.ACCOUNT_TYPE);

        Bundle options =  new Bundle();
        options.putString("clientId", Config.clientId);
        options.putString("clientSecret", Config.clientSecret);
        options.putString("redirectUrl", Config.redirectUrl);
        options.putStringArray("scopes", Config.scopes);

        switch (availableAccounts.length) {
            // No account has been created, let's create one now
            case 0:
                accountManager.addAccount(accountType, Authenticator.TOKEN_TYPE_ID, null, options,
                        this, new AccountManagerCallback<Bundle>() {
                            @Override
                            public void run(AccountManagerFuture<Bundle> futureManager) {
                                // Unless the account creation was cancelled, try logging in again
                                // after the account has been created.
                                if (futureManager.isCancelled()) return;
                                doLogin(view);
                            }
                        }, null);
                break;

            // There's just one account, let's use that
            case 1:
                new ApiTask().execute(availableAccounts[0]);
                break;

            // Multiple accounts, let the user pick one
            default:
                String name[] = new String[availableAccounts.length];

                for (int i = 0; i < availableAccounts.length; i++) {
                    name[i] = availableAccounts[i].name;
                }

                new AlertDialog.Builder(this)
                        .setTitle("Choose an account")
                        .setAdapter(new ArrayAdapter<>(this,
                                        android.R.layout.simple_list_item_1, name),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int selectedAccount) {
                                        selectedAccountIndex = selectedAccount;
                                        new ApiTask().execute(availableAccounts[selectedAccountIndex]);
                                    }
                                })
                        .create()
                        .show();
        }
    }

    public void doRequest(View view) {
        new ProtectedResTask().execute(availableAccounts[selectedAccountIndex]);
    }

    public void doLogout(View view) {
        new LogoutTask().execute(availableAccounts[selectedAccountIndex]);
    }

    private class ApiTask extends AsyncTask<Account, Void, Map> {

        @Override
        protected void onPreExecute() {
            loginButton.setText("");
            progressBar.setVisibility(View.VISIBLE);
        }

        /**
         * Makes the API request. We could use the OIDCUtils.getUserInfo() method, but we'll do it
         * like this to illustrate making generic API requests after we've logged in.
         */
        @Override
        protected Map doInBackground(Account... args) {
            Account account = args[0];

            try {
                return APIUtility.getJson(HomeActivity.this, com.lnikkila.oidc.Config.userInfoUrl, account);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        /**
         * Processes the API's response.
         */
        @Override
        protected void onPostExecute(Map result) {
            progressBar.setVisibility(View.INVISIBLE);

            if (result == null) {
                loginButton.setText("Couldn't get user info");
            } else {
                loginButton.setText("Logged in as " + result.get("given_name"));
            }
        }
    }

    private class ProtectedResTask extends AsyncTask<Account, Void, Map> {

        @Override
        protected void onPreExecute() {
            requestButton.setText("");
            progressBar.setVisibility(View.VISIBLE);
        }

        /**
         * Makes the API request to an SP.
         */
        @Override
        protected Map doInBackground(Account... args) {
            Account account = args[0];

            try {
                return APIUtility.getJson(HomeActivity.this, "http://openam.example.com:8080/rs/", account);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        /**
         * Processes the API's response.
         */
        @Override
        protected void onPostExecute(Map result) {
            progressBar.setVisibility(View.INVISIBLE);

            if (result == null) {
                requestButton.setText("Couldn't get request result");
            } else {
                requestButton.setText(result.toString());
            }
        }
    }

    private class LogoutTask extends AsyncTask<Account, Void, String> {

        @Override
        protected void onPreExecute() {
            logoutButton.setText("");
            progressBar.setVisibility(View.VISIBLE);
        }

        /**
         * Makes the API request. We could use the OIDCUtils.getUserInfo() method, but we'll do it
         * like this to illustrate making generic API requests after we've logged in.
         */
        @Override
        protected String doInBackground(Account... args) {
            Account account = args[0];

            Bundle options =  new Bundle();
            options.putString("clientId", Config.clientId);
            options.putString("clientSecret", Config.clientSecret);
            options.putString("redirectUrl", Config.redirectUrl);
            options.putStringArray("scopes", Config.scopes);

            try {
                String accessToken;

                // Try retrieving an access token from the account manager. The boolean true in the invocation
                // tells Android to show a notification if the token can't be retrieved. When the
                // notification is selected, it will launch the intent for re-authorisation. You could
                // launch it automatically here if you wanted to by grabbing the intent from the bundle.
                try {

                    AccountManagerFuture<Bundle> futureManager = accountManager.getAuthToken(account,
                            Authenticator.TOKEN_TYPE_ACCESS, options, true, null, null);
                    accessToken = futureManager.getResult().getString(AccountManager.KEY_AUTHTOKEN);
                } catch (Exception e) {
                    throw new IOException("Could not get access token from account.", e);
                }

                // Prepare an API request using the accessToken
                HttpRequest request = new HttpRequest("http://openam.example.com:8080/openam/frrest/oauth2/token/"+accessToken, HttpRequest.METHOD_DELETE);
                request.contentType(HttpRequest.CONTENT_TYPE_JSON);
                if (request.ok()) {
                    return request.body();
                } else {
                    String requestContent = "empty body";
                    try {
                        requestContent = request.body();
                    } catch (HttpRequest.HttpRequestException e) {
                        //Nothing to do, the response has no body or couldn't fetch it
                        e.printStackTrace();
                    }
                    throw new IOException(request.code() + " " + request.message() + " " + requestContent);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        /**
         * Processes the API's response.
         */
        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.INVISIBLE);

            if (result == null) {
                logoutButton.setText("Couldn't logout");
            } else {
                logoutButton.setText(result);
            }
        }
    }
}
