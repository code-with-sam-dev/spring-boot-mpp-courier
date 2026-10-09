package com.codewithsam.courier.wallet;

/**
 * A signed transaction that may or may not be on chain yet. The hash is fixed
 * the moment it is signed, so it can be written down before broadcasting.
 */
public record SignedTransfer(String hash, String rawTransaction) {
}
