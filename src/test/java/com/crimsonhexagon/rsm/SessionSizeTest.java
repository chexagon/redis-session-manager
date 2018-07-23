package com.crimsonhexagon.rsm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.bytebuddy.utility.RandomString;
import org.apache.catalina.Context;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

public class SessionSizeTest {
    
    @Test
    public void testValidAttrSize() throws Exception {
        RedisSession rs = session(RedisSessionManager.DO_NOT_CHECK, RedisSessionManager.DO_NOT_CHECK, new MockRedisSessionClient());
        String s = RandomString.make(128);
        rs.setAttribute("foo", s);
        Assert.assertTrue("attribute not stored", s.equals(rs.getAttribute("foo")));
    }

    @Test
    public void testLargeAttrSize() throws Exception {
        String s = RandomString.make(128);
        final int length = new MockRedisSessionClient().getEncodedSize(s);
        RedisSession rs = session(length, RedisSessionManager.DO_NOT_CHECK, new MockRedisSessionClient());
        // == to max, should be stored
        rs.setAttribute("foo", s);
        Assert.assertTrue("attribute not stored", s.equals(rs.getAttribute("foo")));
        
        s = RandomString.make(length + 1);
        rs.setAttribute("bar", s);
        Assert.assertNull("attribute > max length stored", rs.getAttribute("bar"));
    }
    
    @Test
    public void testLargeSessionSize() throws Exception {
        String s = RandomString.make(128);
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
    
}
