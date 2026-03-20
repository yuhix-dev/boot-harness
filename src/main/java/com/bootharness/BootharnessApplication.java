package com.bootharness;

import com.bootharness.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class BootharnessApplication {

  public static void main(String[] args) {
    SpringApplication.run(BootharnessApplication.class, args);
  }
}
