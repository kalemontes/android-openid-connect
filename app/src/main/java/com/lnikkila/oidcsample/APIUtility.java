package com.lnikkila.oidcsample;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.os.Bundle;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.Gson;
import com.lnikkila.oidcsample.oidc.OIDCUtils;
import com.lnikkila.oidcsample.oidc.authenticator.Authenticator;

import java.io.IOException;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

/**
 * An incomplete class that illustrates how to make API requests with the Access Token.
 *
 * @author Leo Nikkilä
 */
public class APIUtility {

    /**
     * Makes a GET request and parses the received JSON string as a Map.
     */
    public static Map getJson(Context context, String url, Account account)
            throws IOException {

        String jsonString = makeRequest(context, HttpRequest.METHOD_GET, url, account);
        return new Gson().fromJson(jsonString, Map.class);
    }

    /**
     * Makes an arbitrary HTTP request using the provided account.
     *
     * If the request doesn't execute successfully on the first try, the tokens will be refreshed
     * and the request will be retried. If the second try fails, an exception will be raised.
     */
    public static String makeRequest(Context context, String method, String url, Account account)
            throws IOException {

        return makeRequest(context, method, url, account, true);
    }

    private static String makeRequest(Context context, String method, String url, Account account,
                                     boolean doRetry) throws IOException {

        AccountManager accountManager = AccountManager.get(context);
		String accessToken;

        // Try retrieving an access token from the account manager. The boolean true in the invocation
        // tells Android to show a notification if the token can't be retrieved. When the
        // notification is selected, it will launch the intent for re-authorisation. You could
        // launch it automatically here if you wanted to by grabbing the intent from the bundle.
        try {

            AccountManagerFuture<Bundle> futureManager = accountManager.getAuthToken(account,
                    Authenticator.TOKEN_TYPE_ACCESS, null, true, null, null);
            accessToken = futureManager.getResult().getString(AccountManager.KEY_AUTHTOKEN);
        } catch (Exception e) {
            throw new IOException("Could not get access token from account.", e);
        }

        // Prepare an API request using the accessToken
        HttpRequest request = new HttpRequest(url, method);
        request = OIDCUtils.prepareApiRequest(request, accessToken);

        if (request.ok()) {
            return request.body();
        } else {
            int code = request.code();

            if (doRetry && (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN || code == HTTP_BAD_REQUEST)) {
                // We're being denied access on the first try, let's renew the token and retry
                String accountType = context.getString(R.string.ACCOUNT_TYPE);

                accountManager.setAuthToken(account, Authenticator.TOKEN_TYPE_ID, null);
                accountManager.setAuthToken(account, Authenticator.TOKEN_TYPE_REFRESH, null);

                accountManager.invalidateAuthToken(accountType, accessToken);

                return makeRequest(context, method, url, account, false);
            } else {
                // An unrecoverable error or the renewed token didn't work either
                throw new IOException(request.code() + " " + request.message() + " " + request.body());
            }
        }
    }

}
