package com.codewithsam.courier.wallet;

/** Where the chain lives and where a person can look a transaction up. */
public record TempoNetwork(String rpcUrl, long chainId, String explorerUrl) {

    public static final TempoNetwork MODERATO = new TempoNetwork(
            "https://rpc.moderato.tempo.xyz", 42431,
            "https://explore.testnet.tempo.xyz");

    public String transactionUrl(String txHash) {
        return explorerUrl + "/tx/" + txHash;
    }
}
