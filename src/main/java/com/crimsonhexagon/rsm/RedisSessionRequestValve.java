/*-
 *  Copyright 2015 Crimson Hexagon
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.crimsonhexagon.rsm;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * {@link Valve} to ensure session is persisted to redis after the request has completed
 *
 * @author Steve Ungerer
 */
public class RedisSessionRequestValve extends ValveBase {
	private static final Log log = LogFactory.getLog(RedisSessionRequestValve.class);
	private final RedisSessionManager manager;
	private final Pattern ignorePattern;

	private static final String POST_METHOD = "post";
	// note key to indicate we're going to process this request after completion
	private static final String REQUEST_PROCESSED = "com.crimsonhexagon.rsm.PROCESSED";
	// note key to store the query string
	private static final String REQUEST_QUERY = "com.crimsonhexagon.rsm.QUERY_STRING";

	public RedisSessionRequestValve(RedisSessionManager manager, String ignorePattern) {
		this.manager = manager;
		if (ignorePattern != null && ignorePattern.trim().length() > 0) {
			this.ignorePattern = Pattern.compile(ignorePattern);
		} else {
			this.ignorePattern = null;
		}
	}

	@Override
	public void invoke(Request request, Response response) throws IOException, ServletException {
        Context context = request.getContext();
        if (context == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, sm.getString("standardHost.noContext"));
            return;
        }
        Thread.currentThread().setContextClassLoader(context.getLoader().getClassLoader());
	    
		try {
			final String query = getQueryString(request);
			if (ignorePattern == null || !ignorePattern.matcher(query).matches()) {
				request.setNote(REQUEST_PROCESSED, Boolean.TRUE);
				log.trace("Will save to redis after request for [" + query + "]");
			} else {
				log.trace("Ignoring [" + query + "]");
			}
			getNext().invoke(request, response);
		} finally {
			if (Boolean.TRUE.equals(request.getNote(REQUEST_PROCESSED))) {
				manager.afterRequest();
			}
		}
	}

	@Override
	public boolean isAsyncSupported() {
		return true;
	}

	protected String getQueryString(final Request request) {
		final Object q = request.getNote(REQUEST_QUERY);
		if (q != null) {
			return q.toString();
		}
		final StringBuilder sb = new StringBuilder();
		sb.append(request.getMethod()).append(' ').append(request.getRequestURI());
		if (!isPostMethod(request) && request.getQueryString() != null) {
			sb.append('?').append(request.getQueryString());
		}
		final String result = sb.toString();
		request.setNote(REQUEST_QUERY, result);
		return result;
	}

	protected boolean isPostMethod(final Request request) {
		return POST_METHOD.equalsIgnoreCase(request.getMethod());
	}

}
