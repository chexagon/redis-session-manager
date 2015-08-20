Redis Session Manager for Tomcat 8
==================================

Tomcat 8 / Java 8 session manager to store sessions in Redis.

Goals
-----
* Ability to use different Java Redis clients (defaults to a Redisson) with client-specific serializers (default to standard java serialization)
* Session save configuration to allow persistence [after a request|when an attribute changes]
* Ignore certain requests (e.g. for static resources)


Usage
-----
* Copy `redis-session-manager-VERSION.jar` to tomcat/lib
* Copy `redisson-X.Y.Z.jar` to tomcat/lib
* Default configuration: (communicates with redis on localhost:6379)

	<Manager className="com.crimsonhexagon.rsm.RedisSessionManager" />

* Full configuration (showing default values):

	<Manager className="com.crimsonhexagon.rsm.RedisSessionManager"
		endpoint="localhost:6379"
		clientClassName="com.crimsonhexagon.rsm.redisson.RedissonSessionClient"
		sessionKeyPrefix="_rsm_"
		saveOnChange="false"
		forceSaveAfterRequest="false"
		ignorePattern=".*\\.(ico|png|gif|jpg|jpeg|swf|css|js)$"
	/>
	
* _endpoint_: hostname:port of the redis server. Must be a primary endpoint (read/write) and not a read replicate (read-only).
* _clientClassName_: fully qualified class name of the RedisSessionClient to use.
* _sessionKeyPrefix_: prefix for redis keys. Useful for situations where 1 redis cluster serves multiple application clusters with potentially conflicting session IDs.
* _saveOnChange_: if _true_, the session will be persisted to redis immediately when any attribute is modified. When _false_, a modified session is persisted to redis when the request is complete.
* _forceSaveAfterRequest_: if _true_, the session will be persisted to redis when the request completes regardless of whether the session has detected a change to its state.
* _ignorePattern_: Java Pattern String to be matched against the request URI (_does not include the query string_). If matched, the request will not be processed by the redis session manager.
	
Notes
-----
* Changes made directly to an object in the session without mutating the session will not be persisted to redis. E.g. `session.getAttribute("anObject").setFoo("bar")` will not result in the session being marked dirty. _forceSaveAfterRequest_ can be used as a workaround, but this is inefficient. A dirty workaround would be to mark the session as dirty by `session.removeAttribute("nonExistentKey")` 


TODO
----
* Full Redisson client configuration

