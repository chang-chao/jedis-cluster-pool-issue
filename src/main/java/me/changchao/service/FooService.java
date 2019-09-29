/**
 * 
 */
package me.changchao.service;

import me.changchao.model.FooUser;
import me.changchao.repository.FooUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class FooService {
	@Autowired
	FooUserRepository fooUserRepository;

	@Cacheable(value = "users", key = "#uid")
	public String insertAndCache(String uid) {
		FooUser fooUser = new FooUser();
		fooUser.setId(uid);
		fooUserRepository.save(fooUser);
		return uid;
	}
}
