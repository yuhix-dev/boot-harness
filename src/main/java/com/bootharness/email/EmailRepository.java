package com.bootharness.email;

public interface EmailRepository {
  void send(String to, String subject, String htmlBody);
}
