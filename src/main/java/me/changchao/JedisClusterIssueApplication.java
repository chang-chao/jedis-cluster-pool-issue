package me.changchao;

import me.changchao.metrics.CommonsObjectPool2Metrics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JedisClusterIssueApplication {
  public static void main(String[] args) {
    SpringApplication.run(JedisClusterIssueApplication.class, args);
  }

  @Bean
  CommonsObjectPool2Metrics commonsObjectPoolMetrics() {
    return new CommonsObjectPool2Metrics();
  }
}
