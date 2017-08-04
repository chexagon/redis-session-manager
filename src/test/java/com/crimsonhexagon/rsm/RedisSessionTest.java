package com.crimsonhexagon.rsm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.authenticator.SavedRequest;
import org.apache.catalina.connector.CoyotePrincipal;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.users.MemoryUser;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class RedisSessionTest {

	private RedisSessionManager mgr = mock(RedisSessionManager.class);

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

	@Test
	public void testSerializedTransientFields() {
		RedisSession rs = session();
		rs.setAttribute("foo", "bar");
		Principal principal = new CoyotePrincipal("user");
		rs.setPrincipal(principal);
		byte[] serializedState = serialize(rs);
		RedisSession result = deserialize(serializedState);
		Assert.assertNotNull(result.getPrincipal());
		Assert.assertEquals(principal.getName(), result.getPrincipal().getName());
		Assert.assertNotNull(getFieldValue(result,"support"));
		Assert.assertNotNull(getFieldValue(result,"listeners"));
		Assert.assertNotNull(getFieldValue(result,"notes"));
	}

	@Test
	public void testFormRequestNoteSerialization() {
		RedisSession rs = session();
		SavedRequest savedRequest = new SavedRequest();
		savedRequest.setRequestURI("http://someuri");
		rs.setNote(Constants.FORM_REQUEST_NOTE, savedRequest);
		rs = deserialize(serialize(rs));
		SavedRequest result = (SavedRequest) rs.getNote(Constants.FORM_REQUEST_NOTE);
		Assert.assertNotNull(result);
		Assert.assertEquals(savedRequest.getRequestURI(), result.getRequestURI());
	}

	@Test
	public void testFormPrincipalNoteSerialization() {
		RedisSession rs = session();
		Principal principal = new CoyotePrincipal("user");
		rs.setNote(Constants.FORM_PRINCIPAL_NOTE, principal);
		rs = deserialize(serialize(rs));
		Principal result = (Principal) rs.getNote(Constants.FORM_PRINCIPAL_NOTE);
		Assert.assertNotNull(result);
		Assert.assertEquals(principal.getName(), result.getName());
	}

	@Test
	public void testUnknownNoteSerialization() {
		RedisSession rs = session();
		rs.setNote("unknown", "someValue");
		rs = deserialize(serialize(rs));
		Assert.assertNull(rs.getNote("unknown"));
	}

	private Object getFieldValue(Object object, String name) {
		Field field = null;
		try {
			field = StandardSession.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(object);
		} catch (NoSuchFieldException|IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private byte[] serialize(RedisSession session) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try(ObjectOutputStream stream = new ObjectOutputStream(baos)) {
			stream.writeObject(session);
			stream.flush();
			return baos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private RedisSession deserialize(byte[] state) {
		ByteArrayInputStream baos = new ByteArrayInputStream(state);
		try (ObjectInputStream stream = new ObjectInputStream(baos)){
			RedisSession redisSession = (RedisSession) stream.readObject();
			redisSession.postDeserialization(mgr);
			return redisSession;
		} catch (IOException|ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private RedisSession session() {
		RedisSession rs = new RedisSession();
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
