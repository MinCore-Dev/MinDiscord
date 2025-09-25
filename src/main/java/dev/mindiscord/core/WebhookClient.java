package dev.mindiscord.core;

interface WebhookClient {
  WebhookTransport.TransportResponse postJson(String url, String json);
}
