package com.crimsonhexagon.rsm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Context;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class RedisSessionTest {

	@Test
	public void testNewAttribute() {
		RedisSession rs = session();
		rs.setAttribute("foo", "bar");
		Assert.assertTrue(rs.isDirty());
	}
	
	@Test
	public void testOverwriteAttribute() {
		RedisSession rs = session();
		rs.setAttribute("foo", "bar");
		Assert.assertTrue(rs.isDirty());
		
		rs.clearDirty();
		rs.setAttribute("foo", "bar");
		Assert.assertFalse(rs.isDirty());
	}
	
	@Test
	public void testMutatedAttribute() {
		// mutated list means old.equals(new)
		{
			RedisSession rs = session();
			RedisSessionManager.class.cast(rs.getManager()).setDirtyOnMutation(false);
			List<String> l = new ArrayList<>();
			l.add("foo");
			rs.setAttribute("strList", l);
			Assert.assertTrue(rs.isDirty());
			
			rs.clearDirty();
			l.add("bar");
			rs.setAttribute("strList", l);
			Assert.assertFalse(rs.isDirty());
		}
		
		// now set dirty on mutation to make sure setting the attribute after mutation marks it as dirty
		{
			RedisSession rs = session();
			RedisSessionManager.class.cast(rs.getManager()).setDirtyOnMutation(true);
			List<String> l = new ArrayList<>();
			l.add("foo");
			rs.setAttribute("strList", l);
			Assert.assertTrue(rs.isDirty());
			
			rs.clearDirty();
			l.add("bar");
			rs.setAttribute("strList", l);
			Assert.assertTrue(rs.isDirty());			
		}
		
	}
	
	@Test
	public void testRemoveAttribute() {
		RedisSession rs = session();
		
		rs.removeAttribute("foo");
		Assert.assertTrue(rs.isDirty());
		
		rs.clearDirty();
		rs.removeAttribute("foo");
		Assert.assertTrue(rs.isDirty());
		
		rs.removeAttribute("foo");
		Assert.assertTrue(rs.isDirty());
	}
	
	private RedisSession session() {
		RedisSession rs = new RedisSession();
		RedisSessionManager mgr = mock(RedisSessionManager.class);
		when(mgr.isDirtyOnMutation()).thenCallRealMethod();
		Mockito.doCallRealMethod().when(mgr).setDirtyOnMutation(Mockito.anyBoolean());
		when(mgr.isForceSaveAfterRequest()).thenCallRealMethod();
		Mockito.doCallRealMethod().when(mgr).setForceSaveAfterRequest(Mockito.anyBoolean());
		when(mgr.isSaveOnChange()).thenCallRealMethod();
		Mockito.doCallRealMethod().when(mgr).setSaveOnChange(Mockito.anyBoolean());
		when(mgr.getContext()).thenReturn(mock(Context.class));
		rs.setManager(mgr);
		rs.setValid(true);
		return rs;
	}
	
}
