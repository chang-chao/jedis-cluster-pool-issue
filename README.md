# jedis-cluster-pool-issue
```
wrk -t60 -c60 -d30s http://localhost:8080/foo/
```

# redis cluster
localhost:30001~30006

# command to reproduce
```
wrk -t60 -c60 -d30s http://localhost:8080/foo/
```

# result
we can see that threads are blocked in borrowing pool object. 
## stacktrace
[here](stacktrace.txt)

## jmx
seems sometimes the borrowed Jedis object did not get returned properly.
we can see this from the following image.
<img src="img/pool-jmx.jpg">

