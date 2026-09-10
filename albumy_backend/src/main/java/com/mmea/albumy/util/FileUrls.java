package com.mmea.albumy.util;

import com.mmea.albumy.model.Event;

/** Stable url helpers for stored media files. */
public final class FileUrls {

    private FileUrls() {
    }

    public static String coverUrl(Event event) {
        return event.getCoverFileName() == null ? null : "/files/" + event.getCoverFileName();
    }
}