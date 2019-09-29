package me.changchao.repository;

import lombok.SneakyThrows;
import me.changchao.model.FooUser;
import org.springframework.stereotype.Service;

@Service
public class FooUserRepository  {
  @SneakyThrows
  public FooUser save(FooUser fooUser) {
    Thread.sleep(10);
    return fooUser;
  }
}
