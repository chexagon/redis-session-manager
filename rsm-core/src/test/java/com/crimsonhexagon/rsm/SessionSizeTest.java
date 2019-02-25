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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.catalina.Context;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Random;

public class SessionSizeTest {

    @Test
    public void testValidAttrSize() throws Exception {
        RedisSession rs = session(RedisSessionManager.DO_NOT_CHECK, RedisSessionManager.DO_NOT_CHECK, new MockRedisSessionClient());
        String s = randomString(128);
        rs.setAttribute("foo", s);
        Assert.assertTrue("attribute not stored", s.equals(rs.getAttribute("foo")));
    }

    @Test
    public void testLargeAttrSize() throws Exception {
        String s = randomString(128);
        final int length = new MockRedisSessionClient().getEncodedSize(s);
        RedisSession rs = session(length, RedisSessionManager.DO_NOT_CHECK, new MockRedisSessionClient());
        // == to max, should be stored
        rs.setAttribute("foo", s);
        Assert.assertTrue("attribute not stored", s.equals(rs.getAttribute("foo")));

        s = randomString(length + 1);
        rs.setAttribute("bar", s);
        Assert.assertNull("attribute > max length stored", rs.getAttribute("bar"));
    }

    @Test
    public void testLargeSessionSize() throws Exception {
        String s = randomString(128);
        RedisSessionClient c = mock(RedisSessionClient.class);
        when(c.getEncodedSize(Mockito.any())).thenReturn(Integer.MAX_VALUE);
        RedisSession rs = session(RedisSessionManager.DO_NOT_CHECK, 1, new MockRedisSessionClient());
        // not checking attr, should be stored
        rs.setAttribute("foo", s);
        Assert.assertTrue("attribute not stored", s.equals(rs.getAttribute("foo")));
        rs.setId("sessionId");
        rs.getManager().save(rs, true);
        verify(c, never()).save(Mockito.anyString(), Mockito.any());
    }

    private RedisSession session(int maxAttrSize, int maxSessionSize, RedisSessionClient client) throws IOException {
        RedisSession rs = new RedisSession();
        RedisSessionManager mgr = spy(RedisSessionManager.class);
        when(mgr.getMaxSessionAttributeSize()).thenReturn(maxAttrSize);
        when(mgr.getMaxSessionSize()).thenReturn(maxSessionSize);
        when(mgr.getContext()).thenReturn(mock(Context.class));
        when(mgr.getClient()).thenReturn(client);
        rs.setManager(mgr);
        rs.setValid(true);
        return rs;
    }

    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
    private static final int BOUNDS = ALPHANUM.length();
    
    protected static String randomString(int size) {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append(ALPHANUM.charAt(r.nextInt(BOUNDS)));
        }
        return sb.toString();
    }
    
}
