package com.crimsonhexagon.rsm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.junit.Assert;
import org.junit.Test;

public class RedisSessionTest {

	@Test
	public void testAddAttribute() {
		RedisSession rs = new RedisSession();
		Manager mgr = mock(Manager.class);
		when(mgr.getContext()).thenReturn(mock(Context.class));
		rs.setManager(mgr);
		
		rs.setValid(true);
		rs.setAttribute("foo", "bar");
		Assert.assertTrue(rs.isDirty());
		
		rs.clearDirty();
		rs.setAttribute("foo", "bar");
		Assert.assertFalse(rs.isDirty());
		
		rs.setAttribute("foo", "baz");
		Assert.assertTrue(rs.isDirty());
		
		rs.setAttribute("foobar", "foobar");
		Assert.assertTrue(rs.isDirty());
	}

	@Test
	public void testRemoveAttribute() {
		RedisSession rs = new RedisSession();
		Manager mgr = mock(Manager.class);
		when(mgr.getContext()).thenReturn(mock(Context.class));
		rs.setManager(mgr);
		
		rs.setValid(true);
		rs.removeAttribute("foo");
		Assert.assertTrue(rs.isDirty());
		
		rs.clearDirty();
		rs.removeAttribute("foo");
		Assert.assertTrue(rs.isDirty());
		
		rs.removeAttribute("foo");
		Assert.assertTrue(rs.isDirty());
	}
	
}
