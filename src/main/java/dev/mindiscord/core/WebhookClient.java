package dev.mindiscord.core;

interface WebhookClient {
  WebhookTransport.TransportResponse postJson(String url, String json);

  default void configure(Config.Transport transport) {
    // no-op by default
  }
}
