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
package com.crimsonhexagon.rsm.note;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import javax.servlet.http.Cookie;

import org.apache.catalina.authenticator.SavedRequest;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Converts tomcat non-serializable SavedRequest to a serializable
 * version.
 *
 * @author Adrian
 */
public class SavedRequestConverter implements Function<Object, Object> {

    @Override
    public Object apply(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof SavedRequest) {
            return new SerializableSavedRequest((SavedRequest) o);
        }
        if (o instanceof SerializableSavedRequest) {
            return ((SerializableSavedRequest) o).toSavedRequest();
        }
        throw new IllegalArgumentException ("Cannot convert instance of type " + o.getClass().getName());
    }

    public static class SerializableSavedRequest implements Serializable {

        private static final long serialVersionUID = 6668024035102051230L;

        private List<Cookie> cookies = new ArrayList<>();
        private Map<String, List<String>> headers = new HashMap<>();
        private List<Locale> locales = new ArrayList<>();
        private ByteChunk body;
        private String contentType;
        private String decodedRequestURI;
        private String requestURI;
        private String method;
        private String queryString;

        public SerializableSavedRequest(SavedRequest savedRequest) {
            Iterator<String> headerNames = savedRequest.getHeaderNames();
            while (headerNames.hasNext()) {
                String name = headerNames.next();
                List<String> values = new ArrayList<>();
                savedRequest.getHeaderValues(name).forEachRemaining(values::add);
                this.headers.put(name, values);
            }
            savedRequest.getLocales().forEachRemaining(this.locales::add);
            this.body = savedRequest.getBody();
            this.contentType = savedRequest.getContentType();
            this.decodedRequestURI = savedRequest.getDecodedRequestURI();
            this.requestURI = savedRequest.getRequestURI();
            this.method = savedRequest.getMethod();
            this.queryString = savedRequest.getQueryString();
            savedRequest.getCookies().forEachRemaining(cookies::add);
        }

        public SavedRequest toSavedRequest() {
            SavedRequest savedRequest = new SavedRequest();
            cookies.stream().forEach(savedRequest::addCookie);
            headers.forEach((name, values) -> {
                values.forEach(value -> savedRequest.addHeader(name, value));
            });
            locales.forEach(savedRequest::addLocale);
            savedRequest.setBody(body);
            savedRequest.setContentType(contentType);
            savedRequest.setDecodedRequestURI(decodedRequestURI);
            savedRequest.setRequestURI(requestURI);
            savedRequest.setMethod(method);
            savedRequest.setQueryString(queryString);
            return savedRequest;
        }
    }
}
