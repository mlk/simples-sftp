package com.github.mlk.simples.sftp;

import java.io.IOException;

@FunctionalInterface
public interface ThrowingConsumer<T>  {

  void accept(T elem) throws IOException;

}
