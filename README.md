Redis Session Manager for Tomcat 8
==================================

Tomcat 8 / Java 8 session manager to store sessions in Redis.

Goals
-----
* Ability to use different Java Redis clients (defaults to a Redisson) with client-specific serializers (default to standard java serialization)
* Session save configuration to allow persistence [after a request|when an attribute changes]
* Ignore certain requests (e.g. for static resources)


Completed
---------
* Rough framework - manager, valve, etc 
* Redisson client to talk to localhost


TODO
----
* Unit tests
* Full Redisson client configuration
* Elasticache integration: use the Elasticache API to get redis cluster info given an Elasticache cluster name
* Documentation

Usage
-----
* Run a redis server locally on port 6379.
* Add `<Manager className="com.crimsonhexagon.rsm.RedisSessionManager" />` to tomcat's context.xml
* Copy `redis-session-manager-with-dependencies-VERSION.jar` to tomcat/lib
