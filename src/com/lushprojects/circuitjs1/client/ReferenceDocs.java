package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;

/**
 * Helper for opening project reference markdown files in rendered form.
 *
 * Reads markdown from a served URL and displays it using InfoViewerDialog.
 */
public final class ReferenceDocs {

    private ReferenceDocs() {
    }

    public static void openMarkdownReference(final String title, final String relativeUrl) {
        if (relativeUrl == null || relativeUrl.trim().isEmpty()) {
            return;
        }

        String requestUrl = relativeUrl.trim();
        String viewerDocPath = requestUrl;
        if (viewerDocPath.startsWith("docs/")) {
            viewerDocPath = viewerDocPath.substring("docs/".length());
        }

        final String viewerUrl = GWT.getModuleBaseURL() + "docs/markdown-viewer.html?doc="
                + URL.encodeQueryString(viewerDocPath);

        RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, requestUrl);
        try {
            requestBuilder.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    int status = response.getStatusCode();
                    if (status == 200 || status == 0) {
                        IframeViewerDialog.openDialog(title, viewerUrl, 900, 700);
                        return;
                    }

                    IframeViewerDialog.openDialog(title, viewerUrl, 900, 700);
                }

                @Override
                public void onError(Request request, Throwable exception) {
                    IframeViewerDialog.openDialog(title, viewerUrl, 900, 700);
                }
            });
        } catch (RequestException e) {
            IframeViewerDialog.openDialog(title, viewerUrl, 900, 700);
        }
    }
}