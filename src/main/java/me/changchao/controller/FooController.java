/** */
package me.changchao.controller;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.changchao.service.FooService;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/foo")
@Slf4j
public class FooController {
  @Autowired RedisTemplate redisTemplate;
  @Autowired FooService fooService;

  @RequestMapping("/")
  @SneakyThrows
  public ResponseEntity<String> foo() {
    log.info("foo");
    String user = "u" + RandomUtils.nextInt(0, 2000);
    if (!redisTemplate.hasKey("users::".concat(user))) {
      user = fooService.insertAndCache(user);
    }

    Thread.sleep(100);
    return new ResponseEntity<>(user, HttpStatus.OK);
  }
}
