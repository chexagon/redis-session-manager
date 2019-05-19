# Redis Session Manager for Tomcat 8.5
Tomcat 8.5 / Java 8 session manager to store sessions in Redis.

## Goals
* Support for Lettuce and Redisson redis clients
* JDK serialization
* Session save configuration to allow persistence [after a request|when an attribute changes]
* Ignore certain requests (e.g. for static resources)


## Usage
* Decide if you're going to use either Lettuce or Redisson as the redis client.
* After building, copy `rsm-[redisson|lettuce]-with-dependencies-VERSION.jar` to tomcat/lib
* Note `master` pom defines Tomcat 8.5.x; use branch `tomcat8` for Tomcat 8.0.x

## Common Configuration
```
<Manager className="com.crimsonhexagon.rsm.XXX"
	sessionKeyPrefix="_rsm_"
	saveOnChange="false"
	forceSaveAfterRequest="false"
	dirtyOnMutation="false"
	ignorePattern=".*\\.(ico|png|gif|jpg|jpeg|swf|css|js)$"
	maxSessionAttributeSize="-1"
	maxSessionSize="-1"
	allowOversizedSessions="false"
/>
```

* _sessionKeyPrefix_: prefix for redis keys. Useful for situations where 1 redis cluster serves multiple application clusters with potentially conflicting session IDs.
* _saveOnChange_: if _true_, the session will be persisted to redis immediately when any attribute is modified. When _false_, a modified session is persisted to redis when the request is complete.
* _forceSaveAfterRequest_: if _true_, the session will be persisted to redis when the request completes regardless of whether the session has detected a change to its state.
* _dirtyOnMutation_: see "Notes on object mutation" below.
* _ignorePattern_: Java Pattern String to be matched against the request URI (_does not include the query string_). If matched, the request will not be processed by the redis session manager.
* _maxSessionAttributeSize_: if not -1 (RedisSessionManager#DO_NOT_CHECK) specifies a maximum _encoded_ size for a session attribute value. Attributes larger than this size will be logged and will not be stored in the session.
* _maxSessionSize_: if not -1 (RedisSessionManager#DO_NOT_CHECK) specifies a maximum _encoded_ size for the entire session. Sessions larger than this size will be logged and will not be persisted to redis.
* _allowOversizedSessions_: if _true_ will allow sessions exceeding the configurations in _maxSessionAttributeSize_ and _maxSessionSize_ to be saved. An error will still be logged for any sessions exceeding the size. This attribute has no effect if neither _maxSessionAttributeSize_ nor _maxSessionSize_ are specified.

Note: A session can be persisted immediately to redis in the web application by setting the RedisSession#SAVE_IMMEDIATELY_ATTR) attribute to any value.

## Lettuce
* Default configuration: (communicates with redis on localhost:6379)
```
<Manager className="com.crimsonhexagon.rsm.lettuce.LettuceSessionManager" />
```

Multiple nodes can be configured with the `nodes` parameter (this includes AWS Elasticache config):
```
<Manager 
  className="com.crimsonhexagon.rsm.lettuce.LettuceSessionManager"
  nodes="redis://host1:6379 redis://host2:6379" 
  [common config]
/>
```
See https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details for more information on 
URI format and specifying timeouts, etc.

## Redisson
* Default configuration: (communicates with redis on localhost:6379)
```
<Manager className="com.crimsonhexagon.rsm.redisson.SingleServerSessionManager" />
```
* Full configuration (showing default values):
```
<Manager className="com.crimsonhexagon.rsm.redisson.SingleServerSessionManager"
	endpoint="redis://localhost:6379"
	connectionPoolSize="100"
	database="0"
	password="<null>"
	timeout="60000"
	pingTimeout="1000"
	retryAttempts="20"
	retryInterval="1000"
	[common config]
/>
```
* _endpoint_: hostname:port of the redis server. Must be a primary endpoint (read/write) and not a read replicate (read-only).

### Redisson AWS ElastiCache usage
Version 2.0.0 added additional support for ElastiCache Replication Groups. Applicable configuration:
```
<Manager className="com.crimsonhexagon.rsm.redisson.ElasticacheSessionManager"
	nodes="redis://node1.cache.amazonaws.com:6379 redis://node2.cache.amazonaws.com:6379 ..."
	nodePollInterval="1000"
	[other redisson/common config]
/>
```
_nodes_ is a space-separated list of all nodes in the replication group. There is no default value; failure to specify this will result in a failure to start.
_nodePollInterval_ is the interval for polling each node in the group to determine if it is the master or a slave.

	
## Notes on object mutation
* TL;DR: avoid mutation of objects pulled from the session. If you must do this, read on.
* Changes made directly to an object in the session without mutating the session will not be persisted to redis. E.g. `session.getAttribute("anObject").setFoo("bar")` will not result in the session being marked dirty. _forceSaveAfterRequest_ can be used as a workaround, but this is inefficient. A dirty workaround would be to mark the session as dirty by `session.removeAttribute("nonExistentKey")` 
* It is possible for an object to be mutated and `session.setAttribute("anObject")` invoked without the session being marked as dirty due to the session object and mutated object being references to the same actual object. _dirtyOnMutation_ will mark the session as dirty whenever `setAttribute()` is invoked. This is generally safe but is disabled by default to avoid unnecessary persists.
