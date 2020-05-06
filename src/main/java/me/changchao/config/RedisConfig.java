package me.changchao.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@EnableRedisRepositories
@Slf4j
public class RedisConfig extends CachingConfigurerSupport {

  @Autowired RedisProperties redisProperties;

  @Bean
  public RedisConnectionFactory connectionFactory() {
    JedisClientConfiguration clientConfiguration =
        JedisClientConfiguration.builder()
            .connectTimeout(redisProperties.getTimeout())
            .readTimeout(redisProperties.getTimeout())
            .build();
    return new JedisConnectionFactory(
        new RedisClusterConfiguration(redisProperties.getCluster().getNodes()),
        clientConfiguration);
  }

  @Bean
  public RedisTemplate<?, ?> redisTemplate() {
    RedisTemplate<byte[], byte[]> redisTemplate = new RedisTemplate<>();
    redisTemplate.setConnectionFactory(connectionFactory());
    return redisTemplate;
  }

  @Bean
  public RedisCacheWriter redisCacheWriter() {
    return RedisCacheWriter.lockingRedisCacheWriter(connectionFactory());
  }

  @Bean
  public RedisCacheConfiguration defaultRedisCacheConfiguration() {
    return RedisCacheConfiguration.defaultCacheConfig()
        .serializeValuesWith(
            SerializationPair.fromSerializer(
                RedisSerializer.java(this.getClass().getClassLoader())))
        .disableCachingNullValues();
  }

  @Bean
  public CacheManager cacheManager() {
    Map<String, RedisCacheConfiguration> cacheNamesConfigurationMap = new HashMap<>();
    RedisCacheManager cacheManager =
        new RedisCacheManager(
            redisCacheWriter(),
            defaultRedisCacheConfiguration().entryTtl(Duration.ofSeconds(900)),
            cacheNamesConfigurationMap);
    cacheManager.setTransactionAware(
        true); // To make ensure redis cache is only stored when the database operation,
    return cacheManager;
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return new SimpleCacheErrorHandler() {
      @Override
      public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        // in case of any error in getting from cache, we ignore the error
        // so that data can be fetched from DB
        log.warn(
            "error in getting from cache, cache={},key={},will fallback to fetch from db",
            cache,
            key,
            exception);
      }

      @Override
      public void handleCachePutError(
          RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("error in putting to cache, cache={},key={}", cache, key, exception);
      }

      @Override
      public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        super.handleCacheEvictError(exception, cache, key);
      }

      @Override
      public void handleCacheClearError(RuntimeException exception, Cache cache) {
        super.handleCacheClearError(exception, cache);
      }
    };
  }
}
