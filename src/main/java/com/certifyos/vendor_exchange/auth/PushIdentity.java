package com.certifyos.vendor_exchange.auth;

/** The verified identity behind a Pub/Sub push: the service account's email. */
public record PushIdentity(String email) {}
