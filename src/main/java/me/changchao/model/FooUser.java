package me.changchao.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class FooUser implements Serializable {
  private static final long serialVersionUID = -420854297057894820L;
  private String id;
}
