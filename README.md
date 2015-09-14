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
* Copy `redis-session-manager-with-dependencies-VERSION.jar` to tomcat/lib
* Default configuration: (communicates with redis on localhost:6379)

	<Manager className="com.crimsonhexagon.rsm.redisson.SingleServerSessionManager" />

* Full configuration (showing default values):

	<Manager className="com.crimsonhexagon.rsm.redisson.SingleServerSessionManager"
		endpoint="localhost:6379"
		sessionKeyPrefix="_rsm_"
		saveOnChange="false"
		forceSaveAfterRequest="false"
		dirtyOnMutation="false"
		ignorePattern=".*\\.(ico|png|gif|jpg|jpeg|swf|css|js)$"
		connectionPoolSize="100"
		database="0"
		password="<null>"
		timeout="60000"
		pingTimeout="1000"
		retryAttempts="20"
		retryInterval="1000"
	/>
	
* _endpoint_: hostname:port of the redis server. Must be a primary endpoint (read/write) and not a read replicate (read-only).
* _sessionKeyPrefix_: prefix for redis keys. Useful for situations where 1 redis cluster serves multiple application clusters with potentially conflicting session IDs.
* _saveOnChange_: if _true_, the session will be persisted to redis immediately when any attribute is modified. When _false_, a modified session is persisted to redis when the request is complete.
* _forceSaveAfterRequest_: if _true_, the session will be persisted to redis when the request completes regardless of whether the session has detected a change to its state.
* _ignorePattern_: Java Pattern String to be matched against the request URI (_does not include the query string_). If matched, the request will not be processed by the redis session manager.


AWS ElastiCache usage
-----
Version 2.0.0 added additional support for ElastiCache Replication Groups. Applicable configuration:

	<Manager className="com.crimsonhexagon.rsm.redisson.ElasticacheSessionManager"
		nodes="node1.cache.amazonaws.com:6379 node2.cache.amazonaws.com:6379 ..."
		nodePollInterval="1000"
		sessionKeyPrefix="_rsm_"
		saveOnChange="false"
		forceSaveAfterRequest="false"
		dirtyOnMutation="false"
		ignorePattern=".*\\.(ico|png|gif|jpg|jpeg|swf|css|js)$"
		masterConnectionPoolSize="100"
		slaveConnectionPoolSize="100"
		database="0"
		password="<null>"
		timeout="60000"
		pingTimeout="1000"
		retryAttempts="20"
		retryInterval="1000"
	/>

_nodes_ is a space-separated list of all nodes in the replication group. There is no default value; failure to specify this will result in a failure to start.
_nodePollInterval_ is the interval for polling each node in the group to determine if it is the master or a slave.

	
Notes on object mutation
-----
* TL;DR: avoid mutation of objects pulled from the session. If you must do this, read on.
* Changes made directly to an object in the session without mutating the session will not be persisted to redis. E.g. `session.getAttribute("anObject").setFoo("bar")` will not result in the session being marked dirty. _forceSaveAfterRequest_ can be used as a workaround, but this is inefficient. A dirty workaround would be to mark the session as dirty by `session.removeAttribute("nonExistentKey")` 
* It is possible for an object to be mutated and `session.setAttribute("anObject")` invoked without the session being marked as dirty due to the session object and mutated object being references to the same actual object. _dirtyOnMutation_ will mark the session as dirty whenever `setAttribute()` is invoked. This is generally safe but is disabled by default to avoid unnecessary persists.


