package com.crimsonhexagon.rsm;

import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.loader.WebappLoader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class RedisSessionRequestValveTest {

	private RedisSessionManager manager;
    private Valve _nextValve;
    private Request request;
    private Response response;
    private Host hostContainer;

    @Before
    public void setUp() throws Exception {
        this.manager = mock( RedisSessionManager.class );
        this.request = mock( Request.class, withSettings().useConstructor() ); // useConstructor to instantiate fields
        this.response = mock( Response.class );

        final Context contextContainer = mock(Context.class);
        this.hostContainer = mock(Host.class);

        when(contextContainer.getParent()).thenReturn(hostContainer);
        when(contextContainer.getPath()).thenReturn("/");

        when(request.getRequestURI()).thenReturn( "/requestURI"); // override for tests
        when(request.getMethod()).thenReturn("GET");
        when(request.getQueryString()).thenReturn(null);
        when(request.getContext()).thenReturn(contextContainer);

        doCallRealMethod().when(request).setNote(Mockito.anyString(), Mockito.anyObject());
        doCallRealMethod().when(request).removeNote(Mockito.anyString());
        when(request.getNote(Mockito.anyString())).thenCallRealMethod();
        
        when(contextContainer.getLoader()).thenReturn(new WebappLoader(Thread.currentThread().getContextClassLoader()));
    }
	
    protected RedisSessionRequestValve createValve(String ignorePattern) {
        RedisSessionRequestValve requestValve = new RedisSessionRequestValve(manager, ignorePattern);
        _nextValve = mock( Valve.class );
        requestValve.setNext( _nextValve );
        requestValve.setContainer(hostContainer);
        return requestValve;
    }
    
    @Test
    public void testDefaultIgnore() throws Exception {
    	// ico|png|gif|jpg|jpeg|swf|css|js
    	RedisSessionRequestValve requestValve = createValve(RedisSessionRequestValve.DEFAULT_IGNORE_PATTERN);
    	when(request.getRequestURI()).thenReturn( "/ignored.ico");
    	requestValve.invoke(request, response);
    	Assert.assertNull(request.getNote(RedisSessionRequestValve.REQUEST_PROCESSED));

    	when(request.getRequestURI()).thenReturn( "/Notignored.valid");
    	requestValve.invoke(request, response);
    	Assert.assertTrue((Boolean)request.getNote(RedisSessionRequestValve.REQUEST_PROCESSED));
    	
    	when(request.getRequestURI()).thenReturn( "/ignored.PNG");
    	requestValve.invoke(request, response);
    	Assert.assertNull(request.getNote(RedisSessionRequestValve.REQUEST_PROCESSED));
    	
    	when(request.getRequestURI()).thenReturn( "/ignored.Gif");
    	requestValve.invoke(request, response);
    	Assert.assertNull(request.getNote(RedisSessionRequestValve.REQUEST_PROCESSED));
    	
    	when(request.getRequestURI()).thenReturn( "/ignored.jpg");
    	requestValve.invoke(request, response);
    	Assert.assertNull(request.getNote(RedisSessionRequestValve.REQUEST_PROCESSED));
    	
    	when(request.getRequestURI()).thenReturn( "/ignored.JPEG");
    	requestValve.invoke(request, response);
    	Assert.assertNull(request.getNote(RedisSessionRequestValve.REQUEST_PROCESSED));
    	
    	when(request.getRequestURI()).thenReturn( "/ignored.swf");
    	requestValve.invoke(request, response);
    	Assert.assertNull(request.getNote(RedisSessionRequestValve.REQUEST_PROCESSED));
    	
    	when(request.getRequestURI()).thenReturn( "/ignored.css");
    	requestValve.invoke(request, response);
    	Assert.assertNull(request.getNote(RedisSessionRequestValve.REQUEST_PROCESSED));
    	
    	when(request.getRequestURI()).thenReturn( "/ignored.js");
    	requestValve.invoke(request, response);
    	Assert.assertNull(request.getNote(RedisSessionRequestValve.REQUEST_PROCESSED));

    	when(request.getRequestURI()).thenReturn( "/Notignored.validjs");
    	requestValve.invoke(request, response);
    	Assert.assertTrue((Boolean)request.getNote(RedisSessionRequestValve.REQUEST_PROCESSED));
    	
    }
    
    
}
