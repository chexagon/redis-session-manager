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

import java.io.IOException;
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
import org.junit.Assert;
import org.junit.Test;

public class SavedRequestConverterTest {

    private SavedRequestConverter converter = new SavedRequestConverter();

    @Test
    public void testConversion() throws IOException {
        SavedRequest savedRequest = new SavedRequest();
        savedRequest.setRequestURI("http://someuri");
        savedRequest.setQueryString("queryString");
        savedRequest.setMethod("GET");
        savedRequest.setDecodedRequestURI("http://decodeduri");
        savedRequest.setContentType("text/html");
        ByteChunk body = new ByteChunk();
        body.append((byte) 5);
        savedRequest.setBody(body);
        savedRequest.addHeader("Accept", "text/html");
        savedRequest.addHeader("Accept", "text/xml");
        savedRequest.addCookie(new Cookie("name", "value"));
        savedRequest.addLocale(Locale.GERMAN);
        SavedRequestConverter.SerializableSavedRequest serializableRequest =
                (SavedRequestConverter.SerializableSavedRequest) converter.apply(savedRequest);
        SavedRequest result = serializableRequest.toSavedRequest();
        Assert.assertEquals(savedRequest.getRequestURI(), result.getRequestURI());
        Assert.assertEquals(savedRequest.getBody(), result.getBody());
        Assert.assertEquals(savedRequest.getContentType(), result.getContentType());
        List<Cookie> cookies = new ArrayList<>();
        Assert.assertEquals(toList(savedRequest.getCookies()), toList(result.getCookies()));
        Assert.assertEquals(savedRequest.getDecodedRequestURI(), result.getDecodedRequestURI());
        Assert.assertEquals(toList(savedRequest.getHeaderNames()), toList(result.getHeaderNames()));
        Assert.assertEquals(toList(savedRequest.getHeaderValues("Accept")),
                toList(result.getHeaderValues("Accept")));
        Assert.assertEquals(toList(savedRequest.getLocales()), toList(result.getLocales()));
        Assert.assertEquals(savedRequest.getMethod(), result.getMethod());
        Assert.assertEquals(savedRequest.getQueryString(), result.getQueryString());
    }

    @Test
    public void testNullValue() {
        Assert.assertNull(converter.apply(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidClass() {
        converter.apply("invalidClass");
    }

    private <E> List<E> toList(Iterator<E> iterator) {
        List<E> list = new ArrayList<>();
        iterator.forEachRemaining(list::add);
        return list;
    }
}
