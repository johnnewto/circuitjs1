package com.lushprojects.circuitjs1.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;

interface InfoViewerTemplateResources extends ClientBundle {
    InfoViewerTemplateResources INSTANCE = GWT.create(InfoViewerTemplateResources.class);

    // Full HTML document shell (head + styles + body opening + script tag).
    @Source("templates/info_viewer_document_shell.html")
    TextResource infoViewerDocumentShell();

    // Shared markdown helper functions injected inside the script body.
    @Source("templates/info_viewer_markdown_helpers.js")
    TextResource infoViewerMarkdownHelpersScript();
}
