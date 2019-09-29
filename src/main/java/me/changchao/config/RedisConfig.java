package me.changchao.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
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
public class RedisConfig {

  @Autowired
  RedisProperties redisProperties;

  @Bean
  public RedisConnectionFactory connectionFactory() {
    JedisClientConfiguration clientConfiguration = JedisClientConfiguration.builder()
            .connectTimeout(redisProperties.getTimeout())
            .readTimeout(redisProperties.getTimeout())
            .build();
    return new JedisConnectionFactory(
            new RedisClusterConfiguration(redisProperties.getCluster().getNodes()),clientConfiguration);
  }

  @Bean
  public RedisTemplate<?,?> redisTemplate() {
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
                    SerializationPair.fromSerializer(RedisSerializer.java(this.getClass().getClassLoader())))
            .disableCachingNullValues();
  }

  @Bean
  public CacheManager cacheManager() {
    Map<String, RedisCacheConfiguration> cacheNamesConfigurationMap = new HashMap<>();
    RedisCacheManager cacheManager = new RedisCacheManager(redisCacheWriter(),
            defaultRedisCacheConfiguration().entryTtl(Duration.ofSeconds(900)), cacheNamesConfigurationMap);
    cacheManager.setTransactionAware(true); // To make ensure redis cache is only stored when the database operation,
    return cacheManager;
  }

}