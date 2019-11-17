package me.changchao;

import me.changchao.metrics.CommonsObjectPoolMetrics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JedisClusterIssueApplication {
  public static void main(String[] args) {
    SpringApplication.run(JedisClusterIssueApplication.class, args);
  }

  @Bean
  CommonsObjectPoolMetrics commonsObjectPoolMetrics() {
    return new CommonsObjectPoolMetrics();
  }
}
