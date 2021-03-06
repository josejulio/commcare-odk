package org.commcare.android.tasks.network;

import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.tasks.DataPullTask;

import java.io.IOException;

/**
 * Builds data pulling object that requests remote data and handles the response.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DataPullResponseFactory implements DataPullRequester {
    public DataPullResponseFactory() {
    }

    @Override
    public RemoteDataPullResponse makeDataPullRequest(DataPullTask task, HttpRequestGenerator requestor, String server, boolean includeSyncToken) throws IOException {
        return new RemoteDataPullResponse(task, requestor, server, includeSyncToken);
    }
}
